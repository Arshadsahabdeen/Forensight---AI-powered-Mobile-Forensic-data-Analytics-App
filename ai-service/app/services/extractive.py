"""Deterministic, local engine: keyword retrieval (BM25), aggregation and pattern-based entity extraction.

Every statement it produces restates or counts supplied records and cites them. It never interprets
meaning, so it is safe to run without any external model.
"""

import math
import re
from collections import Counter, defaultdict

from app.schemas.assist import AssistRequest, AssistResponse, Citation, EntityMention, EvidenceRecord, Statement
from app.services.text import describe, phone_key, record_text, tokens

MAX_CITED_PER_STATEMENT = 50
TOP_RESULTS = 15

_EMAIL = re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+")
_URL = re.compile(r"https?://[^\s\"'<>]+")
_PHONE_IN_TEXT = re.compile(r"\+?\d[\d\s\-().]{6,18}\d")


def bm25_rank(records: list[EvidenceRecord], query_terms: list[str]) -> list[tuple[EvidenceRecord, float, list[str]]]:
    """BM25 over record text; returns (record, score, matched terms) for records with score > 0."""
    if not query_terms or not records:
        return []
    docs = [tokens(record_text(r)) for r in records]
    avg_len = sum(len(d) for d in docs) / len(docs) or 1.0
    doc_freq = Counter(term for d in docs for term in set(d))
    n = len(docs)
    k1, b = 1.5, 0.75
    query_phones = {k for k in (phone_key(t) for t in query_terms) if k}

    ranked = []
    for record, doc in zip(records, docs):
        counts = Counter(doc)
        score = 0.0
        matched = []
        for term in dict.fromkeys(query_terms):
            tf = counts.get(term, 0)
            if not tf:
                continue
            idf = math.log(1 + (n - doc_freq[term] + 0.5) / (doc_freq[term] + 0.5))
            score += idf * tf * (k1 + 1) / (tf + k1 * (1 - b + b * len(doc) / avg_len))
            matched.append(term)
        for field in (record.source, record.target, record.phoneNumber):
            key = phone_key(field)
            if key and key in query_phones:
                score += 3.0
                matched.append(field)
        if score > 0:
            ranked.append((record, score, matched))
    ranked.sort(key=lambda item: (-item[1], item[0].id))
    return ranked


def extract_entities(records: list[EvidenceRecord]) -> list[EntityMention]:
    """Identifiers found in record fields and text, grouped (phones on their last 10 digits) with citations."""
    groups: dict[tuple[str, str], dict] = {}

    def add(kind: str, key: str, value: str, record_id: int) -> None:
        entry = groups.setdefault((kind, key), {"value": value, "ids": []})
        if record_id not in entry["ids"]:
            entry["ids"].append(record_id)

    for r in records:
        for field in (r.source, r.target, r.phoneNumber):
            if not field:
                continue
            key = phone_key(field)
            if key:
                add("PHONE", key, field.strip(), r.id)
            elif "@" in field:
                add("EMAIL", field.strip().lower(), field.strip(), r.id)
            else:
                add("ACCOUNT", field.strip().lower(), field.strip(), r.id)
        if r.contactName:
            add("PERSON", r.contactName.strip().lower(), r.contactName.strip(), r.id)
        if r.location:
            add("LOCATION", r.location.strip().lower(), r.location.strip(), r.id)
        text = " ".join(p for p in (r.title, r.content) if p)
        for email in _EMAIL.findall(text):
            add("EMAIL", email.lower(), email, r.id)
        for url in _URL.findall(text):
            add("URL", url.rstrip(".,);"), url.rstrip(".,);"), r.id)
        for candidate in _PHONE_IN_TEXT.findall(text):
            key = phone_key(candidate)
            if key:
                add("PHONE", key, candidate.strip(), r.id)

    entities = [EntityMention(type=kind, value=data["value"], evidenceIds=data["ids"][:MAX_CITED_PER_STATEMENT])
                for (kind, _), data in groups.items()]
    entities.sort(key=lambda e: (-len(e.evidenceIds), e.type, e.value))
    return entities


def summarize(records: list[EvidenceRecord]) -> list[Statement]:
    statements: list[Statement] = []
    by_type: dict[str, list[int]] = defaultdict(list)
    for r in records:
        by_type[(r.type or "UNKNOWN").upper()].append(r.id)
    for kind, ids in sorted(by_type.items(), key=lambda item: (-len(item[1]), item[0])):
        statements.append(Statement(text=f"{len(ids)} {kind} record(s) are in scope.", kind="AGGREGATE",
                                    evidenceIds=ids[:MAX_CITED_PER_STATEMENT]))

    dated = sorted((r for r in records if r.time), key=lambda r: r.time)
    if dated:
        statements.append(Statement(
            text=f"Dated records span {dated[0].time} to {dated[-1].time} (UTC).", kind="AGGREGATE",
            evidenceIds=sorted({dated[0].id, dated[-1].id})))
    undated = [r.id for r in records if not r.time]
    if undated:
        statements.append(Statement(text=f"{len(undated)} record(s) have no usable timestamp.", kind="AGGREGATE",
                                    evidenceIds=undated[:MAX_CITED_PER_STATEMENT]))
    assumed = [r.id for r in records if r.timestampStatus in ("ASSUMED_UTC", "DATE_ONLY")]
    if assumed:
        statements.append(Statement(
            text=f"{len(assumed)} record(s) have timestamps without a timezone or time of day; their UTC placement is assumed.",
            kind="AGGREGATE", evidenceIds=assumed[:MAX_CITED_PER_STATEMENT]))

    for entity in extract_entities(records)[:5]:
        if entity.type in ("PHONE", "ACCOUNT", "EMAIL", "PERSON") and len(entity.evidenceIds) > 1:
            statements.append(Statement(
                text=f"{entity.type.title()} '{entity.value}' appears in {len(entity.evidenceIds)} record(s).",
                kind="AGGREGATE", evidenceIds=entity.evidenceIds))
    return statements


def run(request: AssistRequest, note: str | None = None) -> AssistResponse:
    records = request.records
    terms = tokens(request.question)
    limitations = ["Extractive engine: results come from keyword matching, counting and pattern extraction over the "
                   "supplied records. No generative model interpreted the evidence."]
    if note:
        limitations.insert(0, note)

    if request.task in ("search", "ask"):
        ranked = bm25_rank(records, terms)[:TOP_RESULTS]
        if not ranked:
            return AssistResponse(engine="extractive", task=request.task, answer="No supplied records match the query terms.",
                                  limitations=limitations)
        prefix = ("No generative model is enabled, so the question is not answered directly. "
                  if request.task == "ask" else "")
        answer = (f"{prefix}{len(ranked)} record(s) match the query terms "
                  f"({', '.join(dict.fromkeys(terms))}); the most relevant are cited below for review.")
        return AssistResponse(
            engine="extractive", task=request.task, answer=answer,
            statements=[Statement(text=describe(r), kind="RECORD_FACT", evidenceIds=[r.id]) for r, _, _ in ranked],
            citations=[Citation(evidenceId=r.id, reason="Matches: " + ", ".join(dict.fromkeys(m))) for r, _, m in ranked],
            limitations=limitations)

    if request.task == "entities":
        entities = extract_entities(records)
        return AssistResponse(engine="extractive", task="entities",
                              answer=f"{len(entities)} distinct identifier(s), name(s), location(s) or link(s) were found "
                                     f"in {len(records)} record(s).",
                              entities=entities, limitations=limitations)

    statements = summarize(records)
    return AssistResponse(
        engine="extractive", task="summarize",
        answer=f"Summary of {len(records)} record(s): counts, time span, data-quality notes and the most frequent identifiers.",
        statements=statements, entities=extract_entities(records)[:20], limitations=limitations)

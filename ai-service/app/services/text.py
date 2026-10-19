"""Shared text helpers: tokenization, record rendering, phone normalization."""

import re

from app.schemas.assist import EvidenceRecord

STOPWORDS = {
    "a", "an", "the", "and", "or", "of", "to", "in", "on", "at", "for", "with", "by", "from", "is", "are", "was",
    "were", "be", "been", "it", "this", "that", "these", "those", "what", "which", "who", "whom", "when", "where",
    "why", "how", "did", "do", "does", "any", "all", "show", "find", "list", "me", "about", "there", "their",
    "they", "them", "has", "have", "had", "can", "could", "should", "would", "between", "messages", "records",
    "evidence", "please",
}

_TOKEN = re.compile(r"[\w@.+-]+", re.UNICODE)
_PHONE_CHARS = re.compile(r"^[+0-9()\-.\s]+$")


def tokens(text: str | None) -> list[str]:
    if not text:
        return []
    out = []
    for raw in _TOKEN.findall(text.lower()):
        token = raw.strip(".+-_")
        if len(token) >= 2 and token not in STOPWORDS:
            out.append(token)
    return out


def phone_key(value: str | None) -> str | None:
    """Last 10 digits of a phone-like value (matches the backend's normalization), else None."""
    if not value or not _PHONE_CHARS.match(value.strip()):
        return None
    digits = re.sub(r"\D", "", value)
    if not 7 <= len(digits) <= 15:
        return None
    return digits[-10:] if len(digits) > 10 else digits


def record_text(record: EvidenceRecord) -> str:
    parts = [record.type, record.title, record.content, record.source, record.target, record.phoneNumber,
             record.contactName, record.location]
    return " ".join(p for p in parts if p)


def snippet(record: EvidenceRecord, limit: int = 160) -> str:
    body = record.content or record.title or ""
    body = " ".join(body.split())
    return body if len(body) <= limit else body[: limit - 1] + "…"


def describe(record: EvidenceRecord) -> str:
    text = (record.type or "RECORD") + (f" at {record.time}" if record.time else " (no timestamp)")
    if record.source or record.target:
        text += f" from {record.source or '?'} to {record.target or '?'}"
    elif record.phoneNumber:
        text += f" involving {record.phoneNumber}"
    if record.contactName:
        text += f" (contact '{record.contactName}')"
    if record.location:
        text += f" at location '{record.location}'"
    body = snippet(record)
    if body:
        text += f': "{body}"'
    return text

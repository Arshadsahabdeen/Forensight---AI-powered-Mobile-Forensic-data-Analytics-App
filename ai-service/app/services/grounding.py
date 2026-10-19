"""Removes anything in a response that is not backed by the supplied records.

The backend re-validates independently; this keeps the AI service itself honest too.
"""

import re

from app.schemas.assist import AssistResponse, EvidenceRecord
from app.services.text import phone_key, record_text

_BRACKET_REF = re.compile(r"\[(\d{1,12})\]")


def _appears_in(value: str, record: EvidenceRecord) -> bool:
    haystack = record_text(record).lower()
    if value.lower() in haystack:
        return True
    key = phone_key(value)
    if key:
        fields = (record.source, record.target, record.phoneNumber)
        return any(phone_key(f) == key for f in fields) or key in re.sub(r"\D", "", haystack)
    return False


def ground(response: AssistResponse, records: list[EvidenceRecord]) -> AssistResponse:
    allowed = {r.id: r for r in records}
    dropped = 0

    statements = []
    for statement in response.statements:
        ids = [i for i in dict.fromkeys(statement.evidenceIds) if i in allowed]
        if not ids or not statement.text.strip():
            dropped += 1
            continue
        statements.append(statement.model_copy(update={"evidenceIds": ids}))

    citations = []
    seen = set()
    for citation in response.citations:
        if citation.evidenceId not in allowed or citation.evidenceId in seen:
            dropped += citation.evidenceId not in allowed
            continue
        seen.add(citation.evidenceId)
        citations.append(citation)

    entities = []
    for entity in response.entities:
        ids = [i for i in dict.fromkeys(entity.evidenceIds) if i in allowed and _appears_in(entity.value, allowed[i])]
        if not ids or not entity.value.strip():
            dropped += 1
            continue
        entities.append(entity.model_copy(update={"evidenceIds": ids}))

    limitations = list(response.limitations)
    unknown = sorted({int(m) for m in _BRACKET_REF.findall(response.answer)} - allowed.keys())
    if unknown:
        limitations.append(f"The answer referenced record IDs that were not supplied {unknown}; treat those parts as unsupported.")
    if dropped:
        limitations.append(f"{dropped} item(s) were removed because they were not supported by the supplied records.")

    return response.model_copy(update={"statements": statements, "citations": citations, "entities": entities,
                                       "limitations": limitations})

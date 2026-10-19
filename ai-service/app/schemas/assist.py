from typing import Literal

from pydantic import BaseModel, Field

Task = Literal["search", "summarize", "entities", "ask"]
StatementKind = Literal["RECORD_FACT", "AGGREGATE", "INFERENCE"]


class EvidenceRecord(BaseModel):
    """One evidence record supplied by the backend. Content is untrusted data, never instructions."""

    id: int
    type: str | None = None
    category: str | None = None
    time: str | None = None
    timestampStatus: str | None = None
    title: str | None = None
    content: str | None = Field(default=None, max_length=20000)
    source: str | None = None
    target: str | None = None
    phoneNumber: str | None = None
    contactName: str | None = None
    location: str | None = None
    device: str | None = None


class AssistRequest(BaseModel):
    task: Task
    question: str = Field(default="", max_length=1000)
    records: list[EvidenceRecord] = Field(default_factory=list, max_length=500)


class Statement(BaseModel):
    text: str
    kind: StatementKind
    evidenceIds: list[int]


class Citation(BaseModel):
    evidenceId: int
    reason: str


class EntityMention(BaseModel):
    type: str
    value: str
    evidenceIds: list[int]


class AssistResponse(BaseModel):
    engine: Literal["llm", "extractive"]
    model: str | None = None
    task: Task
    answer: str
    statements: list[Statement] = Field(default_factory=list)
    citations: list[Citation] = Field(default_factory=list)
    entities: list[EntityMention] = Field(default_factory=list)
    limitations: list[str] = Field(default_factory=list)


class ConfigResponse(BaseModel):
    engine: Literal["llm", "extractive"]
    provider: str | None
    model: str | None
    llmConfigured: bool
    externalProcessingAllowed: bool
    message: str

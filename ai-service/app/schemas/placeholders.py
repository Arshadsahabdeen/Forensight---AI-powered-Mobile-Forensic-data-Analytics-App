from pydantic import BaseModel, Field


class AnalyzeRequest(BaseModel):
    case_id: str | None = Field(default=None)
    artifact_type: str | None = Field(default=None)
    content_ref: str | None = Field(default=None)


class PlaceholderResponse(BaseModel):
    status: str
    message: str
    module: str
    sample: dict

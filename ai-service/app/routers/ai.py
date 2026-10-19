from fastapi import APIRouter

from app.config.settings import settings
from app.schemas.assist import AssistRequest, AssistResponse, ConfigResponse
from app.services.ai_placeholder_service import assist, describe_config

router = APIRouter(prefix="/v1", tags=["Assistant"])


@router.get("/config", response_model=ConfigResponse)
def config() -> ConfigResponse:
    """Engine status. Never returns secrets."""
    return describe_config(settings)


@router.post("/assist", response_model=AssistResponse)
def assist_endpoint(request: AssistRequest) -> AssistResponse:
    return assist(request, settings)

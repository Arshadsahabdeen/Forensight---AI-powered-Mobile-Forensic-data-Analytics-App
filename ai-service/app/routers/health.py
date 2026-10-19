from fastapi import APIRouter

from app.config.settings import settings

router = APIRouter()


@router.get("/health")
def health() -> dict:
    return {"status": "UP", "service": "forensic-ai-service", "version": settings.version}

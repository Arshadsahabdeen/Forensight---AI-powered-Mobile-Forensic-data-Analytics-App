from fastapi import APIRouter
from app.schemas.placeholders import AnalyzeRequest, PlaceholderResponse
from app.services.ai_placeholder_service import placeholder

router = APIRouter(prefix="/ai", tags=["AI Placeholders"])


@router.post("/analyze", response_model=PlaceholderResponse)
def analyze(_: AnalyzeRequest) -> PlaceholderResponse:
    return placeholder("analyze")


@router.post("/summary", response_model=PlaceholderResponse)
def summary(_: AnalyzeRequest) -> PlaceholderResponse:
    return placeholder("summary")


@router.post("/entities", response_model=PlaceholderResponse)
def entities(_: AnalyzeRequest) -> PlaceholderResponse:
    return placeholder("entities")


@router.post("/ocr", response_model=PlaceholderResponse)
def ocr(_: AnalyzeRequest) -> PlaceholderResponse:
    return placeholder("ocr")

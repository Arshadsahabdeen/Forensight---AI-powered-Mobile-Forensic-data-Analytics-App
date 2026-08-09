from app.schemas.placeholders import PlaceholderResponse


def placeholder(module: str) -> PlaceholderResponse:
    return PlaceholderResponse(
        status="UNDER_DEVELOPMENT",
        message="AI functionality will be implemented in later milestones.",
        module=module,
        sample={"confidence": "Coming Soon", "riskScore": "Coming Soon", "entities": []},
    )

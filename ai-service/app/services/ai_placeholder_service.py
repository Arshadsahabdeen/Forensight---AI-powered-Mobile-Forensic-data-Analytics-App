"""Engine selection for the assistant (formerly a placeholder)."""

from app.config.settings import Settings
from app.schemas.assist import AssistRequest, AssistResponse, ConfigResponse
from app.services import extractive, llm
from app.services.grounding import ground


def describe_config(settings: Settings) -> ConfigResponse:
    if settings.llm_enabled:
        return ConfigResponse(engine="llm", provider="anthropic", model=settings.ai_model, llmConfigured=True,
                              externalProcessingAllowed=True,
                              message="Generative analysis is enabled. Selected evidence records are sent to the configured model provider.")
    if settings.llm_configured:
        message = ("An API key is configured but AI_ALLOW_EXTERNAL_PROCESSING is false, so evidence stays local and the "
                   "extractive engine is used.")
    else:
        message = ("No generative model is configured. The local extractive engine (keyword retrieval, counting and "
                   "pattern extraction) is used. Set ANTHROPIC_API_KEY and AI_ALLOW_EXTERNAL_PROCESSING=true to enable it.")
    return ConfigResponse(engine="extractive", provider=None, model=None, llmConfigured=settings.llm_configured,
                          externalProcessingAllowed=settings.ai_allow_external_processing, message=message)


def assist(request: AssistRequest, settings: Settings, client=None) -> AssistResponse:
    if not request.records:
        return AssistResponse(engine="extractive", task=request.task, answer="No records were supplied.",
                              limitations=["The assistant only works from supplied evidence records."])
    if settings.llm_enabled:
        try:
            return ground(llm.run(request, settings, client), request.records)
        except llm.LlmUnavailable as exc:
            return ground(extractive.run(request, note=f"Generative analysis failed: {exc} Extractive results are shown instead."),
                          request.records)
    return ground(extractive.run(request), request.records)

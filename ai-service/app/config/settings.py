from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """AI service configuration, read from environment variables.

    Evidence is only sent to an external model when BOTH an API key is configured AND
    AI_ALLOW_EXTERNAL_PROCESSING is explicitly true. Otherwise the local, deterministic
    extractive engine is used and no evidence leaves the service.
    """

    model_config = SettingsConfigDict(env_file=None, extra="ignore")

    service_name: str = "Forensight AI Service"
    version: str = "1.0.0"

    anthropic_api_key: str | None = None
    ai_allow_external_processing: bool = False
    ai_model: str = "claude-opus-5-5"
    ai_effort: str = "medium"
    ai_timeout_seconds: float = 90.0
    ai_max_tokens: int = 16000
    # Server-side refusal fallback (routes declined requests to another model by refusal category).
    ai_refusal_fallback: bool = True

    max_records: int = 500

    @property
    def llm_configured(self) -> bool:
        return bool(self.anthropic_api_key and self.anthropic_api_key.strip())

    @property
    def llm_enabled(self) -> bool:
        return self.llm_configured and self.ai_allow_external_processing


settings = Settings()

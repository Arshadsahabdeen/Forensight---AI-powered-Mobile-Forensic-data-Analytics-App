from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    service_name: str = "ForensicIQ AI Service"
    version: str = "0.1.0"


settings = Settings()

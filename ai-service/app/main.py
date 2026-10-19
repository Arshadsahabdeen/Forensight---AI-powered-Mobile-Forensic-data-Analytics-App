import logging

from fastapi import FastAPI

from app.config.settings import settings
from app.routers import ai, health

# Never log request bodies: they contain evidence.
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")

app = FastAPI(
    title="Forensight AI Service",
    version=settings.version,
    description="Evidence-grounded search, summarisation and entity extraction. Called only by the Forensight backend.",
)

app.include_router(health.router)
app.include_router(ai.router)

from fastapi import FastAPI
from pydantic import BaseModel, ConfigDict
from typing import Any, Dict, List

app = FastAPI(
    title="Forensic AI Analysis Service",
    version="1.0.0"
)


class AnalysisRequest(BaseModel):
    model_config = ConfigDict(extra="allow")

    evidence: List[Dict[str, Any]]


@app.get("/health")
def health():
    return {"status": "UP"}


@app.post("/analyze")
def analyze(request: AnalysisRequest):

    evidence = request.evidence

    phone_numbers = set()
    contacts = set()
    locations = set()
    keywords = []

    communication_types = {
        "SMS",
        "CALL",
        "MESSAGE",
        "MESSAGES"
    }

    communication_count = 0
    location_count = 0

    all_text = ""

    for item in evidence:

        artifact_type = str(
            item.get("artifactType")
            or item.get("artifact_type")
            or ""
        ).upper()

        content = str(
            item.get("content")
            or ""
        )

        source = str(
            item.get("source")
            or ""
        ).strip()

        target = str(
            item.get("target")
            or ""
        ).strip()

        phone = str(
            item.get("phoneNumber")
            or item.get("phone_number")
            or ""
        ).strip()

        contact = str(
            item.get("contactName")
            or item.get("contact_name")
            or item.get("title")
            or ""
        ).strip()

        location = str(
            item.get("location")
            or ""
        ).strip()

        all_text += " " + content.lower()

        if source:
            phone_numbers.add(source)

        if target:
            phone_numbers.add(target)

        if phone:
            phone_numbers.add(phone)

        if contact:
            contacts.add(contact)

        if location:
            locations.add(location)

        if artifact_type in communication_types:
            communication_count += 1

        if artifact_type == "LOCATION":
            location_count += 1

    suspicious_keywords = [
        "meet",
        "meeting",
        "money",
        "payment",
        "transfer",
        "urgent",
        "secret",
        "kill",
        "weapon",
        "drugs",
        "threat",
        "attack",
        "password",
        "hide"
    ]

    for keyword in suspicious_keywords:

        if keyword in all_text:
            keywords.append(keyword)

    findings = []

    if communication_count:
        findings.append(
            f"{communication_count} communication artifacts identified."
        )

    if phone_numbers:
        findings.append(
            f"{len(phone_numbers)} unique phone identifiers correlated."
        )

    if location_count:
        findings.append(
            f"{location_count} location artifacts identified."
        )

    if keywords:
        findings.append(
            "Potentially significant keywords detected: "
            + ", ".join(keywords)
        )

    if not findings:
        findings.append(
            "No significant patterns detected in the supplied evidence."
        )

    return {
        "status": "SUCCESS",

        "summary": (
            f"Analyzed {len(evidence)} forensic evidence artifacts."
        ),

        "entities": {
            "phoneNumbers": sorted(phone_numbers),
            "contacts": sorted(contacts),
            "locations": sorted(locations)
        },

        "keywords": keywords,

        "statistics": {
            "totalEvidence": len(evidence),
            "communications": communication_count,
            "locations": location_count,
            "uniquePhones": len(phone_numbers),
            "contacts": len(contacts)
        },

        "findings": findings
    }
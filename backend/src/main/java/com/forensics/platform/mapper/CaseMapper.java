package com.forensics.platform.mapper;

import com.forensics.platform.dto.CaseDtos.CaseListItem;
import com.forensics.platform.dto.CaseDtos.CaseRequest;
import com.forensics.platform.dto.CaseDtos.CaseResponse;
import com.forensics.platform.entity.InvestigationCase;
import org.springframework.stereotype.Component;

@Component
public class CaseMapper {
    public InvestigationCase toEntity(CaseRequest request) {
        InvestigationCase entity = new InvestigationCase();
        applyRequest(entity, request);
        return entity;
    }

    public void applyRequest(InvestigationCase entity, CaseRequest request) {
        entity.setCaseNumber(request.caseNumber().trim());
        entity.setTitle(request.title().trim());
        entity.setDescription(request.description().trim());
        entity.setStatus(request.status());
        entity.setPriority(request.priority());
        entity.setLeadInvestigator(request.leadInvestigator().trim());
        entity.setCategory(request.category().trim());
        entity.setCaseType(request.caseType().trim());
        entity.setLocation(request.location() == null ? null : request.location().trim());
        entity.setNotes(request.notes() == null ? null : request.notes().trim());
    }

    public CaseResponse toResponse(InvestigationCase entity) {
        return new CaseResponse(
                entity.getId(),
                entity.getCaseNumber(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getPriority(),
                entity.getLeadInvestigator(),
                entity.getCategory(),
                entity.getCaseType(),
                entity.getLocation(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public CaseListItem toListItem(InvestigationCase entity) {
        return new CaseListItem(
                entity.getId(),
                entity.getCaseNumber(),
                entity.getTitle(),
                entity.getStatus(),
                entity.getPriority(),
                entity.getLeadInvestigator(),
                entity.getCategory(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

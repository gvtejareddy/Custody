package com.bank.custody.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Transactional
    public void record(String action, String aggregateId, String details) {
        AuditEvent e = new AuditEvent();
        e.setAction(action);
        e.setAggregateId(aggregateId);
        e.setDetails(details);
        auditRepository.save(e);
    }
}

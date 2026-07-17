package com.wedding.platform.platform.audit;

import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final OperationLogRepository repository;

    public AuditLogService(OperationLogRepository repository) {
        this.repository = repository;
    }

    public void record(
            Long operatorId,
            String operatorType,
            String module,
            String action,
            String businessType,
            Long businessId,
            String reason,
            String ipAddress
    ) {
        OperationLog log = new OperationLog();
        log.setOperatorId(operatorId);
        log.setOperatorType(operatorType);
        log.setModule(module);
        log.setAction(action);
        log.setBusinessType(businessType);
        log.setBusinessId(businessId);
        log.setReason(reason);
        log.setIpAddress(ipAddress);
        repository.save(log);
    }
}

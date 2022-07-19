package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.models.common.ActionLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CqlLibraryActionLogRepository
    extends MongoRepository<ActionLog, String>, ActionLogRepository {}

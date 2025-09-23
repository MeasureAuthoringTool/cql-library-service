package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.models.common.LibraryActionLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CqlLibraryActionLogRepository
    extends MongoRepository<LibraryActionLog, String>, ActionLogRepository {
  Optional<LibraryActionLog> findByTargetId(String targetId);
}

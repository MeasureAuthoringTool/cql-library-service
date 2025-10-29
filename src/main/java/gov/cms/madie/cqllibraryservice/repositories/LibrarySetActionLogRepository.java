package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.models.common.LibrarySetActionLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface LibrarySetActionLogRepository
    extends MongoRepository<LibrarySetActionLog, String>, ActionLogRepository {
  Optional<LibrarySetActionLog> findByTargetId(String targetId);

  List<LibrarySetActionLog> findByTargetIdIn(List<String> targetIds);
}

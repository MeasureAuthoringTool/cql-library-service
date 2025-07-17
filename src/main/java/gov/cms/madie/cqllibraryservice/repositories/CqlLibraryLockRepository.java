package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.dto.LibraryLock;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CqlLibraryLockRepository extends MongoRepository<LibraryLock, String> {
    Optional<LibraryLock> findByLibraryId(String libraryId);
    void deleteByLibraryId(String libraryId);
}

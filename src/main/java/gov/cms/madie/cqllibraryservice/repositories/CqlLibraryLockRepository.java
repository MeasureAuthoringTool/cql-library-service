package gov.cms.madie.cqllibraryservice.repositories;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

import gov.cms.madie.cqllibraryservice.locks.CqlLibraryLock;

public interface CqlLibraryLockRepository extends MongoRepository<CqlLibraryLock, String> {

  Optional<CqlLibraryLock> findByCqlLibraryId(String cqlLibraryId);

  void deleteByCqlLibraryId(String cqlLibraryId);

  List<CqlLibraryLock> findAllByLockedBy(String lockedBy);
}

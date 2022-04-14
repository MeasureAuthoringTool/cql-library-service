package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.models.CqlLibrary;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CqlLibraryRepository
    extends MongoRepository<CqlLibrary, String>, CqlLibraryVersionRepository {
  Optional<CqlLibrary> findByCqlLibraryName(String cqlLibraryName);

  boolean existsByCqlLibraryName(String cqlLibraryName);

  boolean existsByGroupIdAndDraft(String groupId, boolean draft);

  List<CqlLibrary> findAllByCreatedBy(String user);
}
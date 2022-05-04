package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.models.CqlLibrary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.ExistsQuery;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CqlLibraryRepository
    extends MongoRepository<CqlLibrary, String>, CqlLibraryVersionRepository {

  Optional<CqlLibrary> findByCqlLibraryName(String cqlLibraryName);

  @ExistsQuery("{cqlLibraryName: {$regex: '^?0$', $options: 'i'}}")
  boolean existsByCqlLibraryName(String cqlLibraryName);

  boolean existsByGroupIdAndDraft(String groupId, boolean draft);

  List<CqlLibrary> findAllByCreatedBy(String user);
}

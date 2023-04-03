package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.library.CqlLibrary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.ExistsQuery;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface CqlLibraryRepository
    extends MongoRepository<CqlLibrary, String>, CqlLibraryVersionRepository {

  Optional<CqlLibrary> findByCqlLibraryName(String cqlLibraryName);

  @ExistsQuery("{cqlLibraryName: {$regex: '^?0$', $options: 'i'}}")
  boolean existsByCqlLibraryName(String cqlLibraryName);

  boolean existsByGroupIdAndDraft(String groupId, boolean draft);

  @Query("{createdBy : { $regex : ?0, $options: 'i' }}")
  List<CqlLibrary> findAllByCreatedBy(String user);

  @Query(fields = "{cqlLibraryName: 1, draft:  1, cql: 1}")
  List<CqlLibrary> findAllByCqlLibraryNameAndDraftAndVersion(
      String cqlLibraryName, boolean draft, Version version);

  @Query(fields = "{cqlLibraryName: 1, draft:  1, cql: 1}")
  List<CqlLibrary> findAllByCqlLibraryNameAndDraftAndVersionAndModel(
      String cqlLibraryName, boolean draft, Version version, String model);
}

package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.library.CqlLibrary;

import java.util.List;

import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.ExistsQuery;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CqlLibraryRepository
    extends MongoRepository<CqlLibrary, String>, CqlLibraryVersionRepository, LibraryAclRepository {

  @ExistsQuery("{cqlLibraryName: {$regex: '^?0$', $options: 'i'}}")
  boolean existsByCqlLibraryName(String cqlLibraryName);

  boolean existsByLibrarySetIdAndDraft(String librarySetId, boolean draft);

  List<CqlLibrary> findAllByCqlLibraryNameAndDraftAndVersion(
      String cqlLibraryName, boolean draft, Version version);

  List<CqlLibrary> findAllByCqlLibraryNameAndDraftAndVersionAndModel(
      String cqlLibraryName, boolean draft, Version version, String model);

  @Aggregation(
      pipeline = {
        "{'$group': {'_id': '$librarySetId',"
            + "'librarySetId': {'$first':'$librarySetId'},"
            + "'createdBy': {'$first':'$createdBy'}}}",
        "{'$sort': {'createdAt':1}}"
      })
  List<CqlLibrary> findByCqlLibrarySetId();
}

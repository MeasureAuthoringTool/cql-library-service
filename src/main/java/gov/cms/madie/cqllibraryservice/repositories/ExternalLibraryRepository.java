package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.dto.NamespaceDTO;
import gov.cms.madie.cqllibraryservice.models.ExternalLibrary;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ExternalLibraryRepository extends MongoRepository<ExternalLibrary, String> {

  Optional<ExternalLibrary> findByPackageCanonicalAndLibraryName(
      String namespaceCanonical, String libraryName);

  Optional<ExternalLibrary> findByPackageCanonicalAndLibraryNameAndVersion(
      String namespaceCanonical, String libraryName, String version);

  Optional<ExternalLibrary> findByNamespacePrefixAndLibraryNameAndVersion(
      String namespacePrefix, String libraryName, String version);

  boolean existsByPackageCanonicalAndLibraryNameAndVersion(
      String canonical, String libraryName, String version);

  List<ExternalLibrary> findAllByLibrarySetId(String librarySetId);

  /**
   * Case-insensitive regex search against the fully qualified library name ({@code
   * namespacePrefix.libraryName}), so a search term can match the prefix, the library name or both.
   * CQL content and the FHIR resource are excluded from the results.
   */
  @Query(
      value =
          "{'$expr': {'$regexMatch': {'input': {'$concat': [{'$ifNull': ['$namespacePrefix', '']},"
              + " '.', '$libraryName']}, 'regex': ?0, 'options': 'i'}}}",
      fields = "{'cqlContent': 0, 'fhirResource': 0}")
  List<ExternalLibrary> searchByQualifiedLibraryName(String regex);

  /**
   * Returns every distinct {@code (packageCanonical, namespacePrefix)} pair present in the External
   * Libraries collection, sorted by canonical.
   */
  @Aggregation(
      pipeline = {
        "{'$match': {'packageCanonical': {'$nin': [null, '']},"
            + " 'namespacePrefix': {'$nin': [null, '']}}}",
        "{'$group': {'_id': {'canonical': '$packageCanonical', 'prefix': '$namespacePrefix'}}}",
        "{'$project': {'_id': 0,"
            + " 'namespaceCanonical': '$_id.canonical',"
            + " 'namespacePrefix': '$_id.prefix'}}",
        "{'$sort': {'namespaceCanonical': 1}}"
      })
  List<NamespaceDTO> findDistinctNamespaces();
}

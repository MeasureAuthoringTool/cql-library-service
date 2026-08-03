package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.models.ExternalLibrary;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ExternalLibraryRepository extends MongoRepository<ExternalLibrary, String> {

  Optional<ExternalLibrary> findByCanonicalAndLibraryName(
      String namespaceCanonical, String libraryName);

  boolean existsByCanonicalAndLibraryNameAndVersion(
      String canonical, String libraryName, String version);
}

package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.models.ExternalLibrary;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ExternalLibraryRepository extends MongoRepository<ExternalLibrary, String> {

  Optional<ExternalLibrary> findByPackageCanonicalAndLibraryName(
      String namespaceCanonical, String libraryName);

  boolean existsByPackageCanonicalAndLibraryNameAndVersion(
      String canonical, String libraryName, String version);
}

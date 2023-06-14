package gov.cms.madie.cqllibraryservice.repositories;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import gov.cms.madie.models.library.LibrarySet;

public interface LibrarySetRepository extends MongoRepository<LibrarySet, String> {

  boolean existsByLibrarySetId(String librarySetId);

  Optional<LibrarySet> findByLibrarySetId(String librarySetId);
}

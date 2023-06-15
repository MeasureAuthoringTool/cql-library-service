package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.models.library.LibrarySet;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface LibrarySetRepository extends MongoRepository<LibrarySet, String> {

  boolean existsByLibrarySetId(String librarySetId);

  Optional<LibrarySet> findByLibrarySetId(String LibrarySetId);
}

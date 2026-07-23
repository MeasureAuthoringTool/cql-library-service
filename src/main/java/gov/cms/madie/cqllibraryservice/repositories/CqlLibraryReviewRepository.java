package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.models.library.CqlLibraryReview;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CqlLibraryReviewRepository extends MongoRepository<CqlLibraryReview, String> {

  Optional<CqlLibraryReview> findByLibraryId(String libraryId);

  List<CqlLibraryReview> findAllByLibrarySetId(String librarySetId);

  List<CqlLibraryReview> findAllByLibraryIdIn(Collection<String> libraryIds);

  boolean existsByLibraryId(String libraryId);
}

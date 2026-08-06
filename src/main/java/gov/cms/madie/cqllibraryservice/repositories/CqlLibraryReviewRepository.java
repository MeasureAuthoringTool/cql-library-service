package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.models.common.ReviewStatus;
import gov.cms.madie.models.library.CqlLibraryReview;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CqlLibraryReviewRepository extends MongoRepository<CqlLibraryReview, String> {

  Optional<CqlLibraryReview> findByLibraryId(String libraryId);

  List<CqlLibraryReview> findAllByLibrarySetId(String librarySetId);

  List<CqlLibraryReview> findAllByStatus(ReviewStatus status);

  boolean existsByLibraryId(String libraryId);
}

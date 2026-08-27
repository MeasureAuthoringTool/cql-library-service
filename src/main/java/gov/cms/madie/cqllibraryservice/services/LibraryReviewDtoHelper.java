package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.models.common.ReviewStatus;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.CqlLibraryReview;
import gov.cms.madie.models.library.LibrarySet;
import java.util.List;
import java.util.Map;

final class LibraryReviewDtoHelper {

  private LibraryReviewDtoHelper() {}

  static LibraryListDTO toReviewLibraryListDTO(
      CqlLibrary library, CqlLibraryReview review, LibrarySet librarySet) {
    return LibraryListDTO.builder()
        .id(library.getId())
        .librarySetId(library.getLibrarySetId())
        .cqlLibraryName(library.getCqlLibraryName())
        .model(library.getModel())
        .version(library.getVersion())
        .draft(library.isDraft())
        .createdAt(library.getCreatedAt())
        .lastModifiedAt(library.getLastModifiedAt())
        .librarySet(librarySet)
        .reviewStatus(toReviewStatusDisplayName(review != null ? review.getStatus() : null))
        .reviewers(review != null ? review.getReviewers() : null)
        .build();
  }

  static String toReviewStatusDisplayName(ReviewStatus reviewStatus) {
    if (reviewStatus == null) {
      return "";
    }
    switch (reviewStatus) {
      case READY_FOR_REVIEW:
        return "Ready";
      case IN_PROGRESS:
        return "In Progress";
      case COMPLETE:
        return "Complete";
      default:
        return "";
    }
  }

  static void applyReviewStatus(
      List<LibraryListDTO> libraries, Map<String, ReviewStatus> statusByLibraryId) {
    libraries.forEach(
        library ->
            library.setReviewStatus(
                toReviewStatusDisplayName(statusByLibraryId.get(library.getId()))));
  }
}

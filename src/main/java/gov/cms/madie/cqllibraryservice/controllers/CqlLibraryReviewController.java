package gov.cms.madie.cqllibraryservice.controllers;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryReviewService;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.library.CqlLibraryReview;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/cql-libraries")
@RequiredArgsConstructor
public class CqlLibraryReviewController {

  private final CqlLibraryReviewService cqlLibraryReviewService;

  @PostMapping("/{libraryId}/review")
  public ResponseEntity<CqlLibraryReview> createReview(
      @PathVariable String libraryId, @RequestBody CqlLibraryReview review, Principal principal) {
    final String username = principal.getName().toLowerCase();
    log.info("User [{}] is creating a review for CQL Library [{}]", username, libraryId);
    review.setLibraryId(libraryId);
    CqlLibraryReview created = cqlLibraryReviewService.createReview(review, username);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/{libraryId}/review")
  public ResponseEntity<CqlLibraryReview> updateReview(
      @PathVariable String libraryId, @RequestBody CqlLibraryReview review, Principal principal) {
    final String username = principal.getName().toLowerCase();
    log.info("User [{}] is updating the review for CQL Library [{}]", username, libraryId);
    return ResponseEntity.ok(cqlLibraryReviewService.updateReview(libraryId, review, username));
  }

  @GetMapping("/{libraryId}/review")
  public ResponseEntity<CqlLibraryReview> getReviewByLibraryId(@PathVariable String libraryId) {
    return ResponseEntity.ok(cqlLibraryReviewService.getReviewByLibraryId(libraryId));
  }

  @GetMapping("/library-set/{librarySetId}/reviews")
  public ResponseEntity<List<CqlLibraryReview>> getReviewsByLibrarySetId(
      @PathVariable String librarySetId) {
    return ResponseEntity.ok(cqlLibraryReviewService.getReviewsByLibrarySetId(librarySetId));
  }

  @GetMapping("/reviews")
  public ResponseEntity<List<LibraryListDTO>> getLibrariesInReview(
      Principal principal,
      @RequestHeader("Authorization") String accessToken,
      @RequestParam(required = false, defaultValue = "ALL", name = "ownershipType")
          OwnershipType ownershipType) {
    final String username = principal.getName().toLowerCase();
    log.info("User [{}] is fetching all libraries under review", username);
    return ResponseEntity.ok(
        cqlLibraryReviewService.getLibrariesInReview(username, accessToken, ownershipType));
  }
}

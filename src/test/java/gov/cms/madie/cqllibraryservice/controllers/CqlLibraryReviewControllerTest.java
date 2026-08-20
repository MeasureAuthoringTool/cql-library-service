package gov.cms.madie.cqllibraryservice.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryReviewService;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.common.ReviewStatus;
import gov.cms.madie.models.library.CqlLibraryReview;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class CqlLibraryReviewControllerTest {

  @InjectMocks private CqlLibraryReviewController controller;

  @Mock private CqlLibraryReviewService cqlLibraryReviewService;

  @Captor private ArgumentCaptor<CqlLibraryReview> reviewCaptor;

  private Principal principal;
  private CqlLibraryReview review;

  @BeforeEach
  void setUp() {
    principal = mock(Principal.class);
    review =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .librarySetId("set-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment("Looks good")
            .build();
  }

  @Test
  void createReviewReturnsCreated() {
    when(principal.getName()).thenReturn("test.user");
    when(cqlLibraryReviewService.createReview(any(CqlLibraryReview.class), anyString()))
        .thenReturn(review);

    ResponseEntity<CqlLibraryReview> response = controller.createReview("lib-1", review, principal);

    assertNotNull(response);
    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals("review-1", response.getBody().getId());
    verify(cqlLibraryReviewService).createReview(reviewCaptor.capture(), anyString());
    assertEquals("lib-1", reviewCaptor.getValue().getLibraryId());
  }

  @Test
  void createReviewSetsLibraryIdFromPath() {
    when(principal.getName()).thenReturn("test.user");
    CqlLibraryReview payload =
        CqlLibraryReview.builder().status(ReviewStatus.READY_FOR_REVIEW).build();
    when(cqlLibraryReviewService.createReview(any(CqlLibraryReview.class), anyString()))
        .thenReturn(review);

    controller.createReview("path-lib", payload, principal);

    verify(cqlLibraryReviewService).createReview(reviewCaptor.capture(), anyString());
    assertEquals("path-lib", reviewCaptor.getValue().getLibraryId());
  }

  @Test
  void updateReviewReturnsOk() {
    when(principal.getName()).thenReturn("test.user");
    when(cqlLibraryReviewService.updateReview(
            anyString(), any(CqlLibraryReview.class), anyString()))
        .thenReturn(review);

    ResponseEntity<CqlLibraryReview> response = controller.updateReview("lib-1", review, principal);

    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("review-1", response.getBody().getId());
    verify(cqlLibraryReviewService).updateReview("lib-1", review, "test.user");
  }

  @Test
  void getReviewByLibraryIdReturnsOk() {
    when(cqlLibraryReviewService.getReviewByLibraryId("lib-1")).thenReturn(review);

    ResponseEntity<CqlLibraryReview> response = controller.getReviewByLibraryId("lib-1");

    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("lib-1", response.getBody().getLibraryId());
  }

  @Test
  void getReviewsByLibrarySetIdReturnsList() {
    when(cqlLibraryReviewService.getReviewsByLibrarySetId("set-1")).thenReturn(List.of(review));

    ResponseEntity<List<CqlLibraryReview>> response = controller.getReviewsByLibrarySetId("set-1");

    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(1, response.getBody().size());
  }

  @Test
  void getAllReadyForReviewReturnsListOfLibraries() {
    when(principal.getName()).thenReturn("test.user");
    LibraryListDTO library =
        LibraryListDTO.builder().id("lib-1").librarySetId("set-1").reviewStatus("Ready").build();
    when(cqlLibraryReviewService.getAllReadyForReview(
            anyString(), anyString(), any(OwnershipType.class)))
        .thenReturn(List.of(library));

    ResponseEntity<List<LibraryListDTO>> response =
        controller.getAllReadyForReview(principal, "Bearer token", OwnershipType.ALL);

    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(1, response.getBody().size());
    assertEquals("lib-1", response.getBody().get(0).getId());
    verify(cqlLibraryReviewService)
        .getAllReadyForReview("test.user", "Bearer token", OwnershipType.ALL);
  }
}

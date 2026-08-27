package gov.cms.madie.cqllibraryservice.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.models.dto.UserDetailsDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LibraryUserDetailsHelperTest {

  @Mock private UserServiceClient userServiceClient;

  @Test
  void enrichWithUserDetailsSetsDashWhenReviewerIdIsBlank() {
    LibraryListDTO library = LibraryListDTO.builder().reviewers(List.of("   ")).build();
    when(userServiceClient.getBulkUserDetails(anyList())).thenReturn(Map.of());

    LibraryUserDetailsHelper.enrichWithUserDetails(List.of(library), userServiceClient);

    assertEquals(List.of("-"), library.getReviewers());
  }

  @Test
  void enrichWithUserDetailsFallsBackToReviewerIdWhenUserDetailsMissing() {
    String reviewerId = "reviewer-123";
    LibraryListDTO library = LibraryListDTO.builder().reviewers(List.of(reviewerId)).build();
    when(userServiceClient.getBulkUserDetails(anyList())).thenReturn(Map.of());

    LibraryUserDetailsHelper.enrichWithUserDetails(List.of(library), userServiceClient);

    assertEquals(List.of(reviewerId), library.getReviewers());
  }

  @Test
  void enrichWithUserDetailsUsesFullNameWhenReviewerDetailsExist() {
    String reviewerId = "reviewer-1";
    UserDetailsDto details = new UserDetailsDto();
    details.setFirstName("Alex");
    details.setLastName("Smith");
    LibraryListDTO library = LibraryListDTO.builder().reviewers(List.of(reviewerId)).build();
    when(userServiceClient.getBulkUserDetails(anyList())).thenReturn(Map.of(reviewerId, details));

    LibraryUserDetailsHelper.enrichWithUserDetails(List.of(library), userServiceClient);

    assertEquals(List.of("Alex Smith"), library.getReviewers());
  }

  @Test
  void enrichWithUserDetailsNoOpsWhenLibrariesEmpty() {
    LibraryUserDetailsHelper.enrichWithUserDetails(List.of(), userServiceClient);

    verify(userServiceClient, times(0)).getBulkUserDetails(anyList());
  }

  @Test
  void getFullNameReturnsLastNameWhenFirstNameMissing() {
    UserDetailsDto userDetails = UserDetailsDto.builder().firstName(null).lastName("Doe").build();

    assertEquals("Doe", LibraryUserDetailsHelper.getFullName(userDetails));
  }

  @Test
  void getFullNameReturnsFirstNameWhenLastNameMissing() {
    UserDetailsDto userDetails = UserDetailsDto.builder().firstName("John").lastName(null).build();

    assertEquals("John", LibraryUserDetailsHelper.getFullName(userDetails));
  }

  @Test
  void getFullNameReturnsEmptyWhenNamesMissing() {
    UserDetailsDto userDetails = UserDetailsDto.builder().firstName(null).lastName(null).build();

    assertEquals("", LibraryUserDetailsHelper.getFullName(userDetails));
  }
}

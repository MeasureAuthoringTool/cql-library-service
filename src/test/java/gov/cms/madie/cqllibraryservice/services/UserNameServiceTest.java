package gov.cms.madie.cqllibraryservice.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.cms.madie.models.dto.UserDetailsDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserNameServiceTest {

  @Mock private UserServiceClient userServiceClient;

  @InjectMocks private UserNameService userNameService;

  @Test
  void resolvesTheHarpIdToTheUsersFullName() {
    when(userServiceClient.getBulkUserDetails(List.of("jjones")))
        .thenReturn(
            Map.of(
                "jjones",
                UserDetailsDto.builder().firstName("Jonathan").lastName("Jones").build()));

    assertEquals("Jonathan Jones", userNameService.getDisplayName("jjones"));
  }

  @Test
  void usesWhicheverNameIsPresent() {
    when(userServiceClient.getBulkUserDetails(List.of("jjones")))
        .thenReturn(Map.of("jjones", UserDetailsDto.builder().firstName("Jonathan").build()));

    assertEquals("Jonathan", userNameService.getDisplayName("jjones"));

    when(userServiceClient.getBulkUserDetails(List.of("asmith")))
        .thenReturn(Map.of("asmith", UserDetailsDto.builder().lastName("Smith").build()));

    assertEquals("Smith", userNameService.getDisplayName("asmith"));
  }

  @Test
  void keepsTheHarpIdWhenTheUserHasNoNameOnFile() {
    when(userServiceClient.getBulkUserDetails(List.of("jjones")))
        .thenReturn(Map.of("jjones", UserDetailsDto.builder().build()));

    assertEquals("jjones", userNameService.getDisplayName("jjones"));
  }

  @Test
  void keepsTheHarpIdWhenTheUserServiceReturnsNothing() {
    when(userServiceClient.getBulkUserDetails(List.of("jjones"))).thenReturn(Map.of());

    assertEquals("jjones", userNameService.getDisplayName("jjones"));
  }

  @Test
  void keepsTheHarpIdWhenTheUserServiceReturnsNull() {
    when(userServiceClient.getBulkUserDetails(List.of("jjones"))).thenReturn(null);

    assertEquals("jjones", userNameService.getDisplayName("jjones"));
  }

  @Test
  void skipsTheLookupWhenThereIsNoHarpId() {
    assertNull(userNameService.getDisplayName(null));
    assertEquals("  ", userNameService.getDisplayName("  "));

    verify(userServiceClient, never()).getBulkUserDetails(any());
  }
}

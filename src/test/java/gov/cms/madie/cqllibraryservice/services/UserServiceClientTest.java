package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.config.EnvironmentConfig;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.dto.UserRolesDto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceClientTest {
  @Mock private RestTemplate userServiceRestTemplate;
  @Mock private EnvironmentConfig environmentConfig;

  @InjectMocks private UserServiceClient userServiceClient;

  @Captor private ArgumentCaptor<HttpEntity<Void>> httpEntityCaptor;

  private static final String HARP_ID = "12345";
  private final String TOKEN = "token";

  @Test
  void getSingleUserDetailsReturnsUserDetails() {
    String harpId = "12345";
    String url = "http://example.com/users/12345/details";
    UserDetailsDto userDetails = UserDetailsDto.builder().harpId(harpId).build();

    when(environmentConfig.getUserServiceBaseUrl()).thenReturn("http://example.com");
    when(userServiceRestTemplate.exchange(
            eq(url),
            eq(HttpMethod.GET),
            org.mockito.ArgumentMatchers.any(HttpEntity.class),
            eq(UserDetailsDto.class)))
        .thenReturn(ResponseEntity.ok(userDetails));

    UserDetailsDto result = userServiceClient.getSingleUserDetails(harpId);

    assertThat(result, is(notNullValue()));
    assertThat(result.getHarpId(), is(equalTo(harpId)));
    verify(userServiceRestTemplate, times(1))
        .exchange(
            eq(url), eq(HttpMethod.GET), httpEntityCaptor.capture(), eq(UserDetailsDto.class));
    HttpHeaders headers = httpEntityCaptor.getValue().getHeaders();
    assertThat(headers.getContentType(), is(MediaType.APPLICATION_JSON));
  }

  @Test
  void getSingleUserDetailsReturnsNullForInvalidHarpId() {
    UserDetailsDto result = userServiceClient.getSingleUserDetails(null);
    assertThat(result, is(nullValue()));

    result = userServiceClient.getSingleUserDetails("");
    assertThat(result, is(nullValue()));

    verifyNoInteractions(userServiceRestTemplate);
  }

  @Test
  void getSingleUserDetailsReturnsNullOnException() {
    String harpId = "12345";
    String url = "http://example.com/users/12345/details";

    when(environmentConfig.getUserServiceBaseUrl()).thenReturn("http://example.com");
    when(userServiceRestTemplate.exchange(
            eq(url),
            eq(HttpMethod.GET),
            org.mockito.ArgumentMatchers.any(HttpEntity.class),
            eq(UserDetailsDto.class)))
        .thenThrow(new RuntimeException("Service unavailable"));

    UserDetailsDto result = userServiceClient.getSingleUserDetails(harpId);

    assertThat(result, is(nullValue()));
    verify(userServiceRestTemplate, times(1))
        .exchange(
            eq(url),
            eq(HttpMethod.GET),
            org.mockito.ArgumentMatchers.any(HttpEntity.class),
            eq(UserDetailsDto.class));
  }

  @Test
  void getBulkUserDetailsReturnsMapOfUserDetails() {
    List<String> harpIds = List.of("12345", "67890");
    String url = "http://example.com/users/details";
    Map<String, UserDetailsDto> userDetailsMap =
        Map.of(
            "12345",
                UserDetailsDto.builder()
                    .harpId("12345")
                    .firstName("John")
                    .lastName("Doe")
                    .email("john.doe@example.com")
                    .build(),
            "67890",
                UserDetailsDto.builder()
                    .harpId("67890")
                    .firstName("Jane")
                    .lastName("Smith")
                    .email("jane.smith@example.com")
                    .build());

    when(environmentConfig.getUserServiceBaseUrl()).thenReturn("http://example.com");
    when(userServiceRestTemplate.exchange(
            eq(url),
            eq(HttpMethod.POST),
            org.mockito.ArgumentMatchers.any(HttpEntity.class),
            org.mockito.ArgumentMatchers.any(ParameterizedTypeReference.class)))
        .thenReturn(
            (ResponseEntity<Map<String, UserDetailsDto>>) ResponseEntity.ok(userDetailsMap));

    Map<String, UserDetailsDto> result = userServiceClient.getBulkUserDetails(harpIds);

    assertThat(result.size(), is(2));
    assertThat(result.get("12345").getHarpId(), is(equalTo("12345")));
    assertThat(result.get("12345").getFirstName(), is(equalTo("John")));
    assertThat(result.get("12345").getLastName(), is(equalTo("Doe")));
    assertThat(result.get("12345").getEmail(), is(equalTo("john.doe@example.com")));
    assertThat(result.get("67890").getHarpId(), is(equalTo("67890")));
    assertThat(result.get("67890").getFirstName(), is(equalTo("Jane")));
    assertThat(result.get("67890").getLastName(), is(equalTo("Smith")));
    assertThat(result.get("67890").getEmail(), is(equalTo("jane.smith@example.com")));
    verify(userServiceRestTemplate, times(1))
        .exchange(
            eq(url),
            eq(HttpMethod.POST),
            org.mockito.ArgumentMatchers.any(HttpEntity.class),
            org.mockito.ArgumentMatchers.any(ParameterizedTypeReference.class));
  }

  @Test
  void getBulkUserDetailsReturnsEmptyMapForEmptyHarpIds() {
    Map<String, UserDetailsDto> result =
        userServiceClient.getBulkUserDetails(Collections.emptyList());
    assertThat(result.isEmpty(), is(true));

    verifyNoInteractions(userServiceRestTemplate);
  }

  @Test
  void getBulkUserDetailsReturnsEmptyMapOnException() {
    List<String> harpIds = List.of("12345", "67890");
    String url = "http://example.com/users/details";

    when(environmentConfig.getUserServiceBaseUrl()).thenReturn("http://example.com");
    when(userServiceRestTemplate.exchange(
            eq(url),
            eq(HttpMethod.POST),
            org.mockito.ArgumentMatchers.any(HttpEntity.class),
            org.mockito.ArgumentMatchers.any(ParameterizedTypeReference.class)))
        .thenThrow(new RuntimeException("Service unavailable"));

    Map<String, UserDetailsDto> result = userServiceClient.getBulkUserDetails(harpIds);

    assertThat(result.isEmpty(), is(true));
    verify(userServiceRestTemplate, times(1))
        .exchange(
            eq(url),
            eq(HttpMethod.POST),
            org.mockito.ArgumentMatchers.any(HttpEntity.class),
            org.mockito.ArgumentMatchers.any(ParameterizedTypeReference.class));
  }

  @Test
  public void testGetUserRoles() {
    UserRolesDto expectedUserRolesDto =
        UserRolesDto.builder().harpId(HARP_ID).roles(List.of("MADiE-User")).build();

    when(userServiceRestTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            org.mockito.ArgumentMatchers.any(HttpEntity.class),
            eq(UserRolesDto.class)))
        .thenReturn(ResponseEntity.ok(expectedUserRolesDto));

    UserRolesDto actualUserRolesDto = userServiceClient.getUserRoles(HARP_ID, TOKEN);

    assertThat(actualUserRolesDto, is(notNullValue()));
    assertThat(actualUserRolesDto.getHarpId(), is(equalTo(HARP_ID)));
    assertEquals(expectedUserRolesDto, actualUserRolesDto);
    verify(userServiceRestTemplate, times(1))
        .exchange(
            anyString(), eq(HttpMethod.GET), httpEntityCaptor.capture(), eq(UserRolesDto.class));
    HttpHeaders headers = httpEntityCaptor.getValue().getHeaders();
    assertThat(headers.getContentType(), is(MediaType.APPLICATION_JSON));
  }

  @Test
  void testGetUserRolesWithException() {
    when(userServiceRestTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            org.mockito.ArgumentMatchers.any(HttpEntity.class),
            eq(UserRolesDto.class)))
        .thenThrow(new RestClientException("Connection error"));

    UserRolesDto actualUserRolesDto = userServiceClient.getUserRoles(HARP_ID, TOKEN);

    assertNull(actualUserRolesDto);
    verify(userServiceRestTemplate, times(1))
        .exchange(
            anyString(), eq(HttpMethod.GET), httpEntityCaptor.capture(), eq(UserRolesDto.class));
  }

  @Test
  void testGetUserRolesReturnsNullWhenHarpIdIsNull() {
    UserRolesDto result = userServiceClient.getUserRoles(null, TOKEN);

    assertNull(result);
    verify(userServiceRestTemplate, never())
        .exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(UserRolesDto.class));
  }

  @Test
  public void testGetUserDetails() {
    UserDetailsDto expectedUserDetailsDto =
        UserDetailsDto.builder().harpId(HARP_ID).active(true).build();

    when(userServiceRestTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            org.mockito.ArgumentMatchers.any(HttpEntity.class),
            eq(UserDetailsDto.class)))
        .thenReturn(ResponseEntity.ok(expectedUserDetailsDto));

    UserDetailsDto actualUserDetailsDto = userServiceClient.getUserDetails(HARP_ID, TOKEN);

    assertThat(actualUserDetailsDto, is(notNullValue()));
    assertThat(actualUserDetailsDto.getHarpId(), is(equalTo(HARP_ID)));
    assertEquals(expectedUserDetailsDto, actualUserDetailsDto);
    verify(userServiceRestTemplate, times(1))
        .exchange(
            anyString(), eq(HttpMethod.GET), httpEntityCaptor.capture(), eq(UserDetailsDto.class));
    HttpHeaders headers = httpEntityCaptor.getValue().getHeaders();
    assertThat(headers.getContentType(), is(MediaType.APPLICATION_JSON));
  }

  @Test
  void testGetUserDetailsWithException() {
    when(userServiceRestTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            org.mockito.ArgumentMatchers.any(HttpEntity.class),
            eq(UserDetailsDto.class)))
        .thenThrow(new RestClientException("Connection error"));

    UserDetailsDto actualUserDetailsDto = userServiceClient.getUserDetails(HARP_ID, TOKEN);

    assertNull(actualUserDetailsDto);
    verify(userServiceRestTemplate, times(1))
        .exchange(
            anyString(), eq(HttpMethod.GET), httpEntityCaptor.capture(), eq(UserDetailsDto.class));
  }

  @Test
  void testGetUserDetailsReturnsNullWhenHarpIdIsNull() {
    UserDetailsDto result = userServiceClient.getUserDetails(null, TOKEN);

    assertNull(result);
    verify(userServiceRestTemplate, never())
        .exchange(
            anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(UserDetailsDto.class));
  }
}

package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.config.EnvironmentConfig;
import gov.cms.madie.models.dto.DetailsRequestDto;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.dto.UserRolesDto;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@AllArgsConstructor
public class UserServiceClient {

  private final RestTemplate userServiceRestTemplate;
  private EnvironmentConfig environmentConfig;

  /**
   * Fetches user details in bulk from the user service.
   *
   * @param harpIds List of HARP IDs to fetch details for
   * @return Map of HARP ID to UserDetailsDto, empty map if service call fails
   */
  public Map<String, UserDetailsDto> getBulkUserDetails(List<String> harpIds) {
    if (CollectionUtils.isEmpty(harpIds)) {
      return Collections.emptyMap();
    }

    try {
      String url = environmentConfig.getUserServiceBaseUrl() + "/users/details";
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      DetailsRequestDto requestBody = DetailsRequestDto.builder().harpIds(harpIds).build();
      HttpEntity<DetailsRequestDto> request = new HttpEntity<>(requestBody, headers);
      log.debug("Requesting user details for HARP IDs: {}", harpIds);

      ResponseEntity<Map<String, UserDetailsDto>> responseEntity =
          userServiceRestTemplate.exchange(
              url,
              HttpMethod.POST,
              request,
              new ParameterizedTypeReference<Map<String, UserDetailsDto>>() {});

      Map<String, UserDetailsDto> response = responseEntity.getBody();
      log.debug("Successfully retrieved user details for HARP IDs: {}", harpIds);
      return response != null ? response : Collections.emptyMap();
    } catch (Exception e) {
      log.error(
          "Failed to fetch user details from user service for {} HARP IDs: {}",
          harpIds.size(),
          e.getMessage(),
          e);
      return Collections.emptyMap();
    }
  }

  public UserDetailsDto getSingleUserDetails(String harpId) {
    if (harpId == null || harpId.isEmpty()) {
      return null;
    }
    try {
      String url = environmentConfig.getUserServiceBaseUrl() + "/users/" + harpId + "/details";
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<Void> request = new HttpEntity<>(headers);
      log.debug("Requesting user details for HARP ID: {}", harpId);

      ResponseEntity<UserDetailsDto> responseEntity =
          userServiceRestTemplate.exchange(url, HttpMethod.GET, request, UserDetailsDto.class);

      UserDetailsDto response = responseEntity.getBody();
      log.debug("Successfully retrieved user details for HARP ID: {}", harpId);
      return response;
    } catch (Exception e) {
      log.error(
          "Failed to fetch user details from user service for HARP ID [{}]: {}",
          harpId,
          e.getMessage(),
          e);
      return null;
    }
  }

  /**
   * Fetches UserRolesDto from the user service.
   *
   * @param harpId: HARP ID to fetch UserRolesDto for
   * @return UserRolesDto which contains the HARP ID and associated roles, or null if service call
   *     fails
   */
  public UserRolesDto getUserRoles(String harpId, String accessToken) {
    log.debug("Requesting user roles for HARP ID: [{}]", harpId);
    if (StringUtils.isBlank(harpId)) {
      return null;
    }

    String url = environmentConfig.getUserServiceBaseUrl() + "/users/" + harpId + "/roles";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    HttpEntity<Void> request = new HttpEntity<>(headers);

    try {
      log.debug("Calling user-service to request user roles for HARP ID: [{}]", harpId);
      ResponseEntity<UserRolesDto> responseEntity =
          userServiceRestTemplate.exchange(url, HttpMethod.GET, request, UserRolesDto.class);
      UserRolesDto response = responseEntity.getBody();
      log.debug("Successfully retrieved user roles for HARP ID: [{}]", harpId);
      return response;
    } catch (Exception e) {
      log.error(
          "Failed to fetch user roles from user service for HARP ID: [{}]",
          harpId,
          e.getMessage(),
          e);
      return null;
    }
  }
}

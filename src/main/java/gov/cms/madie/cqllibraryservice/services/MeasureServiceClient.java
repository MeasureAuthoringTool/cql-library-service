package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.config.EnvironmentConfig;
import gov.cms.madie.cqllibraryservice.exceptions.MeasureServiceException;
import gov.cms.madie.models.dto.LibraryUsage;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class MeasureServiceClient {
  private EnvironmentConfig environmentConfig;
  private RestTemplate restTemplate;

  public List<LibraryUsage> getLibraryUsageInMeasures(String libraryName, String accessToken) {
    try {
      URI uri =
          URI.create(
              environmentConfig.getMeasureServiceBaseUrl()
                  + "/measures/library/usage?libraryName="
                  + libraryName);
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.TEXT_PLAIN);
      headers.set(HttpHeaders.AUTHORIZATION, accessToken);
      ResponseEntity<List<LibraryUsage>> responseEntity =
          restTemplate.exchange(
              new RequestEntity<>(headers, HttpMethod.GET, uri),
              new ParameterizedTypeReference<>() {});
      return responseEntity.getBody();
    } catch (Exception ex) {
      String message = "An error occurred while fetching library usage in measures";
      log.error(message, ex);
      throw new MeasureServiceException(message);
    }
  }
}

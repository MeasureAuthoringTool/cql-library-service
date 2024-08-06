package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.config.EnvironmentConfig;
import gov.cms.madie.cqllibraryservice.dto.LibraryUsage;
import gov.cms.madie.cqllibraryservice.exceptions.MeasureServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MeasureServiceClientTest {
  @Mock private EnvironmentConfig environmentConfig;
  @Mock private RestTemplate restTemplate;

  @InjectMocks private MeasureServiceClient measureServiceClient;

  private final String token = "124";
  private final String apiKey = "key";
  private final String libraryName = "Test";

  @Test
  void testGetLibraryUsageInMeasures() {
    String owner = "john";
    LibraryUsage libraryUsage = LibraryUsage.builder().name(libraryName).owner(owner).build();
    when(environmentConfig.getMeasureServiceBaseUrl()).thenReturn("url");
    when(restTemplate.exchange(any(RequestEntity.class), any(ParameterizedTypeReference.class)))
        .thenReturn(ResponseEntity.ok(List.of(libraryUsage)));
    List<LibraryUsage> libraryUsages =
        measureServiceClient.getLibraryUsageInMeasures(libraryName, token, apiKey);
    assertThat(libraryUsages.size(), is(equalTo(1)));
    assertThat(libraryUsages.get(0).getName(), is(equalTo(libraryName)));
    assertThat(libraryUsages.get(0).getOwner(), is(equalTo(owner)));
  }

  @Test
  void testGetLibraryUsageInMeasuresThrowsException() {
    String message = "An error occurred while fetching library usage in measures";
    when(environmentConfig.getMeasureServiceBaseUrl()).thenReturn("url");
    doThrow(new RestClientException(message))
        .when(restTemplate)
        .exchange(any(RequestEntity.class), any(ParameterizedTypeReference.class));
    Exception ex =
        assertThrows(
            MeasureServiceException.class,
            () -> measureServiceClient.getLibraryUsageInMeasures(libraryName, token, apiKey));
    assertThat(ex.getMessage(), is(equalTo(message)));
  }
}

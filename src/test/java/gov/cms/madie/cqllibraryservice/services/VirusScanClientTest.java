package gov.cms.madie.cqllibraryservice.services;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.cms.madie.cqllibraryservice.config.VirusScanConfig;
import gov.cms.madie.models.scanner.VirusScanResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class VirusScanClientTest {

  @Mock private VirusScanConfig virusScanConfig;
  @Mock private RestTemplate virusScanRestTemplate;
  @Mock private Resource fileResource;

  @InjectMocks private VirusScanClient virusScanClient;

  @Test
  void scanFileReturnsCleanResultWhenScanningIsDisabled() {
    when(virusScanConfig.isScanDisabled()).thenReturn(true);

    VirusScanResponseDto result = virusScanClient.scanFile(fileResource);

    assertNotNull(result);
    assertThat(result.getFilesScanned(), is(equalTo(1)));
    assertThat(result.getCleanFileCount(), is(equalTo(1)));
    verify(virusScanRestTemplate, never()).exchange(any(), eq(VirusScanResponseDto.class));
  }

  @Test
  void scanFileCallsScanEndpointAndReturnsResponseWhenScanningIsEnabled() {
    VirusScanResponseDto expectedResponse =
        VirusScanResponseDto.builder().filesScanned(1).cleanFileCount(1).build();

    when(virusScanConfig.isScanDisabled()).thenReturn(false);
    when(virusScanConfig.getBaseUrl()).thenReturn("http://virus-scan-service");
    when(virusScanConfig.getScanFileUri()).thenReturn("/api/v1/scan");
    when(virusScanConfig.getApiKey()).thenReturn("test-api-key");
    when(virusScanRestTemplate.exchange(any(), eq(VirusScanResponseDto.class)))
        .thenReturn(ResponseEntity.ok(expectedResponse));

    VirusScanResponseDto result = virusScanClient.scanFile(fileResource);

    assertNotNull(result);
    assertThat(result.getFilesScanned(), is(equalTo(1)));
    assertThat(result.getCleanFileCount(), is(equalTo(1)));
    verify(virusScanRestTemplate).exchange(any(), eq(VirusScanResponseDto.class));
  }

  @Test
  void scanFileReturnsNullWhenScanResponseBodyIsNull() {
    when(virusScanConfig.isScanDisabled()).thenReturn(false);
    when(virusScanConfig.getBaseUrl()).thenReturn("http://virus-scan-service");
    when(virusScanConfig.getScanFileUri()).thenReturn("/api/v1/scan");
    when(virusScanConfig.getApiKey()).thenReturn("test-api-key");
    when(virusScanRestTemplate.exchange(any(), eq(VirusScanResponseDto.class)))
        .thenReturn(ResponseEntity.ok(null));

    VirusScanResponseDto result = virusScanClient.scanFile(fileResource);

    assertThat(result, is(equalTo(null)));
  }
}

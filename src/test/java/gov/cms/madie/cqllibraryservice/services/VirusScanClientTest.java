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

import java.util.List;

@ExtendWith(MockitoExtension.class)
class VirusScanClientTest {

  @Mock private VirusScanConfig virusScanConfig;
  @Mock private RestTemplate virusScanRestTemplate;
  @Mock private Resource fileResource1;
  @Mock private Resource fileResource2;

  @InjectMocks private VirusScanClient virusScanClient;

  @Test
  void scanFilesReturnsCleanResultForAllFilesWhenScanningIsDisabled() {
    when(virusScanConfig.isScanDisabled()).thenReturn(true);

    List<Resource> files = List.of(fileResource1, fileResource2);
    VirusScanResponseDto result = virusScanClient.scanFiles(files);

    assertNotNull(result);
    assertThat(result.getFilesScanned(), is(equalTo(2)));
    assertThat(result.getCleanFileCount(), is(equalTo(2)));
    verify(virusScanRestTemplate, never()).exchange(any(), eq(VirusScanResponseDto.class));
  }

  @Test
  void scanFilesCallsScanEndpointAndReturnsResponseWhenScanningIsEnabled() {
    VirusScanResponseDto expectedResponse =
        VirusScanResponseDto.builder().filesScanned(2).cleanFileCount(2).build();

    when(virusScanConfig.isScanDisabled()).thenReturn(false);
    when(virusScanConfig.getBaseUrl()).thenReturn("http://virus-scan-service");
    when(virusScanConfig.getScanFileUri()).thenReturn("/api/v1/scan");
    when(virusScanConfig.getApiKey()).thenReturn("test");
    when(virusScanRestTemplate.exchange(any(), eq(VirusScanResponseDto.class)))
        .thenReturn(ResponseEntity.ok(expectedResponse));

    List<Resource> files = List.of(fileResource1, fileResource2);
    VirusScanResponseDto result = virusScanClient.scanFiles(files);

    assertNotNull(result);
    assertThat(result.getFilesScanned(), is(equalTo(2)));
    assertThat(result.getCleanFileCount(), is(equalTo(2)));
    verify(virusScanRestTemplate).exchange(any(), eq(VirusScanResponseDto.class));
  }

  @Test
  void scanFilesReturnsNullWhenScanResponseBodyIsNull() {
    when(virusScanConfig.isScanDisabled()).thenReturn(false);
    when(virusScanConfig.getBaseUrl()).thenReturn("http://virus-scan-service");
    when(virusScanConfig.getScanFileUri()).thenReturn("/api/v1/scan");
    when(virusScanConfig.getApiKey()).thenReturn("test");
    when(virusScanRestTemplate.exchange(any(), eq(VirusScanResponseDto.class)))
        .thenReturn(ResponseEntity.ok(null));

    VirusScanResponseDto result = virusScanClient.scanFiles(List.of(fileResource1));

    assertThat(result, is(equalTo(null)));
  }

  @Test
  void scanFilesHandlesSingleFileCorrectly() {
    VirusScanResponseDto expectedResponse =
        VirusScanResponseDto.builder().filesScanned(1).cleanFileCount(1).build();

    when(virusScanConfig.isScanDisabled()).thenReturn(false);
    when(virusScanConfig.getBaseUrl()).thenReturn("http://virus-scan-service");
    when(virusScanConfig.getScanFileUri()).thenReturn("/api/v1/scan");
    when(virusScanConfig.getApiKey()).thenReturn("test");
    when(virusScanRestTemplate.exchange(any(), eq(VirusScanResponseDto.class)))
        .thenReturn(ResponseEntity.ok(expectedResponse));

    VirusScanResponseDto result = virusScanClient.scanFiles(List.of(fileResource1));

    assertNotNull(result);
    assertThat(result.getFilesScanned(), is(equalTo(1)));
    assertThat(result.getCleanFileCount(), is(equalTo(1)));
    verify(virusScanRestTemplate).exchange(any(), eq(VirusScanResponseDto.class));
  }

  @Test
  void scanFilesReturnsCleanResultForEmptyListWhenScanningIsDisabled() {
    when(virusScanConfig.isScanDisabled()).thenReturn(true);

    VirusScanResponseDto result = virusScanClient.scanFiles(List.of());

    assertNotNull(result);
    assertThat(result.getFilesScanned(), is(equalTo(0)));
    assertThat(result.getCleanFileCount(), is(equalTo(0)));
    verify(virusScanRestTemplate, never()).exchange(any(), eq(VirusScanResponseDto.class));
  }
}

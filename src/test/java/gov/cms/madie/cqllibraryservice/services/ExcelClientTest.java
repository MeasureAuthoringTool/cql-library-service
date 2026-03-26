package gov.cms.madie.cqllibraryservice.services;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import gov.cms.madie.cqllibraryservice.config.ExcelConfig;
import gov.cms.madie.cqllibraryservice.dto.LibraryAccessReportDTO;
import gov.cms.madie.cqllibraryservice.exceptions.InternalServerErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Collections;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class ExcelClientTest {

  @Mock private RestTemplate excelRestTemplate;
  @Mock private ExcelConfig excelConfig;

  @InjectMocks private ExcelClient excelClient;

  @BeforeEach
  void beforeEach() {
    when(excelConfig.getExcelExportServiceBaseUrl()).thenReturn("http://excel-service");
    when(excelConfig.getLibrarySharedAccessReportApiPath())
        .thenReturn("/api/library/access-report");
  }

  @Test
  void getSharedAccessReportForLibrariesReturnsExcelBytes() {
    byte[] expectedBytes = "excel content".getBytes();
    List<LibraryAccessReportDTO> libraries =
        List.of(
            LibraryAccessReportDTO.builder().id("lib1").libraryName("Library 1").build(),
            LibraryAccessReportDTO.builder().id("lib2").libraryName("Library 2").build());

    when(excelRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(byte[].class)))
        .thenReturn(ResponseEntity.ok(expectedBytes));

    byte[] result = excelClient.getSharedAccessReportForLibraries(libraries, "Bearer token123");

    assertThat(result, is(equalTo(expectedBytes)));
  }

  @Test
  void getSharedAccessReportForLibrariesHandlesEmptyList() {
    byte[] expectedBytes = "empty report".getBytes();

    when(excelRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(byte[].class)))
        .thenReturn(ResponseEntity.ok(expectedBytes));

    byte[] result =
        excelClient.getSharedAccessReportForLibraries(Collections.emptyList(), "Bearer token123");

    assertThat(result, is(equalTo(expectedBytes)));
  }

  @Test
  void getSharedAccessReportForLibrariesThrowsExceptionOnRestClientError() {
    List<LibraryAccessReportDTO> libraries =
        List.of(LibraryAccessReportDTO.builder().id("lib1").libraryName("Library 1").build());

    when(excelRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(byte[].class)))
        .thenThrow(new RestClientException("Connection refused"));

    InternalServerErrorException exception =
        assertThrows(
            InternalServerErrorException.class,
            () -> excelClient.getSharedAccessReportForLibraries(libraries, "Bearer token123"));

    assertThat(
        exception.getMessage(),
        is(equalTo("An error occurred while generating library access report.")));
  }

  @Test
  void getSharedAccessReportForLibrariesConstructsCorrectUri() {
    byte[] expectedBytes = "excel content".getBytes();
    List<LibraryAccessReportDTO> libraries =
        List.of(LibraryAccessReportDTO.builder().id("lib1").build());

    when(excelRestTemplate.exchange(
            eq(URI.create("http://excel-service/api/library/access-report")),
            eq(HttpMethod.PUT),
            any(HttpEntity.class),
            eq(byte[].class)))
        .thenReturn(ResponseEntity.ok(expectedBytes));

    byte[] result = excelClient.getSharedAccessReportForLibraries(libraries, "Bearer token123");

    assertThat(result, is(equalTo(expectedBytes)));
  }

  @Test
  void getSharedAccessReportForLibrariesReturnsNullWhenResponseBodyIsNull() {
    List<LibraryAccessReportDTO> libraries =
        List.of(LibraryAccessReportDTO.builder().id("lib1").build());

    when(excelRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(byte[].class)))
        .thenReturn(ResponseEntity.ok(null));

    byte[] result = excelClient.getSharedAccessReportForLibraries(libraries, "Bearer token123");

    assertNull(result);
  }

  @Test
  void getSharedAccessReportForLibrariesHandlesSingleLibrary() {
    byte[] expectedBytes = "single library report".getBytes();
    LibraryAccessReportDTO library =
        LibraryAccessReportDTO.builder()
            .id("lib1")
            .libraryName("Test Library")
            .libraryModel("QI-Core v4.1.1")
            .owner("testOwner")
            .sharedWith(
                List.of(
                    LibraryAccessReportDTO.SharedWithUser.builder()
                        .userId("user1")
                        .dateShared("2025-06-15")
                        .build()))
            .build();

    when(excelRestTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(byte[].class)))
        .thenReturn(ResponseEntity.ok(expectedBytes));

    byte[] result = excelClient.getSharedAccessReportForLibraries(List.of(library), "Bearer token");

    assertThat(result, is(equalTo(expectedBytes)));
  }
}

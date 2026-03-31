package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.config.ExcelConfig;
import gov.cms.madie.cqllibraryservice.dto.LibraryAccessReportDTO;
import gov.cms.madie.cqllibraryservice.exceptions.InternalServerErrorException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Service
@AllArgsConstructor
public class ExcelClient {

  private final RestTemplate excelRestTemplate;
  private final ExcelConfig excelConfig;

  public byte[] getSharedAccessReportForLibraries(
      List<LibraryAccessReportDTO> libraryAccessReportDTOS, String accessToken) {
    URI uri =
        URI.create(
            excelConfig.getExcelExportServiceBaseUrl()
                + excelConfig.getLibrarySharedAccessReportApiPath());

    HttpEntity<List<LibraryAccessReportDTO>> entity =
        new HttpEntity<>(libraryAccessReportDTOS, buildHeaders(accessToken));

    return executeRequest(
        uri,
        entity,
        () ->
            "Failed to export access report for libraries "
                + libraryAccessReportDTOS.stream()
                    .map(LibraryAccessReportDTO::getId)
                    .collect(java.util.stream.Collectors.joining(", ")),
        "An error occurred while generating library access report.");
  }

  private HttpHeaders buildHeaders(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, accessToken);
    headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
    return headers;
  }

  private byte[] executeRequest(
      URI uri, HttpEntity<?> entity, Supplier<String> logMessageSupplier, String errorMessage) {
    try {
      ResponseEntity<byte[]> response =
          excelRestTemplate.exchange(uri, HttpMethod.PUT, entity, byte[].class);
      return response.getBody();
    } catch (RestClientException ex) {
      log.error("{}: {}", logMessageSupplier.get(), ex.getMessage(), ex);
      throw new InternalServerErrorException(errorMessage);
    }
  }
}

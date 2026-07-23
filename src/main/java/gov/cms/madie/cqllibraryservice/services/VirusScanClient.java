package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.config.VirusScanConfig;
import gov.cms.madie.models.scanner.VirusScanResponseDto;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class VirusScanClient {
  private VirusScanConfig virusScanConfig;
  private RestTemplate virusScanRestTemplate;

  /**
   * Scans multiple files in a single multipart request. Each resource is appended under the {@code
   * "files"} key, mirroring the multi-file API accepted by the virus scan service.
   *
   * @param fileResources the list of file resources to scan
   * @return the aggregated scan result
   */
  public VirusScanResponseDto scanFiles(List<Resource> fileResources) {
    if (virusScanConfig.isScanDisabled()) {
      log.info("Virus scanning is disabled.");
      int count = fileResources.size();
      return VirusScanResponseDto.builder().filesScanned(count).cleanFileCount(count).build();
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    headers.set("apikey", virusScanConfig.getApiKey());

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    for (Resource resource : fileResources) {
      body.add("files", resource);
    }

    final String virusScanUrl = virusScanConfig.getBaseUrl() + virusScanConfig.getScanFileUri();
    final URI uri = URI.create(virusScanUrl);
    log.info("Starting virus scan for {} file(s).", fileResources.size());
    long startTime = System.currentTimeMillis();
    ResponseEntity<VirusScanResponseDto> response =
        virusScanRestTemplate.exchange(
            new RequestEntity<>(body, headers, HttpMethod.POST, uri), VirusScanResponseDto.class);
    double elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0;
    log.info("Virus scan completed in {} seconds.", elapsedTime);
    return response.getBody();
  }
}

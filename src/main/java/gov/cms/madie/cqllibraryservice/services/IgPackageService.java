package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.DownloadedPackageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IgPackageService {

  private final FhirPackageDownloadService fhirPackageDownloadService;

  /**
   * Initiates the IG package installation workflow. Downloads the FHIR NPM package from the
   * registry and tracks the download status.
   *
   * @param packageId the identifier of the IG package to install
   * @param packageVersion the version of the IG package to install
   * @param username the admin user initiating the installation
   */
  @Async
  public void installIgPackage(String packageId, String packageVersion, String username) {
    log.info(
        "User [{}] initiated IG package installation for packageId [{}], version [{}]",
        username,
        packageId,
        packageVersion);

    long startTime = System.currentTimeMillis();
    DownloadedPackageResult result =
        fhirPackageDownloadService.downloadPackage(packageId, packageVersion, username);
    double elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0;

    if (result.isSuccess()) {
      log.info(
          "IG package [{}] version [{}] downloaded successfully in [{}] seconds",
          packageId,
          packageVersion,
          elapsedSeconds);
    } else {
      log.warn(
          "IG package [{}] version [{}] download failed after [{}] seconds: {}",
          packageId,
          packageVersion,
          elapsedSeconds,
          result.getErrorMessage());
    }
  }
}

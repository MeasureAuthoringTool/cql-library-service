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
  private final ExternalLibraryImportService externalLibraryImportService;

  /**
   * Initiates the IG package installation workflow. Downloads the FHIR NPM package from the
   * registry, tracks the download status, and – on success – automatically triggers an async import
   * of any Common CQL Libraries found in the package and its dependencies.
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

    DownloadedPackageResult result =
        fhirPackageDownloadService.downloadPackage(packageId, packageVersion, username);

    if (result.isSuccess()) {
      // kick off external CQL Library import now that the package is in the cache.
      externalLibraryImportService.importLibraries(packageId, packageVersion);
    } else {
      log.warn(
          "IG package [{}] version [{}] download failed: {}",
          packageId,
          packageVersion,
          result.getErrorMessage());
    }
  }
}

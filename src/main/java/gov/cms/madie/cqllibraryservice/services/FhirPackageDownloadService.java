package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.DownloadedPackageResult;

public interface FhirPackageDownloadService {
  /**
   * Downloads a FHIR NPM package from the package registry, use cache if available.
   *
   * @param packageId the FHIR package identifier (e.g., "hl7.fhir.us.qicore")
   * @param version the package version (e.g., "7.0.2")
   * @param username the admin user initiating the download
   * @return a result object describing the outcome of the download
   */
  DownloadedPackageResult downloadPackage(String packageId, String version, String username);
}

package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.DownloadedPackageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IgPackageServiceTest {

  @InjectMocks private IgPackageService igPackageService;
  @Mock private FhirPackageDownloadService fhirPackageDownloadService;

  @Test
  void testInstallIgPackageSuccess() {
    DownloadedPackageResult expectedResult =
        DownloadedPackageResult.builder()
            .packageId("hl7.fhir.us.qicore")
            .version("7.0.2")
            .success(true)
            .packageLocation("/cache/hl7.fhir.us.qicore#7.0.2")
            .build();
    when(fhirPackageDownloadService.downloadPackage(anyString(), anyString(), anyString()))
        .thenReturn(expectedResult);

    igPackageService.installIgPackage("hl7.fhir.us.qicore", "7.0.2", "admin.user");

    verify(fhirPackageDownloadService, times(1))
        .downloadPackage("hl7.fhir.us.qicore", "7.0.2", "admin.user");
  }

  @Test
  void testInstallIgPackageFailure() {
    DownloadedPackageResult failedResult =
        DownloadedPackageResult.builder()
            .packageId("some.invalid.package")
            .version("1.0.0")
            .success(false)
            .errorMessage("Package not found")
            .build();
    when(fhirPackageDownloadService.downloadPackage(anyString(), anyString(), anyString()))
        .thenReturn(failedResult);

    igPackageService.installIgPackage("some.invalid.package", "1.0.0", "admin.user");

    verify(fhirPackageDownloadService, times(1))
        .downloadPackage("some.invalid.package", "1.0.0", "admin.user");
  }
}

package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.IgPackageInstallRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@ExtendWith(MockitoExtension.class)
class IgPackageServiceTest {

  @InjectMocks private IgPackageService igPackageService;

  @Test
  void testInstallIgPackage() {
    IgPackageInstallRequest request =
        IgPackageInstallRequest.builder()
            .packageId("hl7.fhir.us.qicore")
            .packageVersion("7.0.2")
            .build();

    assertDoesNotThrow(() -> igPackageService.installIgPackage(request, "admin.user"));
  }
}

package gov.cms.madie.cqllibraryservice.services;

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
    assertDoesNotThrow(
        () -> igPackageService.installIgPackage("hl7.fhir.us.qicore", "7.0.2", "admin.user"));
  }
}

package gov.cms.madie.cqllibraryservice.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IgPackageInstallRequestTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }
  }

  @Test
  void testValidRequest() {
    IgPackageInstallRequest request =
        IgPackageInstallRequest.builder()
            .packageId("hl7.fhir.us.qicore")
            .packageVersion("7.0.2")
            .build();
    Set<ConstraintViolation<IgPackageInstallRequest>> violations = validator.validate(request);
    assertTrue(violations.isEmpty());
  }

  @Test
  void testMissingPackageId() {
    IgPackageInstallRequest request =
        IgPackageInstallRequest.builder().packageVersion("7.0.2").build();
    Set<ConstraintViolation<IgPackageInstallRequest>> violations = validator.validate(request);
    assertEquals(1, violations.size());
    assertEquals("Package ID is required.", violations.iterator().next().getMessage());
  }

  @Test
  void testMissingPackageVersion() {
    IgPackageInstallRequest request =
        IgPackageInstallRequest.builder().packageId("hl7.fhir.us.qicore").build();
    Set<ConstraintViolation<IgPackageInstallRequest>> violations = validator.validate(request);
    assertEquals(1, violations.size());
    assertEquals("Package Version is required.", violations.iterator().next().getMessage());
  }

  @Test
  void testBlankPackageIdAndVersion() {
    IgPackageInstallRequest request =
        IgPackageInstallRequest.builder().packageId("").packageVersion("").build();
    Set<ConstraintViolation<IgPackageInstallRequest>> violations = validator.validate(request);
    assertEquals(2, violations.size());
  }

  @Test
  void testEmptyRequest() {
    IgPackageInstallRequest request = new IgPackageInstallRequest();
    Set<ConstraintViolation<IgPackageInstallRequest>> violations = validator.validate(request);
    assertEquals(2, violations.size());
  }
}

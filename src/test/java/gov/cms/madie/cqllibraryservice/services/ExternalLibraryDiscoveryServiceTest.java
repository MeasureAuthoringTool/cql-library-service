package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.models.ExternalLibrary;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalLibraryDiscoveryServiceTest {

  @Mock private NpmPackage npmPackage;
  @Mock private JsonObject npm;
  @InjectMocks private ExternalLibraryDiscoveryService discoveryService;

  private static final String NAMESPACE_CANONICAL = "http://hl7.org/fhir/us/qicore";
  private static final String NAMESPACE_PREFIX = "hl7.fhir.us.qicore";
  private static final String PACKAGE_ID = "hl7.fhir.us.qicore";
  private static final String PACKAGE_VERSION = "6.0.0";

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static String buildLibraryJson(
      String resourceType, String typeCode, boolean includeCql, String name, String version) {
    String encodedCql =
        Base64.getEncoder().encodeToString("library FHIRHelpers version '4.3.000'".getBytes());
    String contentBlock =
        includeCql
            ?
                """
              [{"contentType":"text/cql","data":"%s"}]
              """
                .formatted(encodedCql)
            :
            """
              [{"contentType":"application/elm+json"}]
              """;
    return
        """
        {
          "resourceType": "%s",
          "id": "%s",
          "name": "%s",
          "version": "%s",
          "url": "http://example.org/%s",
          "type": {"coding": [{"code": "%s"}]},
          "content": %s
        }
        """
        .formatted(resourceType, name, name, version, name, typeCode, contentBlock);
  }

  /** Valid logic-library JSON but with the {@code name} field omitted. */
  private static String buildLibraryJsonNoName() {
    String encodedCql =
        Base64.getEncoder().encodeToString("library Test version '1.0.0'".getBytes());
    return
        """
        {
          "resourceType": "Library",
          "version": "1.0.0",
          "type": {"coding": [{"code": "logic-library"}]},
          "content": [{"contentType":"text/cql","data":"%s"}]
        }
        """
        .formatted(encodedCql);
  }

  /** Valid logic-library JSON but with the {@code version} field omitted. */
  private static String buildLibraryJsonNoVersion() {
    String encodedCql =
        Base64.getEncoder().encodeToString("library Test version '1.0.0'".getBytes());
    return
        """
        {
          "resourceType": "Library",
          "name": "TestLib",
          "type": {"coding": [{"code": "logic-library"}]},
          "content": [{"contentType":"text/cql","data":"%s"}]
        }
        """
        .formatted(encodedCql);
  }

  /** Logic-library JSON whose {@code content[0].data} is not valid base64. */
  private static String buildLibraryJsonInvalidBase64() {
    return
    """
        {
          "resourceType": "Library",
          "name": "BadBase64Lib",
          "version": "1.0.0",
          "type": {"coding": [{"code": "logic-library"}]},
          "content": [{"contentType":"text/cql","data":"!!!not-valid-base64!!!"}]
        }
        """;
  }

  /**
   * Logic-library JSON where {@code contentType} is {@code text/cql} but the {@code data} field is
   * absent.
   */
  private static String buildLibraryJsonCqlNoData() {
    return
    """
        {
          "resourceType": "Library",
          "name": "NoCqlDataLib",
          "version": "1.0.0",
          "type": {"coding": [{"code": "logic-library"}]},
          "content": [{"contentType":"text/cql"}]
        }
        """;
  }

  /** Logic-library JSON with the entire {@code content} array absent. */
  private static String buildLibraryJsonNoContentArray() {
    return
    """
        {
          "resourceType": "Library",
          "name": "NoContentLib",
          "version": "1.0.0",
          "type": {"coding": [{"code": "logic-library"}]}
        }
        """;
  }

  /** Logic-library JSON where {@code type.coding} is a string rather than an array. */
  private static String buildLibraryJsonCodingNotArray() {
    String encodedCql =
        Base64.getEncoder().encodeToString("library Test version '1.0.0'".getBytes());
    return
        """
        {
          "resourceType": "Library",
          "name": "BadCodingLib",
          "version": "1.0.0",
          "type": {"coding": "not-an-array"},
          "content": [{"contentType":"text/cql","data":"%s"}]
        }
        """
        .formatted(encodedCql);
  }

  private void mockNpmPackage(List<String> filenames) throws IOException {
    when(npmPackage.getNpm()).thenReturn(npm);
    when(npm.asString("canonical")).thenReturn(NAMESPACE_CANONICAL);
    when(npm.asString("name")).thenReturn(NAMESPACE_PREFIX);
    // Use doReturn to avoid varargs/Set overload ambiguity during test compilation.
    doReturn(filenames).when(npmPackage).listResources(new String[] {"Library"});
  }

  // ---------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------

  @Test
  void discoverLibrariesValidLogicLibraryIsDiscovered() throws IOException {
    mockNpmPackage(List.of("FHIRHelpers.json"));
    String json = buildLibraryJson("Library", "logic-library", true, "FHIRHelpers", "4.3.000");
    when(npmPackage.load("package", "FHIRHelpers.json"))
        .thenReturn(new ByteArrayInputStream(json.getBytes()));

    List<ExternalLibrary> result =
        discoveryService.discoverLibraries(npmPackage, PACKAGE_ID, PACKAGE_VERSION);

    assertThat(result).hasSize(1);
    ExternalLibrary lib = result.get(0);
    assertThat(lib.getLibraryName()).isEqualTo("FHIRHelpers");
    assertThat(lib.getVersion()).isEqualTo("4.3.000");
    assertThat(lib.getCanonical()).isEqualTo(NAMESPACE_CANONICAL);
    assertThat(lib.getNamespacePrefix()).isEqualTo(NAMESPACE_PREFIX);
    assertThat(lib.getCqlContent()).contains("library FHIRHelpers");
    assertThat(lib.isDraft()).isFalse();
    assertThat(lib.getDateImported()).isNotNull();
  }

  @Test
  void discoverLibrariesNonLibraryResourceTypeIsIgnored() throws IOException {
    mockNpmPackage(List.of("ValueSet-example.json"));
    String json = buildLibraryJson("ValueSet", "logic-library", true, "SomeVS", "1.0.0");
    when(npmPackage.load("package", "ValueSet-example.json"))
        .thenReturn(new ByteArrayInputStream(json.getBytes()));

    List<ExternalLibrary> result =
        discoveryService.discoverLibraries(npmPackage, PACKAGE_ID, PACKAGE_VERSION);

    assertThat(result).isEmpty();
  }

  @Test
  void discoverLibrariesNonLogicLibraryTypeCodeIsIgnored() throws IOException {
    mockNpmPackage(List.of("ModelInfo.json"));
    String json = buildLibraryJson("Library", "model-definition", true, "QICore", "4.1.1");
    when(npmPackage.load("package", "ModelInfo.json"))
        .thenReturn(new ByteArrayInputStream(json.getBytes()));

    List<ExternalLibrary> result =
        discoveryService.discoverLibraries(npmPackage, PACKAGE_ID, PACKAGE_VERSION);

    assertThat(result).isEmpty();
  }

  @Test
  void discoverLibrariesNoCqlContentIsIgnored() throws IOException {
    mockNpmPackage(List.of("NoContent.json"));
    String json = buildLibraryJson("Library", "logic-library", false, "NoContent", "1.0.0");
    when(npmPackage.load("package", "NoContent.json"))
        .thenReturn(new ByteArrayInputStream(json.getBytes()));

    List<ExternalLibrary> result =
        discoveryService.discoverLibraries(npmPackage, PACKAGE_ID, PACKAGE_VERSION);

    assertThat(result).isEmpty();
  }

  @Test
  void discoverLibrariesMultipleLibrariesOnlyValidOnesDiscovered() throws IOException {
    mockNpmPackage(List.of("Valid.json", "Invalid.json"));
    String validJson = buildLibraryJson("Library", "logic-library", true, "ValidLib", "1.0.0");
    String invalidJson =
        buildLibraryJson("Library", "model-definition", true, "InvalidLib", "1.0.0");
    when(npmPackage.load("package", "Valid.json"))
        .thenReturn(new ByteArrayInputStream(validJson.getBytes()));
    when(npmPackage.load("package", "Invalid.json"))
        .thenReturn(new ByteArrayInputStream(invalidJson.getBytes()));

    List<ExternalLibrary> result =
        discoveryService.discoverLibraries(npmPackage, PACKAGE_ID, PACKAGE_VERSION);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getLibraryName()).isEqualTo("ValidLib");
  }

  @Test
  void discoverLibrariesMissingPackageJsonReturnsEmpty() throws IOException {
    when(npmPackage.getNpm()).thenReturn(null);

    List<ExternalLibrary> result =
        discoveryService.discoverLibraries(npmPackage, PACKAGE_ID, PACKAGE_VERSION);

    assertThat(result).isEmpty();
    verify(npmPackage, never()).listResources(new String[] {"Library"});
  }

  @Test
  void discoverLibrariesMissingCanonicalReturnsEmpty() throws IOException {
    when(npmPackage.getNpm()).thenReturn(npm);
    when(npm.asString("canonical")).thenReturn(null);

    List<ExternalLibrary> result =
        discoveryService.discoverLibraries(npmPackage, PACKAGE_ID, PACKAGE_VERSION);

    assertThat(result).isEmpty();
  }

  @Test
  void discoverLibrariesMalformedJsonSkipsBadFileAndContinues() throws IOException {
    mockNpmPackage(List.of("Bad.json", "Good.json"));
    String goodJson = buildLibraryJson("Library", "logic-library", true, "GoodLib", "2.0.0");
    when(npmPackage.load("package", "Bad.json"))
        .thenReturn(new ByteArrayInputStream("not-valid-json{{{".getBytes()));
    when(npmPackage.load("package", "Good.json"))
        .thenReturn(new ByteArrayInputStream(goodJson.getBytes()));

    List<ExternalLibrary> result =
        discoveryService.discoverLibraries(npmPackage, PACKAGE_ID, PACKAGE_VERSION);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getLibraryName()).isEqualTo("GoodLib");
  }

  @Test
  void discoverLibrariesNamespaceCanonicalAndPrefixExtractedFromPackageJson() throws IOException {
    mockNpmPackage(List.of("FHIRHelpers.json"));
    String json = buildLibraryJson("Library", "logic-library", true, "FHIRHelpers", "4.3.000");
    when(npmPackage.load("package", "FHIRHelpers.json"))
        .thenReturn(new ByteArrayInputStream(json.getBytes()));

    List<ExternalLibrary> result =
        discoveryService.discoverLibraries(npmPackage, PACKAGE_ID, PACKAGE_VERSION);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCanonical()).isEqualTo(NAMESPACE_CANONICAL);
    assertThat(result.get(0).getNamespacePrefix()).isEqualTo(NAMESPACE_PREFIX);
  }

  @Test
  void discoverLibrariesFromPathReturnsEmptyOnIOException() {
    // A non-existent filesystem path causes NpmPackage.fromFolder to throw IOException.
    List<ExternalLibrary> result =
        discoveryService.discoverLibrariesForPackage(
            "/nonexistent/path/does-not-exist", PACKAGE_ID, PACKAGE_VERSION);

    assertThat(result).isEmpty();
  }

  @Test
  void discoverLibrariesFromPathDelegatesToNpmPackageOverload() throws IOException {
    String validJson = buildLibraryJson("Library", "logic-library", true, "TestLib", "1.0.0");

    try (MockedStatic<NpmPackage> mockedStatic = mockStatic(NpmPackage.class)) {
      NpmPackage mockNpm = mock(NpmPackage.class);
      JsonObject mockNpmJson = mock(JsonObject.class);
      when(mockNpm.getNpm()).thenReturn(mockNpmJson);
      when(mockNpmJson.asString("canonical")).thenReturn(NAMESPACE_CANONICAL);
      when(mockNpmJson.asString("name")).thenReturn(NAMESPACE_PREFIX);
      doReturn(List.of("TestLib.json")).when(mockNpm).listResources(new String[] {"Library"});
      when(mockNpm.load("package", "TestLib.json"))
          .thenReturn(new ByteArrayInputStream(validJson.getBytes()));
      mockedStatic.when(() -> NpmPackage.fromFolder("/some/valid/path")).thenReturn(mockNpm);

      List<ExternalLibrary> result =
          discoveryService.discoverLibrariesForPackage(
              "/some/valid/path", PACKAGE_ID, PACKAGE_VERSION);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getLibraryName()).isEqualTo("TestLib");
    }
  }

  @Test
  void discoverLibrariesMissingNamespacePrefixContinuesDiscovery() throws IOException {
    when(npmPackage.getNpm()).thenReturn(npm);
    when(npm.asString("canonical")).thenReturn(NAMESPACE_CANONICAL);
    when(npm.asString("name")).thenReturn(null); // blank 'name' – warns but does NOT return early
    doReturn(List.of("FHIRHelpers.json")).when(npmPackage).listResources(new String[] {"Library"});
    String json = buildLibraryJson("Library", "logic-library", true, "FHIRHelpers", "4.3.000");
    when(npmPackage.load("package", "FHIRHelpers.json"))
        .thenReturn(new ByteArrayInputStream(json.getBytes()));

    List<ExternalLibrary> result =
        discoveryService.discoverLibraries(npmPackage, PACKAGE_ID, PACKAGE_VERSION);

    // Discovery still proceeds; library is returned with a null namespacePrefix
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getNamespacePrefix()).isNull();
  }

  @Test
  void discoverLibrariesListResourcesIOExceptionReturnsEmpty() throws IOException {
    when(npmPackage.getNpm()).thenReturn(npm);
    when(npm.asString("canonical")).thenReturn(NAMESPACE_CANONICAL);
    when(npm.asString("name")).thenReturn(NAMESPACE_PREFIX);
    doThrow(new IOException("disk error")).when(npmPackage).listResources(new String[] {"Library"});

    List<ExternalLibrary> result =
        discoveryService.discoverLibraries(npmPackage, PACKAGE_ID, PACKAGE_VERSION);

    assertThat(result).isEmpty();
  }

  @Test
  void discoverLibrariesMissingLibraryNameIsSkipped() throws IOException {
    mockNpmPackage(List.of("NoName.json"));
    when(npmPackage.load("package", "NoName.json"))
        .thenReturn(new ByteArrayInputStream(buildLibraryJsonNoName().getBytes()));

    List<ExternalLibrary> result =
        discoveryService.discoverLibraries(npmPackage, PACKAGE_ID, PACKAGE_VERSION);

    assertThat(result).isEmpty();
  }

  @Test
  void discoverLibrariesMissingLibraryVersionIsSkipped() throws IOException {
    mockNpmPackage(List.of("NoVersion.json"));
    when(npmPackage.load("package", "NoVersion.json"))
        .thenReturn(new ByteArrayInputStream(buildLibraryJsonNoVersion().getBytes()));

    List<ExternalLibrary> result =
        discoveryService.discoverLibraries(npmPackage, PACKAGE_ID, PACKAGE_VERSION);

    assertThat(result).isEmpty();
  }

  @Test
  void discoverLibrariesInvalidBase64CqlIsSkipped() throws IOException {
    mockNpmPackage(List.of("InvalidBase64.json"));
    when(npmPackage.load("package", "InvalidBase64.json"))
        .thenReturn(new ByteArrayInputStream(buildLibraryJsonInvalidBase64().getBytes()));

    List<ExternalLibrary> result =
        discoveryService.discoverLibraries(npmPackage, PACKAGE_ID, PACKAGE_VERSION);

    assertThat(result).isEmpty();
  }

  @Test
  void discoverLibrariesCqlContentTypeButNoDataFieldIsSkipped() throws IOException {
    mockNpmPackage(List.of("NoData.json"));
    when(npmPackage.load("package", "NoData.json"))
        .thenReturn(new ByteArrayInputStream(buildLibraryJsonCqlNoData().getBytes()));

    List<ExternalLibrary> result =
        discoveryService.discoverLibraries(npmPackage, PACKAGE_ID, PACKAGE_VERSION);

    assertThat(result).isEmpty();
  }

  @Test
  void discoverLibrariesNoContentArrayIsSkipped() throws IOException {
    mockNpmPackage(List.of("NoContent.json"));
    when(npmPackage.load("package", "NoContent.json"))
        .thenReturn(new ByteArrayInputStream(buildLibraryJsonNoContentArray().getBytes()));

    List<ExternalLibrary> result =
        discoveryService.discoverLibraries(npmPackage, PACKAGE_ID, PACKAGE_VERSION);

    assertThat(result).isEmpty();
  }

  @Test
  void discoverLibrariesTypeCodingNotAnArrayIsIgnored() throws IOException {
    mockNpmPackage(List.of("BadCoding.json"));
    when(npmPackage.load("package", "BadCoding.json"))
        .thenReturn(new ByteArrayInputStream(buildLibraryJsonCodingNotArray().getBytes()));

    List<ExternalLibrary> result =
        discoveryService.discoverLibraries(npmPackage, PACKAGE_ID, PACKAGE_VERSION);

    assertThat(result).isEmpty();
  }

  @Test
  void discoverLibrariesFhirResourceHasContentArrayStripped() throws IOException {
    mockNpmPackage(List.of("FHIRHelpers.json"));
    String json = buildLibraryJson("Library", "logic-library", true, "FHIRHelpers", "4.3.000");
    when(npmPackage.load("package", "FHIRHelpers.json"))
        .thenReturn(new ByteArrayInputStream(json.getBytes()));

    List<ExternalLibrary> result =
        discoveryService.discoverLibraries(npmPackage, PACKAGE_ID, PACKAGE_VERSION);

    assertThat(result).hasSize(1);
    String fhirResource = result.get(0).getFhirResource();
    assertThat(fhirResource).isNotBlank();
    assertThat(fhirResource).doesNotContain("\"content\"");
    // CQL is still available in the dedicated field
    assertThat(result.get(0).getCqlContent()).contains("library FHIRHelpers");
  }
}

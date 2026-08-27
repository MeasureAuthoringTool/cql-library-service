package gov.cms.madie.cqllibraryservice.services;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.dto.LibrarySearchCriteria;
import gov.cms.madie.cqllibraryservice.dto.LibrarySetDTO;
import gov.cms.madie.cqllibraryservice.dto.LockInfo;
import gov.cms.madie.cqllibraryservice.dto.SharedUser;
import gov.cms.madie.cqllibraryservice.exceptions.*;
import gov.cms.madie.cqllibraryservice.locks.CqlLibraryLock;
import gov.cms.madie.cqllibraryservice.models.ExternalLibrary;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryReviewRepository;
import gov.cms.madie.cqllibraryservice.repositories.ExternalLibraryRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.common.*;
import gov.cms.madie.models.dto.CqlLibraryDto;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.CqlLibraryReview;
import gov.cms.madie.models.library.LibrarySet;
import gov.cms.madie.models.measure.ElmJson;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CqlLibraryServiceTest {

  @Spy @InjectMocks private CqlLibraryService cqlLibraryService;
  @Mock private LibrarySharingService librarySharingService;
  @Mock private CqlLibraryRepository cqlLibraryRepository;
  @Mock private LibrarySetService librarySetService;
  @Mock private MeasureServiceClient measureServiceClient;
  @Mock private LibrarySetRepository librarySetRepository;
  @Mock private ElmTranslatorClient elmTranslatorClient;
  @Mock private ActionLogService actionLogService;
  @Mock private AppConfigService appConfigService;
  @Mock private CqlLibraryLockService cqlLibraryLockService;

  @Mock private UserServiceClient userServiceClient;
  @Mock private CqlLibraryAccessControlService cqlLibraryAccessControlService;
  @Mock private CqlLibraryReviewRepository cqlLibraryReviewRepository;
  @Mock private ExternalLibraryRepository externalLibraryRepository;

  private final String USERNAME = "testUserName";
  private final String ACCESSTOKEN = "accessToken";

  @Test
  public void testGetOwnedLibrariesByCriteria() {
    var librarySearchCriteria =
        LibrarySearchCriteria.builder().searchField("measureSearchCriteria").build();
    PageRequest initialPage = PageRequest.of(0, 10);
    LibraryListDTO lib1 = LibraryListDTO.builder().build();

    Page<LibraryListDTO> activeLibraries = new PageImpl<>(List.of(lib1));
    doReturn(activeLibraries)
        .when(cqlLibraryRepository)
        .searchLibrariesByCriteria(
            eq("test.user"), any(PageRequest.class), any(), eq(OwnershipType.OWNED));
    Page<LibraryListDTO> libraries =
        cqlLibraryService.getLibrariesByCriteria(
            librarySearchCriteria, OwnershipType.OWNED, initialPage, "test.user");
    assertNotNull(libraries);
  }

  @Test
  public void testCheckDuplicateCqlLibraryNameDoesNotThrowException() {
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(false);
    cqlLibraryService.checkDuplicateCqlLibraryName("Lib1");
    verify(cqlLibraryRepository, times(1)).existsByCqlLibraryName(eq("Lib1"));
  }

  @Test
  public void testCheckDuplicateCqlLibraryNameThrowsExceptionForExistingName() {
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(true);
    assertThrows(
        DuplicateKeyException.class, () -> cqlLibraryService.checkDuplicateCqlLibraryName("Lib1"));
  }

  @Test
  public void testIsCqlLibraryNameChangedReturnsFalseForSame() {
    CqlLibrary lib1 = CqlLibrary.builder().cqlLibraryName("Lib1").build();
    CqlLibrary lib2 = CqlLibrary.builder().cqlLibraryName("Lib1").build();
    boolean output = cqlLibraryService.isCqlLibraryNameChanged(lib1, lib2);
    assertThat(output, is(false));
  }

  @Test
  public void testIsCqlLibraryNameChangedReturnsFalseForNulls() {
    CqlLibrary lib1 = CqlLibrary.builder().build();
    CqlLibrary lib2 = CqlLibrary.builder().build();
    boolean output = cqlLibraryService.isCqlLibraryNameChanged(lib1, lib2);
    assertThat(output, is(false));
  }

  @Test
  public void testIsCqlLibraryNameChangedReturnsTrueForLib1Null() {
    CqlLibrary lib1 = CqlLibrary.builder().cqlLibraryName(null).build();
    CqlLibrary lib2 = CqlLibrary.builder().cqlLibraryName("Lib1").build();
    boolean output = cqlLibraryService.isCqlLibraryNameChanged(lib1, lib2);
    assertThat(output, is(true));
  }

  @Test
  public void testIsCqlLibraryNameChangedReturnsTrueForLib2Null() {
    CqlLibrary lib1 = CqlLibrary.builder().cqlLibraryName("Lib1").build();
    CqlLibrary lib2 = CqlLibrary.builder().cqlLibraryName(null).build();
    boolean output = cqlLibraryService.isCqlLibraryNameChanged(lib1, lib2);
    assertThat(output, is(true));
  }

  @Test
  public void testIsCqlLibraryNameChangedReturnsTrueForDifferent() {
    CqlLibrary lib1 = CqlLibrary.builder().cqlLibraryName("Lib1").build();
    CqlLibrary lib2 = CqlLibrary.builder().cqlLibraryName("Lib2").build();
    boolean output = cqlLibraryService.isCqlLibraryNameChanged(lib1, lib2);
    assertThat(output, is(true));
  }

  @Test
  public void testGetVersionedCqlLibrary() {
    List<CqlLibrary> cqlLibraries = new ArrayList<>();
    var cqlLibrary1 =
        CqlLibrary.builder()
            .cqlLibraryName("TestFHIRHelpers")
            .version(Version.builder().major(1).minor(0).revisionNumber(0).build())
            .model("QI-Core v4.1.1")
            .draft(false)
            .cql("this is totally valid CQL here")
            .build();
    cqlLibraries.add(cqlLibrary1);
    when(cqlLibraryRepository.findAllByCqlLibraryNameAndDraftAndVersionAndModel(
            any(), anyBoolean(), any(), anyString()))
        .thenReturn(cqlLibraries);
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{\"library\": {}}").xml("<library/>").build());
    CqlLibraryDto versionedCqlLibrary =
        cqlLibraryService.getVersionedCqlLibrary(
            "TestFHIRHelpers",
            "1.0.000",
            Optional.of("QI-Core v4.1.1"),
            Optional.empty(),
            Optional.empty(),
            true,
            "Info",
            "test-okta");
    assertNotNull(versionedCqlLibrary);
    assertEquals(cqlLibrary1.getCqlLibraryName(), versionedCqlLibrary.getCqlLibraryName());
    assertEquals(cqlLibrary1.getVersion().toString(), versionedCqlLibrary.getVersion());
    assertEquals(cqlLibrary1.getModel(), versionedCqlLibrary.getModel());
    assertEquals("{\"library\": {}}", versionedCqlLibrary.getElmJson());
    assertEquals("<library/>", versionedCqlLibrary.getElmXml());
  }

  @Test
  public void testGetVersionedCqlLibraryWhenModelIsNotProvided() {
    List<CqlLibrary> cqlLibraries = new ArrayList<>();
    var cqlLibrary =
        CqlLibrary.builder()
            .cqlLibraryName("TestFHIRHelpers")
            .version(Version.builder().major(1).minor(0).revisionNumber(0).build())
            .model("QI-Core v4.1.1")
            .draft(false)
            .cql("this is totally valid CQL here")
            .build();
    cqlLibraries.add(cqlLibrary);
    when(cqlLibraryRepository.findAllByCqlLibraryNameAndDraftAndVersion(any(), anyBoolean(), any()))
        .thenReturn(cqlLibraries);
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{\"library\": {}}").build());
    CqlLibraryDto versionedCqlLibrary =
        cqlLibraryService.getVersionedCqlLibrary(
            "TestFHIRHelpers",
            "1.0.000",
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            true,
            "Info",
            "test-okta");
    assertNotNull(versionedCqlLibrary);
    assertEquals(cqlLibrary.getCqlLibraryName(), versionedCqlLibrary.getCqlLibraryName());
    assertEquals(cqlLibrary.getVersion().toString(), versionedCqlLibrary.getVersion());
    assertEquals(cqlLibrary.getModel(), versionedCqlLibrary.getModel());
  }

  @Test
  void testGetVersionedCqlLibraryByNamespaceCanonical() {
    ExternalLibrary externalLibrary =
        ExternalLibrary.builder()
            .libraryName("FHIRHelpers")
            .version("1.0.000")
            .librarySetId("external-library-set-id")
            .namespacePrefix("hl7.fhir.us.qicore")
            .cqlContent("library FHIRHelpers version '1.0.000'")
            .build();
    when(externalLibraryRepository.findByPackageCanonicalAndLibraryNameAndVersion(
            "http://hl7.org/fhir/us/qicore", "FHIRHelpers", "1.0.000"))
        .thenReturn(Optional.of(externalLibrary));

    CqlLibraryDto result =
        cqlLibraryService.getVersionedCqlLibrary(
            "FHIRHelpers",
            "1.0.000",
            Optional.empty(),
            Optional.of("http://hl7.org/fhir/us/qicore"),
            Optional.empty(),
            false,
            "Info",
            null);

    assertEquals("FHIRHelpers", result.getCqlLibraryName());
    assertEquals("library FHIRHelpers version '1.0.000'", result.getCql());
    assertTrue(result.isExternal());
    assertEquals("1.0.000", result.getVersion());
    assertEquals("external-library-set-id", result.getLibrarySetId());
    verify(elmTranslatorClient, never())
        .getElmJson(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void testGetVersionedCqlLibraryByNamespaceCanonicalNotFound() {
    when(externalLibraryRepository.findByPackageCanonicalAndLibraryNameAndVersion(
            "http://hl7.org/fhir/us/qicore", "FHIRHelpers", "1.0.000"))
        .thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            cqlLibraryService.getVersionedCqlLibrary(
                "FHIRHelpers",
                "1.0.000",
                Optional.empty(),
                Optional.of("http://hl7.org/fhir/us/qicore"),
                Optional.empty(),
                false,
                "Info",
                null));
  }

  @Test
  void testGetVersionedCqlLibraryByNamespacePrefix() {
    ExternalLibrary externalLibrary =
        ExternalLibrary.builder()
            .libraryName("FHIRHelpers")
            .version("1.0.000")
            .namespacePrefix("hl7.fhir.us.qicore")
            .cqlContent("library FHIRHelpers version '1.0.000'")
            .build();
    when(externalLibraryRepository.findByNamespacePrefixAndLibraryNameAndVersion(
            "hl7.fhir.us.qicore", "FHIRHelpers", "1.0.000"))
        .thenReturn(Optional.of(externalLibrary));

    CqlLibraryDto result =
        cqlLibraryService.getVersionedCqlLibrary(
            "FHIRHelpers",
            "1.0.000",
            Optional.empty(),
            Optional.empty(),
            Optional.of("hl7.fhir.us.qicore"),
            false,
            "Info",
            null);

    assertEquals("FHIRHelpers", result.getCqlLibraryName());
    assertEquals("library FHIRHelpers version '1.0.000'", result.getCql());
    assertTrue(result.isExternal());
    assertEquals("hl7.fhir.us.qicore", result.getNamespacePrefix());
  }

  @Test
  void testGetVersionedCqlLibraryByNamespacePrefixNotFound() {
    when(externalLibraryRepository.findByNamespacePrefixAndLibraryNameAndVersion(
            "hl7.fhir.us.qicore", "FHIRHelpers", "1.0.000"))
        .thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            cqlLibraryService.getVersionedCqlLibrary(
                "FHIRHelpers",
                "1.0.000",
                Optional.empty(),
                Optional.empty(),
                Optional.of("hl7.fhir.us.qicore"),
                false,
                "Info",
                null));
  }

  @Test
  void testGetVersionedCqlLibraryByNamespacePrefixGeneratesElmWhenRequested() {
    // given - mocks
    String namespaceCanonical = "http://hl7.org/fhir/us/qicore";
    ExternalLibrary externalLibrary =
        ExternalLibrary.builder()
            .libraryName("FHIRHelpers")
            .version("1.0.1")
            .packageCanonical(namespaceCanonical)
            .namespacePrefix("hl7.fhir.us.qicore")
            .cqlContent("library FHIRHelpers version '1.0.1'")
            .fhirResource("{\"resourceType\":\"Library\"}")
            .build();
    ElmJson elmJson = ElmJson.builder().json("ELM JSON").xml("ELM XML").build();
    when(externalLibraryRepository.findByNamespacePrefixAndLibraryNameAndVersion(
            "hl7.fhir.us.qicore", "FHIRHelpers", "1.0.1"))
        .thenReturn(Optional.of(externalLibrary));
    when(elmTranslatorClient.getElmJson(
            externalLibrary.getCqlContent(),
            ModelType.FHIR_4_0_1.getValue(),
            "test-okta",
            "Info",
            namespaceCanonical))
        .thenReturn(elmJson);
    when(elmTranslatorClient.hasErrors(elmJson)).thenReturn(false);

    // when - call method under test
    CqlLibraryDto result =
        cqlLibraryService.getVersionedCqlLibrary(
            "FHIRHelpers",
            "1.0.1",
            Optional.empty(),
            Optional.empty(),
            Optional.of("hl7.fhir.us.qicore"),
            true,
            "Info",
            "test-okta");

    // then - assertions
    assertEquals("ELM JSON", result.getElmJson());
    assertEquals("ELM XML", result.getElmXml());
    assertEquals("{\"resourceType\":\"Library\"}", result.getFhirResource());
    verify(elmTranslatorClient)
        .getElmJson(
            externalLibrary.getCqlContent(),
            ModelType.FHIR_4_0_1.getValue(),
            "test-okta",
            "Info",
            namespaceCanonical);
  }

  @Test
  void testGetVersionedCqlLibraryRejectsCanonicalAndPrefix() {
    BadRequestObjectException exception =
        assertThrows(
            BadRequestObjectException.class,
            () ->
                cqlLibraryService.getVersionedCqlLibrary(
                    "FHIRHelpers",
                    "1.0.000",
                    Optional.empty(),
                    Optional.of("http://hl7.org/fhir/us/qicore"),
                    Optional.of("hl7.fhir.us.qicore"),
                    false,
                    "Info",
                    null));

    assertEquals(
        "Only one of namespaceCanonical and namespacePrefix may be provided.",
        exception.getMessage());
    verifyNoInteractions(externalLibraryRepository, cqlLibraryRepository);
  }

  private CqlLibrary mockSingleVersionedLibraryForOwnerEnrichment(String owner) {
    CqlLibrary cqlLibrary =
        CqlLibrary.builder()
            .cqlLibraryName("TestFHIRHelpers")
            .librarySetId("libSetId")
            .version(Version.builder().major(1).minor(0).revisionNumber(0).build())
            .model("QI-Core v4.1.1")
            .draft(false)
            .cql("this is totally valid CQL here")
            .build();
    when(cqlLibraryRepository.findAllByCqlLibraryNameAndDraftAndVersion(any(), anyBoolean(), any()))
        .thenReturn(List.of(cqlLibrary));
    when(librarySetService.findByLibrarySetId("libSetId"))
        .thenReturn(LibrarySet.builder().librarySetId("libSetId").owner(owner).build());
    return cqlLibrary;
  }

  @Test
  public void testGetVersionedCqlLibrarySetsOwnerDisplayNameToFullName() {
    mockSingleVersionedLibraryForOwnerEnrichment("owner1");
    when(userServiceClient.getSingleUserDetails("owner1"))
        .thenReturn(UserDetailsDto.builder().firstName("John").lastName("Doe").build());

    CqlLibraryDto result =
        cqlLibraryService.getVersionedCqlLibrary(
            "TestFHIRHelpers",
            "1.0.000",
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            false,
            "Info",
            "test-okta");

    assertEquals("John Doe", result.getOwnerDisplayName());
    assertEquals("libSetId", result.getLibrarySetId());
    assertNotNull(result.getLibrarySet());
    assertEquals("libSetId", result.getLibrarySet().getLibrarySetId());
  }

  @Test
  public void testGetVersionedCqlLibraryFallsBackToHarpIdWhenUserHasNoName() {
    mockSingleVersionedLibraryForOwnerEnrichment("owner1");
    when(userServiceClient.getSingleUserDetails("owner1"))
        .thenReturn(UserDetailsDto.builder().firstName("").lastName("").build());

    CqlLibraryDto result =
        cqlLibraryService.getVersionedCqlLibrary(
            "TestFHIRHelpers",
            "1.0.000",
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            false,
            "Info",
            "test-okta");

    assertEquals("owner1", result.getOwnerDisplayName());
  }

  @Test
  public void testGetVersionedCqlLibraryFallsBackToDashWhenUserLookupFails() {
    mockSingleVersionedLibraryForOwnerEnrichment("owner1");
    when(userServiceClient.getSingleUserDetails("owner1")).thenReturn(null);

    CqlLibraryDto result =
        cqlLibraryService.getVersionedCqlLibrary(
            "TestFHIRHelpers",
            "1.0.000",
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            false,
            "Info",
            "test-okta");

    assertEquals("-", result.getOwnerDisplayName());
  }

  @Test
  public void testGetVersionedCqlShouldThrowExceptionWhenNoLibrariesAreFound() {
    List<CqlLibrary> cqlLibraries = new ArrayList<>();
    when(cqlLibraryRepository.findAllByCqlLibraryNameAndDraftAndVersion(any(), anyBoolean(), any()))
        .thenReturn(cqlLibraries);
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            cqlLibraryService.getVersionedCqlLibrary(
                "TestFHIRHelpers",
                "1.0.000",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                true,
                "Info",
                "test-okta"));
  }

  @Test
  public void testGetVersionedCqlShouldThrowExceptionWhenMoreThanOneLibraryIsFound() {
    List<CqlLibrary> cqlLibraries = new ArrayList<>();
    var cqlLibrary1 =
        CqlLibrary.builder()
            .cqlLibraryName("TestFHIRHelpers")
            .version(Version.builder().major(1).minor(0).revisionNumber(0).build())
            .model("FHIR")
            .draft(false)
            .build();
    var cqlLibrary2 =
        CqlLibrary.builder()
            .cqlLibraryName("TestFHIRHelpers")
            .version(Version.builder().major(1).minor(0).revisionNumber(0).build())
            .model("QI-Core v4.1.1")
            .draft(false)
            .build();
    cqlLibraries.add(cqlLibrary1);
    cqlLibraries.add(cqlLibrary2);
    when(cqlLibraryRepository.findAllByCqlLibraryNameAndDraftAndVersion(any(), anyBoolean(), any()))
        .thenReturn(cqlLibraries);
    assertThrows(
        GeneralConflictException.class,
        () ->
            cqlLibraryService.getVersionedCqlLibrary(
                "TestFHIRHelpers",
                "1.0.000",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                true,
                "Info",
                "test-okta"));
  }

  @Test
  void testFindCqlLibraryById() {
    String id = "1";
    CqlLibrary lib =
        CqlLibrary.builder().id(id).cqlLibraryName("XyZ").librarySetId("1-2-3-4").build();
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(lib));
    when(librarySetService.findByLibrarySetId(anyString())).thenReturn(new LibrarySet());

    CqlLibrary cqlLib = cqlLibraryService.findCqlLibraryById(id, USERNAME);
    assertEquals(cqlLib.getId(), id);
    assertNotNull(cqlLib.getLibrarySet());
  }

  @Test
  void testFindCqlLibraryByIdNotFound() {
    String id = "1";
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.empty());
    Exception ex =
        assertThrows(
            ResourceNotFoundException.class,
            () -> cqlLibraryService.findCqlLibraryById(id, USERNAME));
    assertEquals(ex.getMessage(), "Could not find resource CQL Library with id: " + id);
  }

  @Test
  public void testGetSharedLibrariesDelegates() {
    List<String> libraryIds = List.of("lib1", "lib2");
    Map<String, List<SharedUser>> expected = Map.of("lib1", List.of(), "lib2", List.of());
    when(librarySharingService.getSharedLibraries(libraryIds, USERNAME)).thenReturn(expected);

    Map<String, List<SharedUser>> result =
        cqlLibraryService.getSharedLibraries(libraryIds, USERNAME);

    assertEquals(expected, result);
    verify(librarySharingService, times(1)).getSharedLibraries(libraryIds, USERNAME);
  }

  @Test
  public void testShareLibrariesDelegates() {
    Map<String, List<String>> input = Map.of("lib1", List.of("user1"));
    Map<String, List<AclSpecification>> expected = Map.of("lib1", List.of());
    when(librarySharingService.shareLibraries(input, USERNAME, ACCESSTOKEN)).thenReturn(expected);

    Map<String, List<AclSpecification>> result =
        cqlLibraryService.shareLibraries(input, USERNAME, ACCESSTOKEN);

    assertEquals(expected, result);
    verify(librarySharingService, times(1)).shareLibraries(input, USERNAME, ACCESSTOKEN);
  }

  @Test
  public void testUnshareLibrariesDelegates() {
    Map<String, List<String>> input = Map.of("lib1", List.of("user1"));
    Map<String, List<AclSpecification>> expected = Map.of("lib1", List.of());
    when(librarySharingService.unshareLibraries(input, USERNAME, ACCESSTOKEN)).thenReturn(expected);

    Map<String, List<AclSpecification>> result =
        cqlLibraryService.unshareLibraries(input, USERNAME, ACCESSTOKEN);

    assertEquals(expected, result);
    verify(librarySharingService, times(1)).unshareLibraries(input, USERNAME, ACCESSTOKEN);
  }

  @Test
  public void testTransferLibraries() {
    String libraryId = "libraryId";
    String user = "user123";

    LibrarySet librarySet =
        LibrarySet.builder().librarySetId("librarySetId").owner("owner").build();

    CqlLibrary library =
        CqlLibrary.builder()
            .id(libraryId)
            .librarySetId("librarySetId")
            .librarySet(librarySet)
            .build();

    when(cqlLibraryRepository.findById(libraryId)).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId("librarySetId")).thenReturn(librarySet);
    when(cqlLibraryAccessControlService.hasAdminRole(eq("owner"), eq(ACCESSTOKEN)))
        .thenReturn(false);
    when(librarySetService.updateOwnership(
            anyString(), anyString(), anyBoolean(), anyString(), anyBoolean()))
        .thenReturn(new LibrarySet());

    List<String> failedLibraries =
        cqlLibraryService.transferLibraries(List.of(libraryId), user, true, "owner", ACCESSTOKEN);

    assertTrue(failedLibraries.isEmpty());

    verify(cqlLibraryAccessControlService).validateHarpId(user, ACCESSTOKEN);
    verify(cqlLibraryAccessControlService).hasAdminRole("owner", ACCESSTOKEN);
    verify(cqlLibraryService).findCqlLibraryById(libraryId, user);
    verify(librarySetService)
        .updateOwnership(eq("librarySetId"), eq(user), eq(true), eq("owner"), eq(false));
  }

  @Test
  public void testTransferLibrariesLibraryNotFound() {
    String libraryId = "libraryId";
    String user = "user123";

    LibrarySet librarySet = LibrarySet.builder().librarySetId("librarySetId").owner(user).build();

    CqlLibrary library =
        CqlLibrary.builder()
            .id(libraryId)
            .librarySetId("librarySetId")
            .librarySet(librarySet)
            .build();

    when(cqlLibraryRepository.findById(libraryId)).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId("librarySetId")).thenReturn(librarySet);
    when(cqlLibraryAccessControlService.hasAdminRole(eq(user), eq(ACCESSTOKEN))).thenReturn(false);
    when(librarySetService.updateOwnership(
            anyString(), anyString(), anyBoolean(), anyString(), anyBoolean()))
        .thenThrow(new ResourceNotFoundException("LibrarySet", "id", "librarySetId"));

    List<String> failedLibraries =
        cqlLibraryService.transferLibraries(List.of(libraryId), user, true, user, ACCESSTOKEN);

    assertEquals(1, failedLibraries.size());
    assertTrue(failedLibraries.contains(libraryId));

    verify(cqlLibraryAccessControlService).validateHarpId(user, ACCESSTOKEN);
    verify(cqlLibraryAccessControlService).hasAdminRole(user, ACCESSTOKEN);
    verify(cqlLibraryService).findCqlLibraryById(libraryId, user);
    verify(librarySetService)
        .updateOwnership(eq("librarySetId"), eq(user), eq(true), eq(user), eq(false));
  }

  @Test
  public void testDeleteDraftLibraryWithIdNotFound() {
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> cqlLibraryService.deleteDraftLibrary("MISSING", "TEST_USER", "ACCESSTOKEN"));
  }

  @Test
  public void testDeleteDraftLibraryWithVersionedLibrary() {
    CqlLibrary library =
        CqlLibrary.builder()
            .draft(false)
            .id("LibID")
            .librarySetId("LibSetID")
            .version(Version.parse("1.0.0"))
            .build();
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(library));

    when(librarySetService.findByLibrarySetId(anyString()))
        .thenReturn(LibrarySet.builder().librarySetId("LibSetID").owner("TEST_USER").build());

    assertThrows(
        InvalidResourceStateException.class,
        () -> cqlLibraryService.deleteDraftLibrary("LibID", "TEST_USER", "ACCESSTOKEN"));
  }

  @Test
  public void testDeleteDraftLibraryWithDraftLibraryNonOwner() {
    CqlLibrary library =
        CqlLibrary.builder()
            .draft(false)
            .id("LibID")
            .librarySetId("LibSetID")
            .version(Version.parse("1.0.0"))
            .build();
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId(anyString()))
        .thenReturn(LibrarySet.builder().librarySetId("LibSetID").owner("SOME_OTHER_USER").build());

    assertThrows(
        PermissionDeniedException.class,
        () -> cqlLibraryService.deleteDraftLibrary("LibID", "TEST_USER", "ACCESSTOKEN"));
  }

  @Test
  public void testDeleteDraftLibraryWithDraftLibrary() {
    CqlLibrary library =
        CqlLibrary.builder()
            .draft(true)
            .id("LibID")
            .librarySetId("LibSetID")
            .version(Version.parse("1.0.0"))
            .build();
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId(anyString()))
        .thenReturn(LibrarySet.builder().librarySetId("LibSetID").owner("TEST_USER").build());
    doNothing().when(cqlLibraryRepository).delete(any(CqlLibrary.class));

    CqlLibrary output = cqlLibraryService.deleteDraftLibrary("LibID", "TEST_USER", "ACCESSTOKEN");

    assertThat(output, is(notNullValue()));
    assertThat(output, is(equalTo(library)));
  }

  @Test
  public void testDeleteDraftLibraryLockedByOtherUser() {
    LockInfo lockInfo =
        LockInfo.builder().isLocked(true).lockedBy("other.user").lockedId("LibID").build();
    when(cqlLibraryLockService.lockCqlLibrary(eq("LibID"), eq("TEST_USER"))).thenReturn(lockInfo);

    assertThrows(
        ResourceLockedException.class,
        () -> cqlLibraryService.deleteDraftLibrary("LibID", "TEST_USER", "ACCESSTOKEN"));
    // ensure repository was never touched due to early lock failure
    verifyNoInteractions(cqlLibraryRepository, librarySetService);
  }

  @Test
  public void testDeleteDraftLibraryLockAcquiredBySameUser() {
    LockInfo lockInfo =
        LockInfo.builder().isLocked(true).lockedBy("TEST_USER").lockedId("LibID").build();
    when(cqlLibraryLockService.lockCqlLibrary(eq("LibID"), eq("TEST_USER"))).thenReturn(lockInfo);
    CqlLibrary library =
        CqlLibrary.builder()
            .draft(true)
            .id("LibID")
            .librarySetId("LibSetID")
            .version(Version.parse("1.0.0"))
            .build();
    when(cqlLibraryRepository.findById("LibID")).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId("LibSetID"))
        .thenReturn(LibrarySet.builder().librarySetId("LibSetID").owner("TEST_USER").build());
    doNothing().when(cqlLibraryRepository).delete(any(CqlLibrary.class));

    CqlLibrary output = cqlLibraryService.deleteDraftLibrary("LibID", "TEST_USER", "ACCESSTOKEN");

    assertThat(output, is(notNullValue()));
    assertThat(output.getId(), is(equalTo("LibID")));
    verify(cqlLibraryRepository, times(1)).delete(eq(library));
  }

  @Test
  void testFindLibraryUsage() {
    String libraryName = "test";
    String owner = "john";
    LibraryUsage usage = LibraryUsage.builder().name(libraryName).owner(owner).build();
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(true);
    when(cqlLibraryRepository.findLibraryUsageByLibraryName(anyString()))
        .thenReturn(List.of(usage));
    List<LibraryUsage> libraryUsages = cqlLibraryService.findLibraryUsage(libraryName);
    assertThat(libraryUsages.size(), is(equalTo(1)));
    assertThat(libraryUsages.get(0).getName(), is(equalTo(libraryName)));
    assertThat(libraryUsages.get(0).getOwner(), is(equalTo(owner)));
  }

  @Test
  void testFindLibraryUsageWhenLibraryNameBlank() {
    Exception ex =
        assertThrows(
            BadRequestObjectException.class, () -> cqlLibraryService.findLibraryUsage(null));
    assertThat(ex.getMessage(), is(equalTo("Please provide library name.")));
  }

  @Test
  void testDeleteLibraryAlongWithVersionsSuccess() {
    String libraryName = "test";
    String librarySetId = "LibSetID";
    CqlLibrary cqlLibrary =
        CqlLibrary.builder().cqlLibraryName(libraryName).librarySetId(librarySetId).build();
    LibrarySet librarySet = LibrarySet.builder().librarySetId(librarySetId).owner("owner1").build();

    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(true);
    when(cqlLibraryRepository.findLibraryUsageByLibraryName(anyString())).thenReturn(List.of());
    when(measureServiceClient.getLibraryUsageInMeasures(anyString(), anyString()))
        .thenReturn(List.of());
    when(cqlLibraryRepository.findAllByCqlLibraryName(cqlLibrary.getCqlLibraryName()))
        .thenReturn(List.of(cqlLibrary));
    when(librarySetService.findByLibrarySetId(anyString())).thenReturn(librarySet);

    cqlLibraryService.deleteLibraryAlongWithVersions(libraryName, "token", "owner1");
    verify(cqlLibraryRepository, times(1)).deleteAll(List.of(cqlLibrary));
  }

  @Test
  void testDeleteLibraryAlongWithVersionsIfUsedInLibrary() {
    String libraryName = "test";
    String owner = "john";
    LibraryUsage usage = LibraryUsage.builder().name(libraryName).owner(owner).build();
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(true);
    when(cqlLibraryRepository.findLibraryUsageByLibraryName(anyString()))
        .thenReturn(List.of(usage));
    Exception ex =
        assertThrows(
            GeneralConflictException.class,
            () -> cqlLibraryService.deleteLibraryAlongWithVersions(libraryName, "token", "harpId"));
    assertThat(
        ex.getMessage(), is(equalTo("Library is being used actively, hence can not be deleted.")));
  }

  @Test
  void testDeleteLibraryAlongWithVersionsIfUsedInMeasure() {
    String libraryName = "test";
    String owner = "john";
    LibraryUsage usage = LibraryUsage.builder().name(libraryName).owner(owner).build();
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(true);
    when(cqlLibraryRepository.findLibraryUsageByLibraryName(anyString())).thenReturn(List.of());
    when(measureServiceClient.getLibraryUsageInMeasures(anyString(), anyString()))
        .thenReturn(List.of(usage));
    Exception ex =
        assertThrows(
            GeneralConflictException.class,
            () -> cqlLibraryService.deleteLibraryAlongWithVersions(libraryName, "token", "harpId"));
    assertThat(
        ex.getMessage(), is(equalTo("Library is being used actively, hence can not be deleted.")));
  }

  @Test
  void testDeleteLibraryAlongWithVersionsIfOneNotExists() {
    String libraryName = "test";
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(false);
    Exception ex =
        assertThrows(
            ResourceNotFoundException.class,
            () -> cqlLibraryService.deleteLibraryAlongWithVersions(libraryName, "token", "harpId"));
    assertThat(
        ex.getMessage(), is(equalTo("Could not find resource Library with name: " + libraryName)));
  }

  @Test
  void testDeleteLibraryAlongWithVersionsHarpIdMismatchException() {
    String libraryName = "test";
    String librarySetId = "librarySetId";
    CqlLibrary cqlLibrary =
        CqlLibrary.builder()
            .cqlLibraryName(libraryName)
            .id("libraryId")
            .librarySetId(librarySetId)
            .build();
    LibrarySet librarySet = LibrarySet.builder().librarySetId(librarySetId).owner("owner2").build();

    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(true);
    when(cqlLibraryRepository.findLibraryUsageByLibraryName(anyString())).thenReturn(List.of());
    when(measureServiceClient.getLibraryUsageInMeasures(anyString(), anyString()))
        .thenReturn(List.of());
    when(cqlLibraryRepository.findAllByCqlLibraryName(cqlLibrary.getCqlLibraryName()))
        .thenReturn(List.of(cqlLibrary));
    when(librarySetService.findByLibrarySetId(anyString())).thenReturn(librarySet);

    Exception ex =
        assertThrows(
            HarpIdMismatchException.class,
            () -> cqlLibraryService.deleteLibraryAlongWithVersions(libraryName, "token", "owner1"));
    assertThat(
        ex.getMessage(),
        is(
            equalTo(
                "Response could not be completed because the HARP id of owner1 passed in does not"
                    + " match the owner of the library with the library id of libraryId. The owner"
                    + " of the library is owner2")));

    verify(cqlLibraryRepository, times(0)).deleteAll(List.of(cqlLibrary));
  }

  @Test
  void testDeleteCqlLibraryByIdNotFound() {
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> cqlLibraryService.deleteCqlLibraryById("missingId", "owner1", "admin"));
  }

  @Test
  void testDeleteCqlLibraryByIdLibrarySetNotFound() {
    CqlLibrary library = CqlLibrary.builder().id("libId").librarySetId("libSetId").build();

    when(cqlLibraryRepository.findById("libId")).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId("libSetId")).thenReturn(null);

    assertThrows(
        ResourceNotFoundException.class,
        () -> cqlLibraryService.deleteCqlLibraryById("libId", "owner1", "admin"));
    verify(cqlLibraryRepository, times(0)).delete(any(CqlLibrary.class));
  }

  @Test
  void testDeleteCqlLibraryByIdHarpIdMismatch() {
    CqlLibrary library = CqlLibrary.builder().id("libId").librarySetId("libSetId").build();
    LibrarySet librarySet = LibrarySet.builder().librarySetId("libSetId").owner("owner2").build();

    when(cqlLibraryRepository.findById("libId")).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId("libSetId")).thenReturn(librarySet);

    Exception ex =
        assertThrows(
            HarpIdMismatchException.class,
            () -> cqlLibraryService.deleteCqlLibraryById("libId", "owner1", "admin"));
    assertThat(
        ex.getMessage(),
        is(
            equalTo(
                "Response could not be completed because the HARP id of owner1 passed in does not"
                    + " match the owner of the library with the library id of libId. The owner of"
                    + " the library is owner2")));
    verify(cqlLibraryRepository, times(0)).delete(any(CqlLibrary.class));
  }

  @Test
  void testDeleteCqlLibraryByIdSuccess() {
    CqlLibrary library = CqlLibrary.builder().id("libId").librarySetId("libSetId").build();
    LibrarySet librarySet = LibrarySet.builder().librarySetId("libSetId").owner("owner1").build();

    when(cqlLibraryRepository.findById("libId")).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId("libSetId")).thenReturn(librarySet);
    doNothing().when(cqlLibraryRepository).delete(any(CqlLibrary.class));

    CqlLibrary result = cqlLibraryService.deleteCqlLibraryById("libId", "owner1", "admin");

    assertThat(result, is(equalTo(library)));
    verify(cqlLibraryRepository, times(1)).delete(library);
    verify(actionLogService, times(1)).logAction("libId", ActionType.DELETED, "admin", "actionLog");
  }

  @Test
  void testFindLibrariesByNameAndModel() {
    String libraryName = "test";
    String model = "QICore 4.1.1";
    LibraryListDTO l1 =
        LibraryListDTO.builder()
            .cqlLibraryName("L1")
            .version(Version.parse("0.2.000"))
            .model("QICore 4.1.1")
            .librarySet(LibrarySet.builder().owner("owner1").build())
            .build();
    LibraryListDTO l2 =
        LibraryListDTO.builder()
            .cqlLibraryName("L1")
            .version(Version.parse("0.1.000"))
            .model("QICore 4.1.1")
            .librarySet(LibrarySet.builder().owner("owner2").build())
            .build();
    when(cqlLibraryRepository.findLibrariesByNameAndModelOrderByNameAscAndVersionDsc(
            anyString(), anyString()))
        .thenReturn(List.of(l1, l2));
    Map<String, UserDetailsDto> userDetailsMap =
        Map.of(
            "owner1",
            UserDetailsDto.builder().firstName("John").lastName("Doe").build(),
            "owner2",
            UserDetailsDto.builder().firstName("Jane").lastName("Smith").build());
    when(userServiceClient.getBulkUserDetails(anyList())).thenReturn(userDetailsMap);

    List<LibraryListDTO> result = cqlLibraryService.findLibrariesByNameAndModel(libraryName, model);

    assertThat(result.size(), equalTo(2));
    assertThat(result.get(0).getOwnerDisplayName(), equalTo("John Doe"));
    assertThat(result.get(1).getOwnerDisplayName(), equalTo("Jane Smith"));
    verify(userServiceClient, times(1)).getBulkUserDetails(List.of("owner1", "owner2"));
  }

  @Test
  void testFindLibrariesByNameAndModelFallsBackToOwnerIdWhenUserMissing() {
    LibraryListDTO l1 =
        LibraryListDTO.builder()
            .cqlLibraryName("L1")
            .version(Version.parse("0.1.000"))
            .model("QICore 4.1.1")
            .librarySet(LibrarySet.builder().owner("owner1").build())
            .build();
    when(cqlLibraryRepository.findLibrariesByNameAndModelOrderByNameAscAndVersionDsc(
            anyString(), anyString()))
        .thenReturn(List.of(l1));
    when(userServiceClient.getBulkUserDetails(anyList())).thenReturn(Collections.emptyMap());

    List<LibraryListDTO> result = cqlLibraryService.findLibrariesByNameAndModel("test", "QICore");

    assertThat(result.size(), equalTo(1));
    assertThat(result.get(0).getOwnerDisplayName(), equalTo("-"));
  }

  @Test
  void testFindLibrariesByNameAndModelIfModelMissing() {
    Exception ex =
        assertThrows(
            BadRequestObjectException.class,
            () -> cqlLibraryService.findLibrariesByNameAndModel("Test", null));
    assertThat(ex.getMessage(), is(equalTo("Please provide library name and model.")));
  }

  @Test
  void testFindLibrariesByNameAndModelIfLibraryNameMissing() {
    Exception ex =
        assertThrows(
            BadRequestObjectException.class,
            () -> cqlLibraryService.findLibrariesByNameAndModel(null, "QDM"));
    assertThat(ex.getMessage(), is(equalTo("Please provide library name and model.")));
  }

  @Test
  void testGetLibrarySetBySetId() {
    String librarySetId = "1-1-1-1";
    String libraryVersion = "1.0.000";
    String owner = "John";
    LibrarySet librarySet = LibrarySet.builder().librarySetId(librarySetId).owner(owner).build();
    CqlLibrary lib1 =
        CqlLibrary.builder()
            .cqlLibraryName("Lib1")
            .librarySetId(librarySetId)
            .version(Version.parse("0.1.000"))
            .build();
    CqlLibrary lib2 =
        CqlLibrary.builder()
            .cqlLibraryName("Lib1")
            .librarySetId(librarySetId)
            .version(Version.parse(libraryVersion))
            .build();
    when(cqlLibraryRepository.findByLibrarySetIdAndDraftAndActive(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(List.of(lib1, lib2));
    when(librarySetRepository.findByLibrarySetId(anyString()))
        .thenReturn(Optional.ofNullable(librarySet));
    LibrarySetDTO libraryDTO = cqlLibraryService.getLibrarySetBySetId(librarySetId);
    assertThat(libraryDTO.getLibrarySet().getLibrarySetId(), equalTo(librarySetId));
    assertThat(libraryDTO.getLibrarySet().getOwner(), equalTo(owner));
    assertThat(libraryDTO.getLibraries().size(), equalTo(2));
  }

  @Test
  void testGetLibrarySetBySetIdIfNoLibraryExistsWithSetId() {
    String librarySetId = "1-1-1-1";
    when(cqlLibraryRepository.findByLibrarySetIdAndDraftAndActive(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(List.of());
    LibrarySetDTO libraryDTO = cqlLibraryService.getLibrarySetBySetId(librarySetId);
    assertThat(libraryDTO, equalTo(null));
  }

  @Test
  void testGetLibrarySetBySetIdIfLibrarySetNotFound() {
    String librarySetId = "1-1-1-1";
    CqlLibrary lib1 =
        CqlLibrary.builder()
            .cqlLibraryName("Lib1")
            .librarySetId(librarySetId)
            .version(Version.parse("0.1.000"))
            .build();
    when(cqlLibraryRepository.findByLibrarySetIdAndDraftAndActive(
            anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(List.of(lib1));
    when(librarySetRepository.findByLibrarySetId(anyString())).thenReturn(Optional.empty());
    LibrarySetDTO libraryDTO = cqlLibraryService.getLibrarySetBySetId(librarySetId);
    assertThat(libraryDTO.getLibrarySet(), equalTo(null));
    assertThat(libraryDTO.getLibraries().size(), equalTo(1));
  }

  @Test
  void testGetLibrarySetBySetIdIfSetIdNotProvided() {
    Exception exception =
        assertThrows(
            BadRequestObjectException.class, () -> cqlLibraryService.getLibrarySetBySetId(null));
    assertThat(exception.getMessage(), equalTo("Please provide library set ID."));
  }

  @Test
  void testGetLibrariesByLibrarySetId() {
    String librarySetId = "testSetId";
    LibraryListDTO l1 = LibraryListDTO.builder().id("L1").librarySetId(librarySetId).build();
    when(cqlLibraryRepository.findLibrariesByLibrarySetId(
            eq(librarySetId), anyBoolean(), any(LibrarySearchCriteria.class)))
        .thenReturn(List.of(l1));
    List<LibraryListDTO> results =
        cqlLibraryService.getLibrariesByLibrarySetId(
            librarySetId, true, LibrarySearchCriteria.builder().build());
    assertEquals(1, results.size());
    assertThat(results.get(0).getId(), equalTo("L1"));
    assertThat(results.get(0).getLibrarySetId(), equalTo(librarySetId));
  }

  @Test
  void testGetLibrariesByLibrarySetIdThrowsBadRequestObjectException() {
    Exception exception =
        assertThrows(
            BadRequestObjectException.class,
            () -> cqlLibraryService.getLibrariesByLibrarySetId("", true, null));
    assertThat(exception.getMessage(), equalTo("Please provide library set ID."));
  }

  @Test
  void testHasAssociatedLibrariesTrue() {
    LibraryListDTO l1 = LibraryListDTO.builder().id("L1").librarySetId("setId").build();
    when(cqlLibraryRepository.countAllByLibrarySetIdAndActiveAndIdIsNot(
            eq("setId"), anyBoolean(), eq("L1")))
        .thenReturn(2);
    assertTrue(cqlLibraryService.hasAssociatedLibraries(l1));
  }

  @Test
  void testHasAssociatedLibrariesFalse() {
    LibraryListDTO l1 = LibraryListDTO.builder().id("L1").librarySetId("setId").build();
    when(cqlLibraryRepository.countAllByLibrarySetIdAndActiveAndIdIsNot(
            eq("setId"), anyBoolean(), eq("L1")))
        .thenReturn(0);
    assertFalse(cqlLibraryService.hasAssociatedLibraries(l1));
  }

  @Test
  void throwsInvalidRequestExceptionWhenCqlLibraryIdIsBlank() {
    String cqlLibraryId = " ";
    String userName = "testUser";

    Exception exception =
        assertThrows(
            InvalidRequestException.class,
            () -> cqlLibraryService.getCqlLibraryHistory(cqlLibraryId, userName));

    assertThat(exception.getMessage(), is(equalTo("Cql Library ID cannot be null or empty.")));
  }

  @Test
  void throwsResourceNotFoundExceptionWhenCqlLibraryDoesNotExist() {
    String cqlLibraryId = "nonExistentId";
    String userName = "testUser";

    when(cqlLibraryRepository.findById(cqlLibraryId)).thenReturn(Optional.empty());

    Exception exception =
        assertThrows(
            ResourceNotFoundException.class,
            () -> cqlLibraryService.getCqlLibraryHistory(cqlLibraryId, userName));

    assertThat(exception.getMessage(), is(equalTo("Cql Library does not exist: " + cqlLibraryId)));
  }

  @Test
  void returnsCqlLibraryHistoryWhenLibraryExists() {
    String cqlLibraryId = "existingId";
    String userName = "testUser";
    String librarySetId = "librarySetId";

    CqlLibrary library = CqlLibrary.builder().id(cqlLibraryId).librarySetId(librarySetId).build();
    List<Action> actions =
        new ArrayList<>(
            List.of(
                Action.builder().actionType(ActionType.CREATED).performedBy("testuser").build(),
                Action.builder().actionType(ActionType.UPDATED).performedBy("testuser").build()));

    when(cqlLibraryRepository.findById(cqlLibraryId)).thenReturn(Optional.of(library));
    when(actionLogService.findCqlLibraryHistory(cqlLibraryId, librarySetId)).thenReturn(actions);

    List<Action> result = cqlLibraryService.getCqlLibraryHistory(cqlLibraryId, userName);

    assertThat(result.size(), is(equalTo(2)));
    assertThat(result.get(0).getActionType(), is(equalTo(ActionType.CREATED)));
    assertThat(result.get(1).getActionType(), is(equalTo(ActionType.UPDATED)));
    verify(librarySetService, times(1)).populatePerformedByDisplayNames(actions);
  }

  @Test
  void returnsEmptyCqlLibraryHistoryWithoutFetchingUserDetails() {
    CqlLibrary library = CqlLibrary.builder().id("existingId").librarySetId("librarySetId").build();
    when(cqlLibraryRepository.findById("existingId")).thenReturn(Optional.of(library));
    when(actionLogService.findCqlLibraryHistory("existingId", "librarySetId"))
        .thenReturn(new ArrayList<>());
    List<Action> result = cqlLibraryService.getCqlLibraryHistory("existingId", "testUser");
    assertThat(result.isEmpty(), is(true));
    verify(userServiceClient, never()).getBulkUserDetails(anyList());
  }

  @Test
  void testFindCqlLibraryByIdWithLock() {
    String id = "1";
    CqlLibrary lib =
        CqlLibrary.builder().id(id).cqlLibraryName("XyZ").librarySetId("1-2-3-4").build();
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(lib));
    when(librarySetService.findByLibrarySetId(anyString())).thenReturn(new LibrarySet());

    when(cqlLibraryLockService.findByCqlLibraryId(anyString()))
        .thenReturn(CqlLibraryLock.builder().lockedBy("another.user").build());

    CqlLibrary cqlLib = cqlLibraryService.findCqlLibraryById(id, USERNAME);
    assertEquals(cqlLib.getId(), id);
    assertNotNull(cqlLib.getLibrarySet());
    assertNotNull(cqlLib.getCqlLibraryLock());
  }

  @Test
  void testFindCqlLibraryByIdNoLock() {
    String id = "1";
    CqlLibrary lib =
        CqlLibrary.builder().id(id).cqlLibraryName("XyZ").librarySetId("1-2-3-4").build();
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(lib));
    when(librarySetService.findByLibrarySetId(anyString())).thenReturn(new LibrarySet());

    when(cqlLibraryLockService.findByCqlLibraryId(anyString())).thenReturn(null);

    CqlLibrary cqlLib = cqlLibraryService.findCqlLibraryById(id, USERNAME);
    assertEquals(cqlLib.getId(), id);
    assertNotNull(cqlLib.getLibrarySet());
    assertNull(cqlLib.getCqlLibraryLock());
  }

  @Test
  void testFindCqlLibraryByIdLockedBySelf() {
    String id = "1";
    CqlLibrary lib =
        CqlLibrary.builder().id(id).cqlLibraryName("XyZ").librarySetId("1-2-3-4").build();
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(lib));
    when(librarySetService.findByLibrarySetId(anyString())).thenReturn(new LibrarySet());

    when(cqlLibraryLockService.findByCqlLibraryId(anyString()))
        .thenReturn(CqlLibraryLock.builder().lockedBy(USERNAME).build());

    CqlLibrary cqlLib = cqlLibraryService.findCqlLibraryById(id, USERNAME);
    assertEquals(cqlLib.getId(), id);
    assertNotNull(cqlLib.getLibrarySet());
    assertNull(cqlLib.getCqlLibraryLock());
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForNullLibrary() {
    assertThrows(
        BadRequestObjectException.class, () -> cqlLibraryService.updateCqlLibrary(null, USERNAME));
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForNullId() {
    assertThrows(
        BadRequestObjectException.class,
        () -> cqlLibraryService.updateCqlLibrary(CqlLibrary.builder().build(), USERNAME));
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForNullUser() {
    assertThrows(
        BadRequestObjectException.class,
        () -> cqlLibraryService.updateCqlLibrary(CqlLibrary.builder().build(), null));
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForNotFound() {
    final CqlLibrary updatingLibrary =
        CqlLibrary.builder().id("Library1_ID").cqlLibraryName("NewName").build();

    doThrow(new ResourceNotFoundException("CQL Library", updatingLibrary.getId()))
        .when(cqlLibraryRepository)
        .findById(anyString());

    assertThrows(
        ResourceNotFoundException.class,
        () -> cqlLibraryService.updateCqlLibrary(updatingLibrary, USERNAME));
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForNonUniqueNameUpdate() {
    LibrarySet librarySet =
        LibrarySet.builder().librarySetId("librarySetId").owner(USERNAME).build();
    final CqlLibrary existingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .librarySetId(librarySet.getLibrarySetId())
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .draft(true)
            .createdBy("test.user")
            .librarySet(
                LibrarySet.builder().librarySetId("testLibrarySetId").owner("test.user").build())
            .build();
    final CqlLibrary updatingLibrary =
        existingLibrary.toBuilder().id("Library1_ID").cqlLibraryName("NewName").build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingLibrary));
    when(librarySetService.findByLibrarySetId(anyString())).thenReturn(librarySet);
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(true);

    assertThrows(
        DuplicateKeyException.class,
        () -> cqlLibraryService.updateCqlLibrary(updatingLibrary, USERNAME));
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForNonDraftUpdate() {
    LibrarySet librarySet =
        LibrarySet.builder().librarySetId("librarySetId").owner(USERNAME).build();
    final CqlLibrary existingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .librarySetId(librarySet.getLibrarySetId())
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .draft(false)
            .createdBy("test.user")
            .librarySet(
                LibrarySet.builder().librarySetId("testLibrarySetId").owner("test.user").build())
            .build();
    final CqlLibrary updatingLibrary =
        existingLibrary.toBuilder().id("Library1_ID").cqlLibraryName("NewName").build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingLibrary));
    when(librarySetService.findByLibrarySetId(anyString())).thenReturn(librarySet);

    assertThrows(
        InvalidResourceStateException.class,
        () -> cqlLibraryService.updateCqlLibrary(updatingLibrary, USERNAME));
  }

  @Test
  public void testUpdateCqlLibraryThrowsPermissionDeniedException() {
    LibrarySet librarySet =
        LibrarySet.builder().librarySetId("librarySetId").owner("another user").build();
    final CqlLibrary existingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .librarySetId(librarySet.getLibrarySetId())
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .draft(true)
            .createdBy("test.user")
            .librarySet(
                LibrarySet.builder().librarySetId("testLibrarySetId").owner("test.user").build())
            .build();
    final CqlLibrary updatingLibrary =
        existingLibrary.toBuilder().id("Library1_ID").cqlLibraryName("NewName").build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingLibrary));
    when(librarySetService.findByLibrarySetId(anyString())).thenReturn(librarySet);
    doThrow(new PermissionDeniedException("CQL Library", "Library1_ID", USERNAME))
        .when(cqlLibraryAccessControlService)
        .checkAccessPermissions(any(CqlLibrary.class), eq(USERNAME));

    assertThrows(
        PermissionDeniedException.class,
        () -> cqlLibraryService.updateCqlLibrary(updatingLibrary, USERNAME));
  }

  @Test
  public void testUpdateCqlLibrarySuccessfully() {
    final String cql =
        "library testCql version '2.1.000'\n"
            + "using QICore version '4.1.1'\n"
            + "include TestLibrary17194110463836086082 version '1.0.000' called Test";
    final Instant createdTime = Instant.now().minus(100, ChronoUnit.MINUTES);
    final CqlLibrary existingLibrary =
        getTestCqlLibrary("L1", "Library1", ModelType.QI_CORE.getValue(), USERNAME);
    existingLibrary.setCql("library testCql version '1.0.000'");
    existingLibrary.setCreatedAt(createdTime);
    existingLibrary.setLastModifiedAt(createdTime);
    final CqlLibrary updatingLibrary =
        existingLibrary.toBuilder()
            .id("L1")
            .cqlLibraryName("NewName")
            .cql(cql)
            .draft(false)
            .build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingLibrary));
    when(librarySetService.findByLibrarySetId(anyString()))
        .thenReturn(existingLibrary.getLibrarySet());
    when(cqlLibraryRepository.save(any(CqlLibrary.class))).thenReturn(updatingLibrary);

    CqlLibrary updatedLibrary = cqlLibraryService.updateCqlLibrary(updatingLibrary, USERNAME);
    assertThat(updatedLibrary, is(equalTo(updatingLibrary)));
    assertThat(updatedLibrary, is(notNullValue()));
    assertThat(updatedLibrary.getId(), is(equalTo(updatedLibrary.getId())));
    assertThat(updatedLibrary.getCqlLibraryName(), is(equalTo("NewName")));
    assertThat(updatedLibrary.getCql(), is(equalTo(cql)));
    assertThat(updatedLibrary.getCreatedAt(), is(equalTo(createdTime)));
    assertThat(updatedLibrary.getCreatedBy(), is(equalTo("User2")));
    assertThat(updatedLibrary.getLastModifiedAt(), is(notNullValue()));
    assertThat(updatedLibrary.getLastModifiedAt().isAfter(createdTime), is(true));
    assertThat(updatedLibrary.getLastModifiedBy(), is(equalTo(USERNAME)));
    assertThat(updatedLibrary.getIncludedLibraries().size(), is(equalTo(1)));
    verify(actionLogService, times(1))
        .logAction(anyString(), any(ActionType.class), anyString(), anyString());
    verify(actionLogService, times(1))
        .logAction(eq("L1"), eq(ActionType.UPDATED), eq(USERNAME), eq("actionLog"));
  }

  @Test
  public void testUpdateLockedCqlLibraryThrowsException() {
    final CqlLibrary existingLibrary =
        getTestCqlLibrary("L1", "Library1", ModelType.QI_CORE.getValue(), USERNAME);
    final CqlLibrary updates =
        getTestCqlLibrary("L1", "NewName", ModelType.QI_CORE.getValue(), USERNAME);

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingLibrary));
    when(librarySetService.findByLibrarySetId(anyString()))
        .thenReturn(existingLibrary.getLibrarySet());
    when(cqlLibraryLockService.lockCqlLibrary(anyString(), anyString()))
        .thenReturn(LockInfo.builder().isLocked(true).lockedBy("John").build());

    assertThrows(
        ResourceLockedException.class, () -> cqlLibraryService.updateCqlLibrary(updates, USERNAME));

    verify(cqlLibraryRepository, times(0)).save(any(CqlLibrary.class));
    verify(actionLogService, times(0))
        .logAction(anyString(), any(ActionType.class), anyString(), anyString());
  }

  @Test
  public void testUpdateCqlLibrarySuccessfullyWhenLockedBySameUser() {
    final String cql =
        "library testCql version '2.1.000'\n"
            + "using QICore version '4.1.1'\n"
            + "include TestLibrary17194110463836086082 version '1.0.000' called Test";
    final Instant createdTime = Instant.now().minus(100, ChronoUnit.MINUTES);
    final CqlLibrary existingLibrary =
        getTestCqlLibrary("L1", "Library1", ModelType.QI_CORE.getValue(), USERNAME);
    existingLibrary.setCql("library testCql version '1.0.000'");
    existingLibrary.setCreatedAt(createdTime);
    existingLibrary.setLastModifiedAt(createdTime);
    final CqlLibrary updatingLibrary =
        existingLibrary.toBuilder()
            .id("L1")
            .cqlLibraryName("NewName")
            .cql(cql)
            .draft(false)
            .build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingLibrary));
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(false);
    when(librarySetService.findByLibrarySetId(anyString()))
        .thenReturn(existingLibrary.getLibrarySet());
    when(cqlLibraryRepository.save(any(CqlLibrary.class))).thenReturn(updatingLibrary);
    when(cqlLibraryLockService.lockCqlLibrary(anyString(), anyString()))
        .thenReturn(
            LockInfo.builder()
                .isLocked(true)
                .lockedBy(existingLibrary.getLibrarySet().getOwner())
                .build());

    CqlLibrary updatedLibrary = cqlLibraryService.updateCqlLibrary(updatingLibrary, USERNAME);
    assertThat(updatedLibrary, is(equalTo(updatingLibrary)));
    assertThat(updatedLibrary, is(notNullValue()));
    assertThat(updatedLibrary.getId(), is(equalTo(updatedLibrary.getId())));
    assertThat(updatedLibrary.getCqlLibraryName(), is(equalTo("NewName")));
    assertThat(updatedLibrary.getCql(), is(equalTo(cql)));
    assertThat(updatedLibrary.getCreatedAt(), is(equalTo(createdTime)));
    assertThat(updatedLibrary.getCreatedBy(), is(equalTo("User2")));
    assertThat(updatedLibrary.getLastModifiedAt(), is(notNullValue()));
    assertThat(updatedLibrary.getLastModifiedAt().isAfter(createdTime), is(true));
    assertThat(updatedLibrary.getLastModifiedBy(), is(equalTo(USERNAME)));
    assertThat(updatedLibrary.getIncludedLibraries().size(), is(equalTo(1)));
    verify(actionLogService, times(1))
        .logAction(anyString(), any(ActionType.class), anyString(), anyString());
  }

  private CqlLibrary getTestCqlLibrary(String id, String name, String model, String username) {
    final String cql =
        "library testCql version '2.1.000'\n"
            + "using QICore version '4.1.1'\n"
            + "include TestLibrary17194110463836086082 version '1.0.000' called Test";
    return CqlLibrary.builder()
        .id(id)
        .librarySetId("testLibrarySetId")
        .cqlLibraryName(name)
        .model(model)
        .cql(cql)
        .draft(true)
        .createdAt(Instant.now())
        .createdBy("User2")
        .librarySet(LibrarySet.builder().librarySetId("testLibrarySetId").owner(USERNAME).build())
        .lastModifiedAt(Instant.now().plus(1, ChronoUnit.MINUTES))
        .lastModifiedBy(username)
        .build();
  }

  @Test
  public void getLibrariesByLibrarySetIdReturnsLibrariesWithOwnerDetails() {
    String librarySetId = "testSetId";
    String ownerId = "owner123";
    LibrarySearchCriteria criteria =
        LibrarySearchCriteria.builder().searchField("testField").build();
    LibrarySet librarySet = LibrarySet.builder().owner(ownerId).build();
    LibraryListDTO library =
        LibraryListDTO.builder().id("L1").librarySetId(librarySetId).librarySet(librarySet).build();
    UserDetailsDto userDetails = UserDetailsDto.builder().firstName("John").lastName("Doe").build();

    when(cqlLibraryRepository.findLibrariesByLibrarySetId(eq(librarySetId), eq(true), eq(criteria)))
        .thenReturn(List.of(library));
    when(userServiceClient.getSingleUserDetails(eq(ownerId))).thenReturn(userDetails);

    List<LibraryListDTO> result =
        cqlLibraryService.getLibrariesByLibrarySetId(librarySetId, true, criteria);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals("John Doe", result.get(0).getOwnerDisplayName());
    verify(userServiceClient, times(1)).getSingleUserDetails(eq(ownerId));
  }

  @Test
  public void getLibrariesByLibrarySetIdEnrichesReviewStatusPerInstance() {
    String librarySetId = "testSetId";
    LibrarySearchCriteria criteria =
        LibrarySearchCriteria.builder().searchField("testField").build();
    LibraryListDTO libraryV2 = LibraryListDTO.builder().id("L2").librarySetId(librarySetId).build();
    LibraryListDTO libraryV1 = LibraryListDTO.builder().id("L1").librarySetId(librarySetId).build();

    when(cqlLibraryRepository.findLibrariesByLibrarySetId(eq(librarySetId), eq(true), eq(criteria)))
        .thenReturn(new ArrayList<>(List.of(libraryV2, libraryV1)));
    when(cqlLibraryReviewRepository.findAllByLibrarySetId(librarySetId))
        .thenReturn(
            List.of(
                CqlLibraryReview.builder()
                    .libraryId("L1")
                    .status(ReviewStatus.READY_FOR_REVIEW)
                    .build(),
                CqlLibraryReview.builder()
                    .libraryId("L2")
                    .status(ReviewStatus.NOT_READY_FOR_REVIEW)
                    .build()));

    List<LibraryListDTO> result =
        cqlLibraryService.getLibrariesByLibrarySetId(librarySetId, true, criteria);

    assertEquals(2, result.size());
    assertEquals("", result.get(0).getReviewStatus());
    assertEquals("Ready", result.get(1).getReviewStatus());
  }

  @Test
  public void getLibrariesByLibrarySetIdFiltersToReadyWhenSearchingByReview() {
    String librarySetId = "testSetId";
    LibrarySearchCriteria criteria =
        LibrarySearchCriteria.builder().searchField("testField").build();
    // Two versions; only L1 has been marked READY_FOR_REVIEW.
    LibraryListDTO libraryV2 = LibraryListDTO.builder().id("L2").librarySetId(librarySetId).build();
    LibraryListDTO libraryV1 = LibraryListDTO.builder().id("L1").librarySetId(librarySetId).build();

    when(cqlLibraryRepository.findLibrariesByLibrarySetId(eq(librarySetId), eq(true), eq(criteria)))
        .thenReturn(new ArrayList<>(List.of(libraryV2, libraryV1)));
    when(cqlLibraryReviewRepository.findAllByLibrarySetId(librarySetId))
        .thenReturn(
            List.of(
                CqlLibraryReview.builder()
                    .libraryId("L1")
                    .status(ReviewStatus.READY_FOR_REVIEW)
                    .build(),
                CqlLibraryReview.builder()
                    .libraryId("L2")
                    .status(ReviewStatus.NOT_READY_FOR_REVIEW)
                    .build()));

    List<LibraryListDTO> result =
        cqlLibraryService.getLibrariesByLibrarySetId(librarySetId, true, criteria);

    assertEquals(2, result.size());
    assertEquals("", result.get(0).getReviewStatus());
    assertEquals("Ready", result.get(1).getReviewStatus());
  }

  @Test
  public void getLibrariesByLibrarySetIdThrowsExceptionWhenLibrarySetIdIsBlank() {
    LibrarySearchCriteria criteria =
        LibrarySearchCriteria.builder().searchField("testField").build();

    Exception exception =
        assertThrows(
            BadRequestObjectException.class,
            () -> cqlLibraryService.getLibrariesByLibrarySetId(" ", true, criteria));

    assertEquals("Please provide library set ID.", exception.getMessage());
    verifyNoInteractions(cqlLibraryRepository, userServiceClient);
  }

  @Test
  public void getLibrariesByLibrarySetIdReturnsEmptyListWhenNoLibrariesFound() {
    String librarySetId = "testSetId";
    LibrarySearchCriteria criteria =
        LibrarySearchCriteria.builder().searchField("testField").build();

    when(cqlLibraryRepository.findLibrariesByLibrarySetId(eq(librarySetId), eq(true), eq(criteria)))
        .thenReturn(Collections.emptyList());

    List<LibraryListDTO> result =
        cqlLibraryService.getLibrariesByLibrarySetId(librarySetId, true, criteria);

    assertNotNull(result);
    assertTrue(result.isEmpty());
    verifyNoInteractions(userServiceClient);
  }

  @Test
  public void testTransferLibrariesThrowsWhenTargetHarpIdNotFound() {
    String libraryId = "libraryId";
    String harpId = "unknownUser";
    String conductedBy = "owner";

    doThrow(
            new InvalidIdException(
                "The provided HARP ID is not associated with an active MADiE user."))
        .when(cqlLibraryAccessControlService)
        .validateHarpId(eq(harpId), eq(ACCESSTOKEN));

    assertThrows(
        InvalidIdException.class,
        () ->
            cqlLibraryService.transferLibraries(
                List.of(libraryId), harpId, true, conductedBy, ACCESSTOKEN));

    verify(librarySetService, never())
        .updateOwnership(anyString(), anyString(), anyBoolean(), anyString(), anyBoolean());
  }

  @Test
  public void testTransferLibrariesThrowsWhenTargetUserIsNotActive() {
    String libraryId = "libraryId";
    String harpId = "inactiveUser";
    String conductedBy = "owner";

    doThrow(
            new InvalidIdException(
                "The provided HARP ID is not associated with an active MADiE user."))
        .when(cqlLibraryAccessControlService)
        .validateHarpId(eq(harpId), eq(ACCESSTOKEN));

    assertThrows(
        InvalidIdException.class,
        () ->
            cqlLibraryService.transferLibraries(
                List.of(libraryId), harpId, true, conductedBy, ACCESSTOKEN));

    verify(librarySetService, never())
        .updateOwnership(anyString(), anyString(), anyBoolean(), anyString(), anyBoolean());
  }

  @Test
  public void testTransferLibrariesWhenGetUserRolesReturnsNull() {
    String libraryId = "libraryId";
    String harpId = "user123";
    String conductedBy = "owner";

    LibrarySet librarySet =
        LibrarySet.builder().librarySetId("librarySetId").owner("owner").build();

    CqlLibrary library =
        CqlLibrary.builder()
            .id(libraryId)
            .librarySetId("librarySetId")
            .librarySet(librarySet)
            .build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId(anyString())).thenReturn(librarySet);
    when(cqlLibraryAccessControlService.hasAdminRole(eq(conductedBy), eq(ACCESSTOKEN)))
        .thenReturn(false);
    when(librarySetService.updateOwnership(
            anyString(), anyString(), anyBoolean(), anyString(), anyBoolean()))
        .thenReturn(new LibrarySet());

    List<String> failedLibraries =
        cqlLibraryService.transferLibraries(
            List.of(libraryId), harpId, true, conductedBy, ACCESSTOKEN);

    assertTrue(failedLibraries.isEmpty());

    verify(cqlLibraryService).findCqlLibraryById(libraryId, harpId);
    verify(librarySetService)
        .updateOwnership(eq("librarySetId"), eq(harpId), eq(true), eq(conductedBy), eq(false));
  }

  @Test
  public void testTransferLibrariesWhenGetUserRolesReturnsNullNonOwnerFails() {
    String libraryId = "libraryId";
    String harpId = "user123";
    String conductedBy = "nonOwner";

    LibrarySet librarySet =
        LibrarySet.builder().librarySetId("librarySetId").owner("actualOwner").build();

    CqlLibrary library =
        CqlLibrary.builder()
            .id(libraryId)
            .librarySetId("librarySetId")
            .librarySet(librarySet)
            .build();

    when(cqlLibraryRepository.findById(libraryId)).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId("librarySetId")).thenReturn(librarySet);
    when(cqlLibraryAccessControlService.hasAdminRole(eq(conductedBy), eq(ACCESSTOKEN)))
        .thenReturn(false);
    doThrow(new PermissionDeniedException("CQL Library", libraryId, conductedBy))
        .when(cqlLibraryAccessControlService)
        .checkOwnership(any(CqlLibrary.class), eq(conductedBy));

    List<String> failedLibraries =
        cqlLibraryService.transferLibraries(
            List.of(libraryId), harpId, true, conductedBy, ACCESSTOKEN);

    assertEquals(1, failedLibraries.size());
    assertTrue(failedLibraries.contains(libraryId));

    verify(cqlLibraryService).findCqlLibraryById(libraryId, harpId);
    verify(librarySetService, never())
        .updateOwnership(anyString(), anyString(), anyBoolean(), anyString(), anyBoolean());
  }

  @Test
  public void testTransferLibrariesByUserWithMadieAdminRole() {
    String libraryId = "libraryId";
    String harpId = "user123";
    String conductedBy = "adminUser";

    LibrarySet librarySet =
        LibrarySet.builder().librarySetId("librarySetId").owner("someOtherOwner").build();

    CqlLibrary library =
        CqlLibrary.builder()
            .id(libraryId)
            .librarySetId("librarySetId")
            .librarySet(librarySet)
            .build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId(anyString())).thenReturn(librarySet);
    when(cqlLibraryAccessControlService.hasAdminRole(eq(conductedBy), eq(ACCESSTOKEN)))
        .thenReturn(true);
    when(librarySetService.updateOwnership(
            anyString(), anyString(), anyBoolean(), anyString(), anyBoolean()))
        .thenReturn(new LibrarySet());

    List<String> failedLibraries =
        cqlLibraryService.transferLibraries(
            List.of(libraryId), harpId, true, conductedBy, ACCESSTOKEN);

    assertTrue(failedLibraries.isEmpty());

    verify(cqlLibraryService).findCqlLibraryById(libraryId, harpId);
    verify(librarySetService)
        .updateOwnership(eq("librarySetId"), eq(harpId), eq(true), eq(conductedBy), eq(true));
  }

  @Test
  public void testTransferLibrariesNonOwnerWithoutAdminRoleFails() {
    String libraryId = "libraryId";
    String harpId = "user123";
    String conductedBy = "nonOwnerUser";

    LibrarySet librarySet =
        LibrarySet.builder().librarySetId("librarySetId").owner("actualOwner").build();

    CqlLibrary library =
        CqlLibrary.builder()
            .id(libraryId)
            .librarySetId("librarySetId")
            .librarySet(librarySet)
            .build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId(anyString())).thenReturn(librarySet);
    when(cqlLibraryAccessControlService.hasAdminRole(eq(conductedBy), eq(ACCESSTOKEN)))
        .thenReturn(false);
    doThrow(new PermissionDeniedException("CQL Library", libraryId, conductedBy))
        .when(cqlLibraryAccessControlService)
        .checkOwnership(any(CqlLibrary.class), eq(conductedBy));

    List<String> failedLibraries =
        cqlLibraryService.transferLibraries(
            List.of(libraryId), harpId, true, conductedBy, ACCESSTOKEN);

    assertEquals(1, failedLibraries.size());
    assertTrue(failedLibraries.contains(libraryId));

    verify(cqlLibraryService).findCqlLibraryById(libraryId, harpId);
    verify(librarySetService, never())
        .updateOwnership(anyString(), anyString(), anyBoolean(), anyString(), anyBoolean());
  }

  @Test
  public void testTransferLibrariesWhenGetUserRolesReturnsNullRoles() {
    String libraryId = "libraryId";
    String harpId = "user123";
    String conductedBy = "nonOwner";

    LibrarySet librarySet =
        LibrarySet.builder().librarySetId("librarySetId").owner("actualOwner").build();

    CqlLibrary library =
        CqlLibrary.builder()
            .id(libraryId)
            .librarySetId("librarySetId")
            .librarySet(librarySet)
            .build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId(anyString())).thenReturn(librarySet);
    when(cqlLibraryAccessControlService.hasAdminRole(eq(conductedBy), eq(ACCESSTOKEN)))
        .thenReturn(false);
    doThrow(new PermissionDeniedException("CQL Library", libraryId, conductedBy))
        .when(cqlLibraryAccessControlService)
        .checkOwnership(any(CqlLibrary.class), eq(conductedBy));

    List<String> failedLibraries =
        cqlLibraryService.transferLibraries(
            List.of(libraryId), harpId, true, conductedBy, ACCESSTOKEN);

    assertEquals(1, failedLibraries.size());
    assertTrue(failedLibraries.contains(libraryId));

    verify(cqlLibraryService).findCqlLibraryById(libraryId, harpId);
    verify(librarySetService, never())
        .updateOwnership(anyString(), anyString(), anyBoolean(), anyString(), anyBoolean());
  }

  @Test
  void testGetUserDetailsWhenNoUserDetailsFound() {
    LibrarySet librarySet1 = LibrarySet.builder().owner("owner1").build();
    LibrarySet librarySet2 = LibrarySet.builder().owner("owner2").build();
    LibraryListDTO library1 =
        LibraryListDTO.builder()
            .id("L1")
            .librarySet(librarySet1)
            .reviewers(List.of("reviewer1", "reviewer2"))
            .build();
    LibraryListDTO library2 =
        LibraryListDTO.builder()
            .id("L2")
            .librarySet(librarySet2)
            .reviewers(List.of("reviewer3"))
            .build();
    List<LibraryListDTO> libraries = List.of(library1, library2);
    Page<LibraryListDTO> librariesPage = new PageImpl<>(libraries);

    LibrarySearchCriteria criteria = new LibrarySearchCriteria();
    OwnershipType ownershipType = OwnershipType.OWNED;
    Pageable pageable = PageRequest.of(0, 10);
    String username = "testUser";

    when(cqlLibraryRepository.searchLibrariesByCriteria(
            username, pageable, criteria, ownershipType))
        .thenReturn(librariesPage);
    when(userServiceClient.getBulkUserDetails(anyList())).thenReturn(Collections.emptyMap());

    Page<LibraryListDTO> result =
        cqlLibraryService.getLibrariesByCriteria(criteria, ownershipType, pageable, username);

    assertEquals("-", result.getContent().get(0).getOwnerDisplayName());
    assertEquals("-", result.getContent().get(1).getOwnerDisplayName());
    assertEquals(List.of("reviewer1", "reviewer2"), result.getContent().get(0).getReviewers());
    assertEquals(List.of("reviewer3"), result.getContent().get(1).getReviewers());
    verify(userServiceClient, times(1))
        .getBulkUserDetails(List.of("owner1", "owner2", "reviewer1", "reviewer2", "reviewer3"));
  }

  @Test
  void testGetUserDetailsWhenUserDetailsFound() {
    LibrarySet librarySet1 = LibrarySet.builder().owner("owner1").build();
    LibrarySet librarySet2 = LibrarySet.builder().owner("owner2").build();
    LibrarySet librarySet3 = LibrarySet.builder().owner("owner3").build();
    LibrarySet librarySet4 = LibrarySet.builder().owner("owner4").build();
    LibrarySet librarySet5 = LibrarySet.builder().owner("owner5").build();
    LibraryListDTO library1 =
        LibraryListDTO.builder()
            .id("L1")
            .librarySet(librarySet1)
            .reviewers(List.of("reviewer1"))
            .build();
    LibraryListDTO library2 =
        LibraryListDTO.builder()
            .id("L2")
            .librarySet(librarySet2)
            .reviewers(List.of("reviewer2"))
            .build();
    LibraryListDTO library3 =
        LibraryListDTO.builder()
            .id("L3")
            .librarySet(librarySet3)
            .reviewers(List.of("reviewer3"))
            .build();
    LibraryListDTO library4 =
        LibraryListDTO.builder()
            .id("L4")
            .librarySet(librarySet4)
            .reviewers(List.of("reviewer4"))
            .build();
    LibraryListDTO library5 =
        LibraryListDTO.builder()
            .id("L5")
            .librarySet(librarySet5)
            .reviewers(List.of("reviewer5"))
            .build();
    List<LibraryListDTO> libraries = List.of(library1, library2, library3, library4, library5);
    Page<LibraryListDTO> librariesPage = new PageImpl<>(libraries);

    LibrarySearchCriteria criteria = new LibrarySearchCriteria();
    OwnershipType ownershipType = OwnershipType.OWNED;
    Pageable pageable = PageRequest.of(0, 10);
    String username = "testUser";

    UserDetailsDto user1 = UserDetailsDto.builder().firstName("John").lastName("Doe").build();
    UserDetailsDto user2 = UserDetailsDto.builder().firstName("").lastName("").build();
    UserDetailsDto user3 = UserDetailsDto.builder().firstName(null).lastName(null).build();
    UserDetailsDto user4 = UserDetailsDto.builder().firstName(null).lastName("Doe").build();
    UserDetailsDto user5 = UserDetailsDto.builder().firstName("Jane").lastName(null).build();
    Map<String, UserDetailsDto> userDetailsMap =
        Map.of("owner1", user1, "owner2", user2, "owner3", user3, "owner4", user4, "owner5", user5);

    when(cqlLibraryRepository.searchLibrariesByCriteria(
            username, pageable, criteria, ownershipType))
        .thenReturn(librariesPage);
    when(userServiceClient.getBulkUserDetails(anyList())).thenReturn(userDetailsMap);

    Page<LibraryListDTO> result =
        cqlLibraryService.getLibrariesByCriteria(criteria, ownershipType, pageable, username);

    assertEquals("John Doe", result.getContent().get(0).getOwnerDisplayName());
    assertEquals("owner2", result.getContent().get(1).getOwnerDisplayName());
    assertEquals("owner3", result.getContent().get(2).getOwnerDisplayName());
    assertEquals("Doe", result.getContent().get(3).getOwnerDisplayName());
    assertEquals("Jane", result.getContent().get(4).getOwnerDisplayName());
    assertEquals(List.of("reviewer1"), result.getContent().get(0).getReviewers());
    assertEquals(List.of("reviewer2"), result.getContent().get(1).getReviewers());
    assertEquals(List.of("reviewer3"), result.getContent().get(2).getReviewers());
    assertEquals(List.of("reviewer4"), result.getContent().get(3).getReviewers());
    assertEquals(List.of("reviewer5"), result.getContent().get(4).getReviewers());

    verify(userServiceClient, times(1))
        .getBulkUserDetails(
            List.of(
                "owner1",
                "owner2",
                "owner3",
                "owner4",
                "owner5",
                "reviewer1",
                "reviewer2",
                "reviewer3",
                "reviewer4",
                "reviewer5"));
  }

  @Test
  public void testUpdateCqlLibraryWhenUserNameIsNull() {
    CqlLibrary library = CqlLibrary.builder().id("libraryId").build();

    Exception exception =
        assertThrows(
            BadRequestObjectException.class,
            () -> cqlLibraryService.updateCqlLibrary(library, null));

    assertThat(exception.getMessage(), is(equalTo("Harp id cannot be null or empty.")));
  }

  @Test
  public void testGetVersionedCqlLibraryElmJsonHasErrors() {
    String cqlLibraryId = "libraryId";
    String cqlLibraryName = "testLibrary";
    String librarySetId = "librarySetId";
    CqlLibrary library =
        CqlLibrary.builder()
            .id(cqlLibraryId)
            .cqlLibraryName(cqlLibraryName)
            .librarySetId(librarySetId)
            .version(Version.parse("1.0.000"))
            .cql("library test version '1.0.0'")
            .model("QI-Core v4.1.1")
            .build();

    // Mock repository to return a non-empty list
    when(cqlLibraryRepository.findAllByCqlLibraryNameAndDraftAndVersionAndModel(
            anyString(), anyBoolean(), any(), anyString()))
        .thenReturn(List.of(library));
    ElmJson elmJson = ElmJson.builder().json("{}").build();
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(elmJson);
    when(elmTranslatorClient.hasErrors(any())).thenReturn(true);
    assertThrows(
        CqlElmTranslationErrorException.class,
        () ->
            cqlLibraryService.getVersionedCqlLibrary(
                cqlLibraryName,
                "1.0.000",
                Optional.of("QI-Core v4.1.1"),
                Optional.empty(),
                Optional.empty(),
                true,
                "Info",
                "test-okta"));
  }

  @Test
  public void testDeleteDraftLibraryLockInfoNotLocked() {
    LockInfo lockInfo =
        LockInfo.builder().isLocked(false).lockedBy("anyUser").lockedId("LibID").build();
    when(cqlLibraryLockService.lockCqlLibrary(eq("LibID"), eq("TEST_USER"))).thenReturn(lockInfo);
    CqlLibrary library =
        CqlLibrary.builder()
            .draft(true)
            .id("LibID")
            .librarySetId("LibSetID")
            .version(Version.parse("1.0.0"))
            .build();
    when(cqlLibraryRepository.findById("LibID")).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId("LibSetID"))
        .thenReturn(LibrarySet.builder().librarySetId("LibSetID").owner("TEST_USER").build());
    doNothing().when(cqlLibraryRepository).delete(any(CqlLibrary.class));

    CqlLibrary output = cqlLibraryService.deleteDraftLibrary("LibID", "TEST_USER", "ACCESSTOKEN");

    assertThat(output, is(notNullValue()));
    assertThat(output.getId(), is(equalTo("LibID")));
    verify(cqlLibraryRepository, times(1)).delete(eq(library));
  }

  @Test
  public void testRefreshIncludedLibrariesUpdatedAndPersistedLibraryCqlSame() {
    CqlLibrary updatedLibrary =
        CqlLibrary.builder()
            .id("LibID")
            .librarySetId("LibSetID")
            .cqlLibraryName("name1")
            .version(Version.parse("1.0.0"))
            .cql("library test version '1.0.0'")
            .build();
    CqlLibrary persistedLibrary =
        CqlLibrary.builder()
            .id("LibID")
            .librarySetId("LibSetID")
            .cqlLibraryName("name2")
            .version(Version.parse("1.0.0"))
            .cql("library test version '1.0.0'")
            .build();

    ReflectionTestUtils.invokeMethod(
        cqlLibraryService, "refreshIncludedLibraries", updatedLibrary, persistedLibrary);
    assertNull(updatedLibrary.getIncludedLibraries());
  }

  @Test
  public void testEnsureUniqueNameCqlLibraryNameNotChanged() {
    CqlLibrary updatingLibrary =
        CqlLibrary.builder()
            .id("LibID")
            .librarySetId("LibSetID")
            .cqlLibraryName("name1")
            .version(Version.parse("1.0.0"))
            .build();
    CqlLibrary persistedLibrary =
        CqlLibrary.builder()
            .id("LibID")
            .librarySetId("LibSetID")
            .cqlLibraryName("name1")
            .version(Version.parse("1.0.0"))
            .build();

    ReflectionTestUtils.invokeMethod(
        cqlLibraryService, "ensureUniqueName", updatingLibrary, persistedLibrary);
    // Assert that the name remains unchanged
    assertEquals("name1", updatingLibrary.getCqlLibraryName());
    assertEquals("name1", persistedLibrary.getCqlLibraryName());
  }

  @Test
  public void testCheckDuplicateCqlLibraryNameLibraryNameNull() {
    assertDoesNotThrow(() -> cqlLibraryService.checkDuplicateCqlLibraryName(null));
  }

  @Test
  public void testGetVersionedCqlLibraryFetchElmFalse() {
    String cqlLibraryName = "testLibrary";
    String version = "1.0.000";
    String model = "QI-Core v4.1.1";
    CqlLibrary library =
        CqlLibrary.builder()
            .id("libraryId")
            .cqlLibraryName(cqlLibraryName)
            .version(Version.parse(version))
            .model(model)
            .build();
    when(cqlLibraryRepository.findAllByCqlLibraryNameAndDraftAndVersionAndModel(
            anyString(), anyBoolean(), any(), anyString()))
        .thenReturn(List.of(library));

    CqlLibraryDto result =
        cqlLibraryService.getVersionedCqlLibrary(
            cqlLibraryName,
            version,
            Optional.of(model),
            Optional.empty(),
            Optional.empty(),
            false,
            "Info",
            "test-okta");

    assertNotNull(result);
    assertEquals(cqlLibraryName, result.getCqlLibraryName());
    assertEquals(version, result.getVersion());
    assertEquals(model, result.getModel());
    verify(elmTranslatorClient, times(0))
        .getElmJson(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  public void testGetReviewLibrariesReturnsEnrichedList() {
    Map<String, CqlLibraryReview> reviewByLibraryId =
        Map.of(
            "lib-1",
            CqlLibraryReview.builder()
                .libraryId("lib-1")
                .status(ReviewStatus.READY_FOR_REVIEW)
                .reviewers(List.of("reviewer-1", "reviewer-2"))
                .build());

    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner("owner-1");
    CqlLibrary library =
        CqlLibrary.builder()
            .id("lib-1")
            .librarySetId("set-1")
            .cqlLibraryName("Lib1")
            .model("QI-Core v4.1.1")
            .draft(true)
            .build();
    when(cqlLibraryRepository.findByIdIn(anySet())).thenReturn(List.of(library));
    when(librarySetService.findByLibrarySetId("set-1")).thenReturn(librarySet);

    UserDetailsDto ownerDetails = new UserDetailsDto();
    ownerDetails.setFirstName("Jane");
    ownerDetails.setLastName("Doe");
    UserDetailsDto reviewerOneDetails = new UserDetailsDto();
    reviewerOneDetails.setFirstName("Alex");
    reviewerOneDetails.setLastName("Smith");
    UserDetailsDto reviewerTwoDetails = new UserDetailsDto();
    reviewerTwoDetails.setFirstName("OnlyFirst");
    when(userServiceClient.getBulkUserDetails(anyList()))
        .thenReturn(
            Map.of(
                "owner-1", ownerDetails,
                "reviewer-1", reviewerOneDetails,
                "reviewer-2", reviewerTwoDetails));

    List<LibraryListDTO> result = cqlLibraryService.getReviewLibraries(reviewByLibraryId);

    assertNotNull(result);
    assertEquals(1, result.size());
    LibraryListDTO dto = result.get(0);
    assertEquals("lib-1", dto.getId());
    assertEquals("Lib1", dto.getCqlLibraryName());
    assertEquals("QI-Core v4.1.1", dto.getModel());
    assertTrue(dto.isDraft());
    assertEquals("Ready", dto.getReviewStatus());
    assertEquals(List.of("Alex Smith", "reviewer-2"), dto.getReviewers());
    assertEquals(librarySet, dto.getLibrarySet());
    assertEquals("Jane Doe", dto.getOwnerDisplayName());
  }

  @Test
  public void testGetReviewLibrariesLabelsEveryInReviewStatus() {
    Map<String, CqlLibraryReview> reviewByLibraryId =
        Map.of(
            "lib-1",
                CqlLibraryReview.builder()
                    .libraryId("lib-1")
                    .status(ReviewStatus.READY_FOR_REVIEW)
                    .build(),
            "lib-2",
                CqlLibraryReview.builder()
                    .libraryId("lib-2")
                    .status(ReviewStatus.IN_PROGRESS)
                    .build(),
            "lib-3",
                CqlLibraryReview.builder()
                    .libraryId("lib-3")
                    .status(ReviewStatus.COMPLETE)
                    .build());

    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner("owner-1");
    List<CqlLibrary> libraries =
        List.of(
            CqlLibrary.builder().id("lib-1").librarySetId("set-1").cqlLibraryName("Lib1").build(),
            CqlLibrary.builder().id("lib-2").librarySetId("set-1").cqlLibraryName("Lib2").build(),
            CqlLibrary.builder().id("lib-3").librarySetId("set-1").cqlLibraryName("Lib3").build());
    when(cqlLibraryRepository.findByIdIn(anySet())).thenReturn(libraries);
    when(librarySetService.findByLibrarySetId("set-1")).thenReturn(librarySet);
    when(userServiceClient.getBulkUserDetails(anyList())).thenReturn(Map.of());

    Map<String, String> statusById =
        cqlLibraryService.getReviewLibraries(reviewByLibraryId).stream()
            .collect(Collectors.toMap(LibraryListDTO::getId, LibraryListDTO::getReviewStatus));

    assertEquals("Ready", statusById.get("lib-1"));
    assertEquals("In Progress", statusById.get("lib-2"));
    assertEquals("Complete", statusById.get("lib-3"));
  }

  @Test
  public void testGetReviewLibrariesReturnsEmptyListForEmptyMap() {
    List<LibraryListDTO> result = cqlLibraryService.getReviewLibraries(Map.of());

    assertNotNull(result);
    assertTrue(result.isEmpty());
    verify(cqlLibraryRepository, never()).findByIdIn(anySet());
  }

  @Test
  public void testDeleteDraftLibraryWithAdminUserNonOwner() {
    CqlLibrary library =
        CqlLibrary.builder()
            .draft(true)
            .id("LibID")
            .librarySetId("LibSetID")
            .version(Version.parse("1.0.0"))
            .build();

    when(cqlLibraryRepository.findById("LibID")).thenReturn(Optional.of(library));

    when(librarySetService.findByLibrarySetId("LibSetID"))
        .thenReturn(LibrarySet.builder().librarySetId("LibSetID").owner("SOME_OTHER_USER").build());

    when(cqlLibraryAccessControlService.hasAdminRole(eq("TEST_USER"), eq("ACCESSTOKEN")))
        .thenReturn(true);

    doNothing().when(cqlLibraryRepository).delete(any(CqlLibrary.class));

    CqlLibrary result = cqlLibraryService.deleteDraftLibrary("LibID", "TEST_USER", "ACCESSTOKEN");

    assertNotNull(result);
    assertEquals("LibID", result.getId());

    verify(cqlLibraryAccessControlService).hasAdminRole("TEST_USER", "ACCESSTOKEN");
    verify(cqlLibraryRepository).delete(library);
    verify(cqlLibraryLockService).unlockCqlLibrary("LibID", "TEST_USER");
  }
}

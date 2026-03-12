package gov.cms.madie.cqllibraryservice.services;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import gov.cms.madie.cqllibraryservice.dto.LibrarySearchCriteria;
import gov.cms.madie.cqllibraryservice.dto.LibrarySetDTO;
import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.dto.SharedUser;
import gov.cms.madie.cqllibraryservice.exceptions.*;
import gov.cms.madie.cqllibraryservice.locks.CqlLibraryLock;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.*;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.dto.UserRolesDto;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.models.library.LibrarySet;
import gov.cms.madie.models.measure.ElmJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

import gov.cms.madie.cqllibraryservice.dto.LockInfo;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CqlLibraryServiceTest {

  @Spy @InjectMocks private CqlLibraryService cqlLibraryService;
  @Mock private CqlLibraryRepository cqlLibraryRepository;
  @Mock private LibrarySetService librarySetService;
  @Mock private MeasureServiceClient measureServiceClient;
  @Mock private LibrarySetRepository librarySetRepository;
  @Mock private ElmTranslatorClient elmTranslatorClient;
  @Mock private ActionLogService actionLogService;
  @Mock private AppConfigService appConfigService;
  @Mock private CqlLibraryLockService cqlLibraryLockService;

  @Mock private UserServiceClient userServiceClient;

  private final String USERNAME = "testUserName";

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
        .thenReturn(ElmJson.builder().json("{\"library\": {}}").build());
    CqlLibrary versionedCqlLibrary =
        cqlLibraryService.getVersionedCqlLibrary(
            "TestFHIRHelpers", "1.0.000", Optional.of("QI-Core v4.1.1"), true, "Info", "test-okta");
    assertNotNull(versionedCqlLibrary);
    assertEquals(cqlLibrary1.getCqlLibraryName(), versionedCqlLibrary.getCqlLibraryName());
    assertEquals(cqlLibrary1.getVersion(), versionedCqlLibrary.getVersion());
    assertEquals(cqlLibrary1.getModel(), versionedCqlLibrary.getModel());
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
    CqlLibrary versionedCqlLibrary =
        cqlLibraryService.getVersionedCqlLibrary(
            "TestFHIRHelpers", "1.0.000", Optional.empty(), true, "Info", "test-okta");
    assertNotNull(versionedCqlLibrary);
    assertEquals(cqlLibrary.getCqlLibraryName(), versionedCqlLibrary.getCqlLibraryName());
    assertEquals(cqlLibrary.getVersion(), versionedCqlLibrary.getVersion());
    assertEquals(cqlLibrary.getModel(), versionedCqlLibrary.getModel());
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
                "TestFHIRHelpers", "1.0.000", Optional.empty(), true, "Info", "test-okta"));
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
                "TestFHIRHelpers", "1.0.000", Optional.empty(), true, "Info", "test-okta"));
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

    UserRolesDto userRolesDto =
        UserRolesDto.builder().harpId("owner").roles(List.of("MADiE-User")).build();

    when(cqlLibraryRepository.findById(libraryId)).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId("librarySetId")).thenReturn(librarySet);
    when(userServiceClient.getUserRoles(eq("owner"), eq("accessToken"))).thenReturn(userRolesDto);
    when(librarySetService.updateOwnership(
            anyString(), anyString(), anyBoolean(), anyString(), anyBoolean()))
        .thenReturn(new LibrarySet());

    List<String> failedLibraries =
        cqlLibraryService.transferLibraries(List.of(libraryId), user, true, "owner", "accessToken");

    assertTrue(failedLibraries.isEmpty());

    verify(userServiceClient).getUserRoles("owner", "accessToken");
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

    UserRolesDto userRolesDto =
        UserRolesDto.builder().harpId(user).roles(List.of("MADiE-User")).build();

    when(cqlLibraryRepository.findById(libraryId)).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId("librarySetId")).thenReturn(librarySet);
    when(userServiceClient.getUserRoles(eq(user), eq("accessToken"))).thenReturn(userRolesDto);
    when(librarySetService.updateOwnership(
            anyString(), anyString(), anyBoolean(), anyString(), anyBoolean()))
        .thenThrow(new ResourceNotFoundException("LibrarySet", "id", "librarySetId"));

    List<String> failedLibraries =
        cqlLibraryService.transferLibraries(List.of(libraryId), user, true, user, "accessToken");

    assertEquals(1, failedLibraries.size());
    assertTrue(failedLibraries.contains(libraryId));

    verify(userServiceClient).getUserRoles(user, "accessToken");
    verify(cqlLibraryService).findCqlLibraryById(libraryId, user);
    verify(librarySetService)
        .updateOwnership(eq("librarySetId"), eq(user), eq(true), eq(user), eq(false));
  }

  @Test
  public void testDeleteDraftLibraryWithIdNotFound() {
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> cqlLibraryService.deleteDraftLibrary("MISSING", "TEST_USER"));
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
        () -> cqlLibraryService.deleteDraftLibrary("LibID", "TEST_USER"));
  }

  @Test
  public void testDeleteDraftLibraryWithDraftLibraryNonOwner() {
    CqlLibrary library =
        CqlLibrary.builder()
            .draft(true)
            .id("LibID")
            .librarySetId("LibSetID")
            .version(Version.parse("1.0.0"))
            .build();
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId(anyString()))
        .thenReturn(LibrarySet.builder().librarySetId("LibSetID").owner("SOME_OTHER_USER").build());

    assertThrows(
        PermissionDeniedException.class,
        () -> cqlLibraryService.deleteDraftLibrary("LibID", "TEST_USER"));
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

    CqlLibrary output = cqlLibraryService.deleteDraftLibrary("LibID", "TEST_USER");

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
        () -> cqlLibraryService.deleteDraftLibrary("LibID", "TEST_USER"));
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

    CqlLibrary output = cqlLibraryService.deleteDraftLibrary("LibID", "TEST_USER");

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
                "Response could not be completed because the HARP id of owner1 passed in does not match the owner of the library with the library id of libraryId. The owner of the library is owner2")));

    verify(cqlLibraryRepository, times(0)).deleteAll(List.of(cqlLibrary));
  }

  @Test
  void testFindLibrariesByNameAndModel() {
    String libraryName = "test";
    String model = "QICore 4.1.1";
    LibraryListDTO l1 =
        LibraryListDTO.builder()
            .cqlLibraryName("L1")
            .version(Version.parse("0.1.000"))
            .model("QICore 4.1.1")
            .build();
    when(cqlLibraryRepository.findLibrariesByNameAndModelOrderByNameAscAndVersionDsc(
            anyString(), anyString()))
        .thenReturn(List.of(l1));
    List<LibraryListDTO> result = cqlLibraryService.findLibrariesByNameAndModel(libraryName, model);
    assertThat(result.size(), equalTo(1));
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
  public void testGetSharedLibrariesWithNoLibraryFound() {
    String libraryId1 = "libraryId1";
    List<String> libraryIds = List.of(libraryId1);

    when(cqlLibraryRepository.findById(libraryId1)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> cqlLibraryService.getSharedLibraries(libraryIds, USERNAME));
  }

  @Test
  public void testGetSharedLibrariesWithNoLibrarySetFound() {
    CqlLibrary library1 =
        CqlLibrary.builder().id("libraryId1").librarySetId("librarySetId1").build();

    when(cqlLibraryRepository.findById("libraryId1")).thenReturn(Optional.ofNullable(library1));

    assertThrows(
        ResourceNotFoundException.class,
        () -> cqlLibraryService.getSharedLibraries(List.of("libraryId1"), USERNAME));
  }

  @Test
  public void testGetSharedLibrariesWithNoLibrarySetAclsFoundForOneLibrary() {
    AclSpecification aclSpec = new AclSpecification();
    aclSpec.setUserId("john");
    aclSpec.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });

    LibrarySet librarySet1 =
        LibrarySet.builder()
            .librarySetId("librarySetId1")
            .owner("testUser")
            .acls(
                new ArrayList<>() {
                  {
                    add(aclSpec);
                  }
                })
            .build();

    String libraryId1 = "libraryId1";
    CqlLibrary library1 =
        CqlLibrary.builder().id("libraryId1").librarySetId("librarySetId1").build();

    LibrarySet librarySet2 =
        LibrarySet.builder().librarySetId("librarySetId1").owner("testUser").build();

    String libraryId2 = "libraryId2";
    CqlLibrary library2 =
        CqlLibrary.builder()
            .id(libraryId1)
            .librarySetId(librarySet1.getLibrarySetId())
            .librarySet(librarySet2)
            .build();

    List<String> libraryIds = List.of(libraryId1, libraryId2);

    when(cqlLibraryRepository.findById("libraryId1")).thenReturn(Optional.ofNullable(library1));
    when(librarySetService.findByLibrarySetId("librarySetId1")).thenReturn(librarySet1);
    when(cqlLibraryRepository.findById("libraryId2")).thenReturn(Optional.ofNullable(library2));

    Map<String, List<SharedUser>> sharedLibraries =
        cqlLibraryService.getSharedLibraries(libraryIds, USERNAME);

    assertThat(sharedLibraries.size(), is(equalTo(2)));

    assertTrue(sharedLibraries.containsKey(libraryId1));
    assertThat(sharedLibraries.get(libraryId1).size(), is(equalTo(1)));
    assertThat(
        sharedLibraries.get(libraryId1).get(0).getUserId(),
        is(equalTo(library1.getLibrarySet().getAcls().get(0).getUserId())));

    assertTrue(sharedLibraries.containsKey(libraryId2));
    assertThat(sharedLibraries.get(libraryId2).size(), is(equalTo(1)));
  }

  @Test
  public void testGetSharedLibraries() {
    AclSpecification aclSpec1 = new AclSpecification();
    aclSpec1.setUserId("userId1");
    aclSpec1.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });

    AclSpecification aclSpec2 = new AclSpecification();
    aclSpec2.setUserId("userId2");
    aclSpec2.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });

    LibrarySet librarySet2 =
        LibrarySet.builder()
            .librarySetId("librarySetId1")
            .owner("testUser")
            .acls(
                new ArrayList<>() {
                  {
                    add(aclSpec1);
                  }
                })
            .build();

    LibrarySet librarySet1 =
        LibrarySet.builder()
            .librarySetId("librarySetId1")
            .owner("testUser")
            .acls(List.of(aclSpec2, aclSpec1))
            .build();

    String libraryId1 = "libraryId1";
    CqlLibrary library1 =
        CqlLibrary.builder()
            .id(libraryId1)
            .librarySetId(librarySet1.getLibrarySetId())
            .librarySet(librarySet1)
            .build();

    String libraryId2 = "libraryId2";
    CqlLibrary library2 =
        CqlLibrary.builder()
            .id(libraryId1)
            .librarySetId(librarySet1.getLibrarySetId())
            .librarySet(librarySet2)
            .build();

    Instant fixedInstant = Instant.parse("2025-03-17T10:00:00Z");
    ZoneId utc = ZoneId.of("UTC");
    Clock fixedClock = Clock.fixed(fixedInstant, utc);

    LibrarySetActionLog librarySetActionLog =
        LibrarySetActionLog.builder()
            .actions(
                List.of(
                    AccessControlAction.builder()
                        .sharedWith(aclSpec1.getUserId())
                        .actionType(ActionType.SHARED)
                        .performedAt(fixedClock.instant())
                        .performedBy("performedByUserId")
                        .build()))
            .build();

    List<String> libraryIds = List.of(libraryId1, libraryId2);

    when(cqlLibraryRepository.findById("libraryId1")).thenReturn(Optional.ofNullable(library1));
    when(librarySetService.findByLibrarySetId("librarySetId1")).thenReturn(librarySet1);
    when(cqlLibraryRepository.findById("libraryId2")).thenReturn(Optional.ofNullable(library2));
    when(actionLogService.findLibrarySetActionLogByTargetId(anyString()))
        .thenReturn(librarySetActionLog);

    Map<String, List<SharedUser>> sharedLibraries =
        cqlLibraryService.getSharedLibraries(libraryIds, USERNAME);

    assertThat(sharedLibraries.size(), is(equalTo(2)));

    assertTrue(sharedLibraries.containsKey(libraryId1));
    assertThat(sharedLibraries.get(libraryId1).size(), is(equalTo(2)));
    assertThat(
        sharedLibraries.get(libraryId1).get(0).getUserId(),
        is(equalTo(library1.getLibrarySet().getAcls().get(0).getUserId())));
    assertThat(sharedLibraries.get(libraryId1).get(0).getPerformedAt(), is(equalTo(null)));
    assertThat(sharedLibraries.get(libraryId1).get(1).getUserId(), is(equalTo("userId1")));

    assertTrue(sharedLibraries.containsKey(libraryId2));
    assertThat(sharedLibraries.get(libraryId1).size(), is(equalTo(2)));
    assertThat(
        sharedLibraries.get(libraryId2).get(0).getUserId(),
        is(equalTo(library2.getLibrarySet().getAcls().get(0).getUserId())));
  }

  @Test
  public void testUpdateSharedLibrariesWithNoLibraryFound() {
    Map<String, List<String>> libraries = new HashMap<>();

    String libraryId1 = "libraryId1";
    String libraryId2 = "libraryId2";

    libraries.put(libraryId1, List.of("userId1", "userId2"));
    libraries.put(libraryId2, List.of("userId2"));

    when(cqlLibraryRepository.findById(eq(libraryId1))).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> cqlLibraryService.shareLibraries(libraries, "userName"));
  }

  @Test
  public void testUpdateSharedLibraries() {
    Map<String, List<String>> libraries = new HashMap<>();

    AclSpecification aclSpec1 = new AclSpecification();
    aclSpec1.setUserId("testUser");
    aclSpec1.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });

    AclSpecification aclSpec2 = new AclSpecification();
    aclSpec2.setUserId("userId2");
    aclSpec2.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });

    LibrarySet librarySet1 =
        LibrarySet.builder()
            .librarySetId("librarySetId1")
            .owner("testUser")
            .acls(List.of(aclSpec2, aclSpec1))
            .build();

    LibrarySet librarySet2 =
        LibrarySet.builder()
            .librarySetId("librarySetId1")
            .owner("testUser")
            .acls(List.of(aclSpec2, aclSpec1))
            .build();

    String libraryId1 = "libraryId1";

    CqlLibrary library1 =
        CqlLibrary.builder()
            .id(libraryId1)
            .librarySetId(librarySet1.getLibrarySetId())
            .librarySet(librarySet1)
            .build();

    String libraryId2 = "libraryId2";
    CqlLibrary library2 =
        CqlLibrary.builder()
            .id(libraryId1)
            .librarySetId(librarySet1.getLibrarySetId())
            .librarySet(librarySet2)
            .build();

    libraries.put(libraryId1, List.of("testUser", "userId2"));
    libraries.put(libraryId2, List.of("userId2"));

    when(cqlLibraryRepository.findById("libraryId1")).thenReturn(Optional.ofNullable(library1));
    when(librarySetService.findByLibrarySetId("librarySetId1")).thenReturn(librarySet1);
    when(cqlLibraryRepository.findById("libraryId2")).thenReturn(Optional.ofNullable(library2));

    when(librarySetService.updateLibrarySetAcls(any(), any(), any())).thenReturn(librarySet1);

    AclSpecification aclSpecification1 =
        AclSpecification.builder().userId("testUser").roles(Set.of(RoleEnum.SHARED_WITH)).build();
    AclSpecification aclSpecification2 =
        AclSpecification.builder().userId("userId2").roles(Set.of(RoleEnum.SHARED_WITH)).build();

    Map<String, List<AclSpecification>> updatedSharedLibraries =
        cqlLibraryService.shareLibraries(libraries, "testUser");
    assertThat(updatedSharedLibraries.size(), is(equalTo(2)));

    assertTrue(updatedSharedLibraries.containsKey(libraryId1));
    assertTrue(updatedSharedLibraries.containsKey(libraryId2));

    assertThat(
        updatedSharedLibraries.get(libraryId1),
        is(equalTo(List.of(aclSpecification2, aclSpecification1))));

    assertThat(
        updatedSharedLibraries.get(libraryId2),
        is(equalTo(List.of(aclSpecification2, aclSpecification1))));
  }

  @Test
  public void testThrowingUnauthorizedErrorWhenLibrarySetHaveNoAcls() {
    LibrarySet librarySet1 = LibrarySet.builder().owner("test").build();
    assertThrows(
        UnauthorizedException.class,
        () ->
            cqlLibraryService.verifyLibrarySetAuthorization(
                "testUser", "test", "targetId", null, librarySet1));
  }

  @Test
  public void testThrowingUnauthorizedErrorWhenLibrarySetHaveAclsWIthNotCorrectOwner() {
    AclSpecification aclSpec1 = new AclSpecification();
    aclSpec1.setUserId("testUser1");
    aclSpec1.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });

    LibrarySet librarySet1 =
        LibrarySet.builder()
            .librarySetId("librarySetId1")
            .owner("testUser")
            .acls(List.of(aclSpec1))
            .build();

    assertThrows(
        UnauthorizedException.class,
        () ->
            cqlLibraryService.verifyLibrarySetAuthorization(
                "testUser2", "test", "targetId", null, librarySet1));
  }

  @Test
  public void testThrowReourceNotFoundWhenVerifyingAuthorization() {
    when(librarySetService.findByLibrarySetId(anyString())).thenReturn(null);

    CqlLibrary lib1 = CqlLibrary.builder().cqlLibraryName("Lib1").librarySetId("LibSetId1").build();
    assertThrows(
        ResourceNotFoundException.class,
        () -> cqlLibraryService.verifyAuthorization("testUser", lib1, null));
  }

  @Test
  public void testUnshareLibraries() {
    Map<String, List<String>> libraries = new HashMap<>();

    AclSpecification aclSpec1 = new AclSpecification();
    aclSpec1.setUserId("testUser");
    aclSpec1.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });

    LibrarySet librarySet1 =
        LibrarySet.builder()
            .librarySetId("librarySetId1")
            .owner("testUser")
            .acls(List.of(aclSpec1))
            .build();

    String libraryId1 = "libraryId1";

    CqlLibrary library1 =
        CqlLibrary.builder()
            .id(libraryId1)
            .librarySetId(librarySet1.getLibrarySetId())
            .librarySet(librarySet1)
            .build();

    libraries.put(libraryId1, List.of("testUser", "userId2"));

    when(cqlLibraryRepository.findById("libraryId1")).thenReturn(Optional.ofNullable(library1));
    when(librarySetService.findByLibrarySetId("librarySetId1")).thenReturn(librarySet1);

    when(librarySetService.updateLibrarySetAcls(any(), any(), any())).thenReturn(librarySet1);

    AclSpecification aclSpecification1 =
        AclSpecification.builder().userId("testUser").roles(Set.of(RoleEnum.SHARED_WITH)).build();

    Map<String, List<AclSpecification>> updatedSharedLibraries =
        cqlLibraryService.unshareLibraries(libraries, "testUser");
    assertThat(updatedSharedLibraries.size(), is(equalTo(1)));

    assertTrue(updatedSharedLibraries.containsKey(libraryId1));

    assertThat(updatedSharedLibraries.get(libraryId1), is(equalTo(List.of(aclSpecification1))));
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
        List.of(
            Action.builder().actionType(ActionType.CREATED).build(),
            Action.builder().actionType(ActionType.UPDATED).build());

    when(cqlLibraryRepository.findById(cqlLibraryId)).thenReturn(Optional.of(library));
    when(actionLogService.findCqlLibraryHistory(cqlLibraryId, librarySetId)).thenReturn(actions);

    List<Action> result = cqlLibraryService.getCqlLibraryHistory(cqlLibraryId, userName);

    assertThat(result.size(), is(equalTo(2)));
    assertThat(result.get(0).getActionType(), is(equalTo(ActionType.CREATED)));
    assertThat(result.get(1).getActionType(), is(equalTo(ActionType.UPDATED)));
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
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(false);
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
    assertEquals("John Doe", result.get(0).getOwner());
    verify(userServiceClient, times(1)).getSingleUserDetails(eq(ownerId));
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
    when(userServiceClient.getUserRoles(eq(conductedBy), eq("accessToken"))).thenReturn(null);
    when(librarySetService.updateOwnership(
            anyString(), anyString(), anyBoolean(), anyString(), anyBoolean()))
        .thenReturn(new LibrarySet());

    List<String> failedLibraries =
        cqlLibraryService.transferLibraries(
            List.of(libraryId), harpId, true, conductedBy, "accessToken");

    assertTrue(failedLibraries.isEmpty());

    verify(userServiceClient).getUserRoles(conductedBy, "accessToken");
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

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId(anyString())).thenReturn(librarySet);
    when(userServiceClient.getUserRoles(eq(conductedBy), eq("accessToken"))).thenReturn(null);

    List<String> failedLibraries =
        cqlLibraryService.transferLibraries(
            List.of(libraryId), harpId, true, conductedBy, "accessToken");

    assertEquals(1, failedLibraries.size());
    assertTrue(failedLibraries.contains(libraryId));

    verify(userServiceClient).getUserRoles(conductedBy, "accessToken");
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

    UserRolesDto userRolesDto =
        UserRolesDto.builder().harpId(conductedBy).roles(List.of("MADiE-Admin")).build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId(anyString())).thenReturn(librarySet);
    when(userServiceClient.getUserRoles(eq(conductedBy), eq("accessToken")))
        .thenReturn(userRolesDto);
    when(librarySetService.updateOwnership(
            anyString(), anyString(), anyBoolean(), anyString(), anyBoolean()))
        .thenReturn(new LibrarySet());

    List<String> failedLibraries =
        cqlLibraryService.transferLibraries(
            List.of(libraryId), harpId, true, conductedBy, "accessToken");

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

    UserRolesDto userRolesDto =
        UserRolesDto.builder().harpId(conductedBy).roles(List.of("MADiE-User")).build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId(anyString())).thenReturn(librarySet);
    when(userServiceClient.getUserRoles(eq(conductedBy), eq("accessToken")))
        .thenReturn(userRolesDto);

    List<String> failedLibraries =
        cqlLibraryService.transferLibraries(
            List.of(libraryId), harpId, true, conductedBy, "accessToken");

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

    UserRolesDto userRolesDto = UserRolesDto.builder().harpId(conductedBy).roles(null).build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId(anyString())).thenReturn(librarySet);
    when(userServiceClient.getUserRoles(eq(conductedBy), eq("accessToken")))
        .thenReturn(userRolesDto);

    List<String> failedLibraries =
        cqlLibraryService.transferLibraries(
            List.of(libraryId), harpId, true, conductedBy, "accessToken");

    assertEquals(1, failedLibraries.size());
    assertTrue(failedLibraries.contains(libraryId));

    verify(userServiceClient).getUserRoles(conductedBy, "accessToken");
    verify(cqlLibraryService).findCqlLibraryById(libraryId, harpId);
    verify(librarySetService, never())
        .updateOwnership(anyString(), anyString(), anyBoolean(), anyString(), anyBoolean());
  }

  @Test
  void testGetUserDetailsWhenNoUserDetailsFound() {
    LibrarySet librarySet1 = LibrarySet.builder().owner("owner1").build();
    LibrarySet librarySet2 = LibrarySet.builder().owner("owner2").build();
    LibraryListDTO library1 = LibraryListDTO.builder().id("L1").librarySet(librarySet1).build();
    LibraryListDTO library2 = LibraryListDTO.builder().id("L2").librarySet(librarySet2).build();
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

    assertEquals("-", result.getContent().get(0).getOwner());
    assertEquals("-", result.getContent().get(1).getOwner());
    verify(userServiceClient, times(1)).getBulkUserDetails(List.of("owner1", "owner2"));
  }

  @Test
  void testGetUserDetailsWhenUserDetailsFound() {
    LibrarySet librarySet1 = LibrarySet.builder().owner("owner1").build();
    LibrarySet librarySet2 = LibrarySet.builder().owner("owner2").build();
    LibrarySet librarySet3 = LibrarySet.builder().owner("owner3").build();
    LibrarySet librarySet4 = LibrarySet.builder().owner("owner4").build();
    LibrarySet librarySet5 = LibrarySet.builder().owner("owner5").build();
    LibraryListDTO library1 = LibraryListDTO.builder().id("L1").librarySet(librarySet1).build();
    LibraryListDTO library2 = LibraryListDTO.builder().id("L2").librarySet(librarySet2).build();
    LibraryListDTO library3 = LibraryListDTO.builder().id("L3").librarySet(librarySet3).build();
    LibraryListDTO library4 = LibraryListDTO.builder().id("L4").librarySet(librarySet4).build();
    LibraryListDTO library5 = LibraryListDTO.builder().id("L5").librarySet(librarySet5).build();
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

    assertEquals("John Doe", result.getContent().get(0).getOwner());
    assertEquals("owner2", result.getContent().get(1).getOwner());
    assertEquals("owner3", result.getContent().get(2).getOwner());
    assertEquals("Doe", result.getContent().get(3).getOwner());
    assertEquals("Jane", result.getContent().get(4).getOwner());

    verify(userServiceClient, times(1))
        .getBulkUserDetails(List.of("owner1", "owner2", "owner3", "owner4", "owner5"));
  }
}

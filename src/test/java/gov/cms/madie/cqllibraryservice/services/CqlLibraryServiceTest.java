package gov.cms.madie.cqllibraryservice.services;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import gov.cms.madie.cqllibraryservice.dto.LibrarySearchCriteria;
import gov.cms.madie.cqllibraryservice.dto.LibrarySetDTO;
import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.dto.SharedUser;
import gov.cms.madie.cqllibraryservice.exceptions.*;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.*;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.models.library.LibrarySet;
import gov.cms.madie.models.measure.ElmJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

@ExtendWith(MockitoExtension.class)
class CqlLibraryServiceTest {

  @InjectMocks private CqlLibraryService cqlLibraryService;
  @Mock private CqlLibraryRepository cqlLibraryRepository;
  @Mock private LibrarySetService librarySetService;
  @Mock private MeasureServiceClient measureServiceClient;
  @Mock private LibrarySetRepository librarySetRepository;
  @Mock private ElmTranslatorClient elmTranslatorClient;

  @Mock private ActionLogService actionLogService;

  @Test
  public void testGetOwnedLibrariesByCriteria() {
    var librarySearchCriteria =
        LibrarySearchCriteria.builder().searchField("measureSearchCriteria").build();
    PageRequest initialPage = PageRequest.of(0, 10);
    CqlLibrary lib1 = CqlLibrary.builder().build();

    Page<CqlLibrary> activeLibraries = new PageImpl<>(List.of(lib1));
    doReturn(activeLibraries)
        .when(cqlLibraryRepository)
        .searchLibrariesByCriteria(
            eq("test.user"), any(PageRequest.class), any(), eq(ViewScope.OWNED));
    Object libraries =
        cqlLibraryService.getLibrariesByCriteria(
            librarySearchCriteria, ViewScope.OWNED, initialPage, "test.user");
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

    CqlLibrary cqlLib = cqlLibraryService.findCqlLibraryById(id);
    assertEquals(cqlLib.getId(), id);
    assertNotNull(cqlLib.getLibrarySet());
  }

  @Test
  void testFindCqlLibraryByIdNotFound() {
    String id = "1";
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.empty());
    Exception ex =
        assertThrows(
            ResourceNotFoundException.class, () -> cqlLibraryService.findCqlLibraryById(id));
    assertEquals(ex.getMessage(), "Could not find resource CQL Library with id: " + id);
  }

  @Test
  public void testChangeOwnership() {
    LibrarySet librarySet = LibrarySet.builder().librarySetId("123").owner("testUser").build();
    CqlLibrary library =
        CqlLibrary.builder().id("123").librarySetId("123").librarySet(librarySet).build();
    Optional<CqlLibrary> persistedLibrary = Optional.of(library);
    when(cqlLibraryRepository.findById(anyString())).thenReturn(persistedLibrary);
    when(librarySetService.updateOwnership(anyString(), anyString())).thenReturn(new LibrarySet());

    boolean result = cqlLibraryService.changeOwnership(library.getId(), "user123");
    assertTrue(result);
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
        GeneralConflictException.class,
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
            () ->
                cqlLibraryService.deleteLibraryAlongWithVersions(
                    libraryName, "token", anyString()));
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
            () ->
                cqlLibraryService.deleteLibraryAlongWithVersions(
                    libraryName, "token", anyString()));
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
            () ->
                cqlLibraryService.deleteLibraryAlongWithVersions(
                    libraryName, "token", anyString()));
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
    when(cqlLibraryRepository.findLibrariesByLibrarySetId(eq(librarySetId), anyBoolean()))
        .thenReturn(List.of(l1));
    List<LibraryListDTO> results = cqlLibraryService.getLibrariesByLibrarySetId(librarySetId, true);
    assertEquals(1, results.size());
    assertThat(results.get(0).getId(), equalTo("L1"));
    assertThat(results.get(0).getLibrarySetId(), equalTo(librarySetId));
  }

  @Test
  void testGetLibrariesByLibrarySetIdThrowsBadRequestObjectException() {
    Exception exception =
        assertThrows(
            BadRequestObjectException.class,
            () -> cqlLibraryService.getLibrariesByLibrarySetId("", true));
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
        ResourceNotFoundException.class, () -> cqlLibraryService.getSharedLibraries(libraryIds));
  }

  @Test
  public void testGetSharedLibrariesWithNoLibrarySetFound() {
    CqlLibrary library1 =
        CqlLibrary.builder().id("libraryId1").librarySetId("librarySetId1").build();

    when(cqlLibraryRepository.findById("libraryId1")).thenReturn(Optional.ofNullable(library1));

    assertThrows(
        ResourceNotFoundException.class,
        () -> cqlLibraryService.getSharedLibraries(List.of("libraryId1")));
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
        cqlLibraryService.getSharedLibraries(libraryIds);

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
        cqlLibraryService.getSharedLibraries(libraryIds);

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
}

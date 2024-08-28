package gov.cms.madie.cqllibraryservice.controllers;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.exceptions.DuplicateKeyException;
import gov.cms.madie.cqllibraryservice.exceptions.InvalidIdException;
import gov.cms.madie.cqllibraryservice.exceptions.InvalidResourceStateException;
import gov.cms.madie.cqllibraryservice.exceptions.PermissionDeniedException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotDraftableException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.services.ActionLogService;
import gov.cms.madie.cqllibraryservice.services.LibrarySetService;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.CqlLibraryDraft;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryService;
import gov.cms.madie.cqllibraryservice.services.VersionService;
import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import gov.cms.madie.models.library.LibrarySet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class CqlLibraryControllerTest {

  @Mock CqlLibraryRepository cqlLibraryRepository;

  @Mock VersionService versionService;

  @Mock CqlLibraryService cqlLibraryService;

  @Mock ActionLogService actionLogService;

  @Mock private LibrarySetService librarySetService;

  @Mock Principal principal;

  @InjectMocks CqlLibraryController cqlLibraryController;

  @Captor private ArgumentCaptor<CqlLibrary> cqlLibraryArgumentCaptor;

  @Captor private ArgumentCaptor<ActionType> actionTypeArgumentCaptor;

  @Captor private ArgumentCaptor<String> targetIdArgumentCaptor;

  private CqlLibrary cqlLibrary;
  private LibraryListDTO libraryList;

  @BeforeEach
  public void setUp() {
    cqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .cqlLibraryName("testCqlLibraryName")
            .librarySetId("testCqlLibrarySetId")
            .build();

    libraryList =
        LibraryListDTO.builder()
            .id("testCqlLibraryId")
            .cqlLibraryName("testCqlLibraryName")
            .librarySetId("testCqlLibrarySetId")
            .build();
  }

  @Test
  void getCqlLibrariesWithoutCurrentUserFilter() {
    List<LibraryListDTO> cqlLibraries = List.of(libraryList);
    when(cqlLibraryRepository.findAllLibrariesByUser("")).thenReturn(cqlLibraries);
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    ResponseEntity<List<LibraryListDTO>> response =
        cqlLibraryController.getCqlLibraries(principal, false);
    verify(cqlLibraryRepository, times(1)).findAllLibrariesByUser("");
    verifyNoMoreInteractions(cqlLibraryRepository);
    assertNotNull(response.getBody());
    assertNotNull(response.getBody().get(0));
    assertEquals("testCqlLibraryId", response.getBody().get(0).getId());
  }

  @Test
  void getCqlLibrariesWithCurrentUserFilter() {
    List<LibraryListDTO> cqlLibraries = List.of(libraryList);
    when(cqlLibraryRepository.findAllLibrariesByUser(anyString())).thenReturn(cqlLibraries);
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<List<LibraryListDTO>> response =
        cqlLibraryController.getCqlLibraries(principal, true);
    verify(cqlLibraryRepository, times(1)).findAllLibrariesByUser(eq("test.user"));
    verifyNoMoreInteractions(cqlLibraryRepository);
    assertNotNull(response.getBody());
    assertNotNull(response.getBody().get(0));
    assertEquals("testCqlLibraryId", response.getBody().get(0).getId());
  }

  @Test
  void testSaveCqlLibrary() {
    ArgumentCaptor<CqlLibrary> saveCqlLibraryArgCaptor = ArgumentCaptor.forClass(CqlLibrary.class);
    doReturn(cqlLibrary).when(cqlLibraryRepository).save(any());
    doNothing().when(librarySetService).createLibrarySet(anyString(), anyString(), anyString());

    CqlLibrary cqlLibrary = new CqlLibrary();
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<CqlLibrary> response =
        cqlLibraryController.createCqlLibrary(cqlLibrary, principal);
    assertNotNull(response.getBody());
    assertEquals("testCqlLibraryId", response.getBody().getId());

    verify(cqlLibraryRepository, times(1)).save(saveCqlLibraryArgCaptor.capture());
    CqlLibrary savedCqlLibrary = saveCqlLibraryArgCaptor.getValue();
    assertThat(savedCqlLibrary.getCreatedBy(), is(equalTo("test.user")));
    assertThat(savedCqlLibrary.getLastModifiedBy(), is(equalTo("test.user")));
    assertThat(savedCqlLibrary.getCreatedAt(), is(notNullValue()));
    assertThat(savedCqlLibrary.getLastModifiedAt(), is(notNullValue()));

    verify(actionLogService, times(1))
        .logAction(
            targetIdArgumentCaptor.capture(), actionTypeArgumentCaptor.capture(), anyString());
    assertThat(targetIdArgumentCaptor.getValue(), is(notNullValue()));
    assertThat(actionTypeArgumentCaptor.getValue(), is(equalTo(ActionType.CREATED)));
  }

  @Test
  public void testGetCqlLibraryThrowsExceptionForNotFound() {
    doThrow(new ResourceNotFoundException("CQL Library", "Library1"))
        .when(cqlLibraryService)
        .findCqlLibraryById(anyString());
    assertThrows(
        ResourceNotFoundException.class, () -> cqlLibraryController.getCqlLibrary("Library1"));
  }

  @Test
  public void testGetCqlLibraryReturnsLibrary() {
    CqlLibrary library =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .cql("library testCql version '1.0.000'")
            .model(ModelType.QI_CORE.getValue())
            .build();
    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(library);
    ResponseEntity<CqlLibrary> output = cqlLibraryController.getCqlLibrary("Library1_ID");
    assertNotNull(output);
    assertEquals(library, output.getBody());
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForNullIdOnLibrary() {
    final String pathId = "Library1_ID";
    final CqlLibrary existingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .build();
    final CqlLibrary updatingLibrary =
        existingLibrary.toBuilder().id(null).cqlLibraryName("NewName").build();

    assertThrows(
        InvalidIdException.class,
        () -> cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal));
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForEmptyIdOnLibrary() {
    final String pathId = "Library1_ID";
    final CqlLibrary existingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .build();
    final CqlLibrary updatingLibrary =
        existingLibrary.toBuilder().id("").cqlLibraryName("NewName").build();

    assertThrows(
        InvalidIdException.class,
        () -> cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal));
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForNullId() {
    final String pathId = null;
    final CqlLibrary existingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .build();
    final CqlLibrary updatingLibrary =
        existingLibrary.toBuilder().id("Library1_ID").cqlLibraryName("NewName").build();

    assertThrows(
        InvalidIdException.class,
        () -> cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal));
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForEmptyId() {
    final String pathId = "";
    final CqlLibrary existingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .build();
    final CqlLibrary updatingLibrary =
        existingLibrary.toBuilder().id("Library1_ID").cqlLibraryName("NewName").build();

    assertThrows(
        InvalidIdException.class,
        () -> cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal));
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForMismatchedIds() {
    final String pathId = "Library1_ID";
    final CqlLibrary updatingLibrary =
        CqlLibrary.builder().id("Library2_ID").cqlLibraryName("NewName").build();

    assertThrows(
        InvalidIdException.class,
        () -> cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal));
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForNotFound() {
    final String pathId = "Library1_ID";
    final CqlLibrary updatingLibrary =
        CqlLibrary.builder().id("Library1_ID").cqlLibraryName("NewName").build();

    doThrow(new ResourceNotFoundException("CQL Library", updatingLibrary.getId()))
        .when(cqlLibraryService)
        .findCqlLibraryById(anyString());

    assertThrows(
        ResourceNotFoundException.class,
        () -> cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal));
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForNonUniqueNameUpdate() {
    when(principal.getName()).thenReturn("test.user");
    final String pathId = "Library1_ID";
    final CqlLibrary existingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .draft(true)
            .createdBy("test.user")
            .librarySet(
                LibrarySet.builder().librarySetId("testLibrarySetId").owner("test.user").build())
            .build();
    final CqlLibrary updatingLibrary =
        existingLibrary.toBuilder().id("Library1_ID").cqlLibraryName("NewName").build();

    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingLibrary);
    when(cqlLibraryService.isCqlLibraryNameChanged(any(CqlLibrary.class), any(CqlLibrary.class)))
        .thenReturn(true);
    doThrow(new DuplicateKeyException("cqlLibraryName", "Library name must be unique."))
        .when(cqlLibraryService)
        .checkDuplicateCqlLibraryName(anyString());

    assertThrows(
        DuplicateKeyException.class,
        () -> cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal));
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForNonDraftUpdate() {
    when(principal.getName()).thenReturn("test.user");
    final String pathId = "Library1_ID";
    final CqlLibrary existingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .draft(false)
            .createdBy("test.user")
            .librarySet(
                LibrarySet.builder().librarySetId("testLibrarySetId").owner("test.user").build())
            .build();
    final CqlLibrary updatingLibrary =
        existingLibrary.toBuilder().id("Library1_ID").cqlLibraryName("NewName").draft(true).build();

    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingLibrary);
    assertThrows(
        InvalidResourceStateException.class,
        () -> cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal));
  }

  @Test
  public void testUpdateCqlLibraryThrowsPermissionDeniedException() {
    when(principal.getName()).thenReturn("random.user");
    final String pathId = "Library1_ID";
    final CqlLibrary existingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .draft(false)
            .createdBy("test.user")
            .librarySet(
                LibrarySet.builder().librarySetId("testLibrarySetId").owner("test.user").build())
            .build();
    final CqlLibrary updatingLibrary =
        existingLibrary.toBuilder().id("Library1_ID").cqlLibraryName("NewName").draft(true).build();

    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingLibrary);
    assertThrows(
        PermissionDeniedException.class,
        () -> cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal));
  }

  @Test
  public void testUpdateCqlLibrarySuccessfullyUpdates() {
    final String pathId = "Library1_ID";
    final String cql =
        "library testCql version '2.1.000'\n"
            + "using QICore version '4.1.1'\n"
            + "include TestLibrary17194110463836086082 version '1.0.000' called Test";
    final Instant createdTime = Instant.now().minus(100, ChronoUnit.MINUTES);
    final CqlLibrary existingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .cql("library testCql version '1.0.000'")
            .draft(true)
            .createdAt(createdTime)
            .createdBy("User2")
            .librarySet(
                LibrarySet.builder().librarySetId("testLibrarySetId").owner("User2").build())
            .lastModifiedAt(createdTime)
            .lastModifiedBy("User1")
            .build();
    final CqlLibrary updatingLibrary =
        existingLibrary.toBuilder()
            .id("Library1_ID")
            .cqlLibraryName("NewName")
            .cql(cql)
            .draft(false)
            .build();

    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingLibrary);
    when(cqlLibraryService.isCqlLibraryNameChanged(any(CqlLibrary.class), any(CqlLibrary.class)))
        .thenReturn(false);
    when(principal.getName()).thenReturn("User2");

    when(cqlLibraryRepository.save(any(CqlLibrary.class))).thenReturn(updatingLibrary);

    ResponseEntity<CqlLibrary> output =
        cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal);
    assertThat(output.getBody(), is(equalTo(updatingLibrary)));
    verify(cqlLibraryRepository, times(1)).save(cqlLibraryArgumentCaptor.capture());
    CqlLibrary updatedLibrary = cqlLibraryArgumentCaptor.getValue();
    assertThat(updatedLibrary, is(notNullValue()));
    assertThat(updatedLibrary.getId(), is(equalTo("Library1_ID")));
    assertThat(updatedLibrary.getCqlLibraryName(), is(equalTo("NewName")));
    assertThat(updatedLibrary.getCql(), is(equalTo(cql)));
    assertThat(updatedLibrary.getCreatedAt(), is(equalTo(createdTime)));
    assertThat(updatedLibrary.getCreatedBy(), is(equalTo("User2")));
    assertThat(updatedLibrary.getLastModifiedAt(), is(notNullValue()));
    assertThat(updatedLibrary.getLastModifiedAt().isAfter(createdTime), is(true));
    assertThat(updatedLibrary.getLastModifiedBy(), is(equalTo("User2")));
    assertThat(updatedLibrary.getIncludedLibraries().size(), is(equalTo(1)));
  }

  @Test
  public void testCreateDraftReturnsDraft() {
    CqlLibrary draft =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .draft(true)
            .version(new Version(0, 0, 0))
            .cql("library testCql version '1.0.000'")
            .createdBy("User1")
            .lastModifiedBy("User1")
            .build();
    when(versionService.createDraft(anyString(), anyString(), anyString())).thenReturn(draft);
    when(principal.getName()).thenReturn("test.user");
    ResponseEntity<CqlLibrary> output =
        cqlLibraryController.createDraft(
            "Library1_ID",
            CqlLibraryDraft.builder()
                .cqlLibraryName("Library1")
                .cql("library Library1 version '1.0.000'")
                .build(),
            principal);
    assertThat(output, is(notNullValue()));
    assertThat(output.getStatusCode(), is(equalTo(HttpStatus.CREATED)));
    assertThat(output.getBody(), is(equalTo(draft)));
  }

  @Test
  public void testCreateDraftReturnsException() {
    when(versionService.createDraft(anyString(), anyString(), anyString()))
        .thenThrow(new ResourceNotDraftableException("CqlLibrary"));
    when(principal.getName()).thenReturn("test.user");
    assertThrows(
        ResourceNotDraftableException.class,
        () ->
            cqlLibraryController.createDraft(
                "Library1_ID",
                CqlLibraryDraft.builder()
                    .cqlLibraryName("Library1")
                    .cql("library Library1 version '1.0.000'")
                    .build(),
                principal));
  }

  @Test
  public void testCreateVersionReturnsVersion() {
    CqlLibrary version =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .draft(false)
            .version(new Version(1, 0, 0))
            .cql("library testCql version '1.0.000'")
            .createdBy("User1")
            .lastModifiedBy("User1")
            .build();
    when(principal.getName()).thenReturn("test.user");
    when(versionService.createVersion(anyString(), anyBoolean(), anyString(), anyString()))
        .thenReturn(version);
    ResponseEntity<CqlLibrary> output =
        cqlLibraryController.createVersion("Library1_ID", true, principal, "accesstoken");
    assertThat(output, is(notNullValue()));
    assertThat(output.getStatusCode(), is(HttpStatus.OK));
    assertThat(output.getBody(), is(equalTo(version)));
  }

  @Test
  public void testCreateVersionReturnsError() {
    when(principal.getName()).thenReturn("test.user");
    when(versionService.createVersion(anyString(), anyBoolean(), anyString(), anyString()))
        .thenThrow(new PermissionDeniedException("CQL Library", cqlLibrary.getId(), "test.user"));
    assertThrows(
        PermissionDeniedException.class,
        () -> cqlLibraryController.createVersion("Library1_ID", true, principal, "accesstoken"));
  }

  @Test
  public void testGetLibraryCql() {
    when(cqlLibraryService.getVersionedCqlLibrary(anyString(), any(), any(), anyBoolean(), any()))
        .thenReturn(CqlLibrary.builder().cql("Test Cql").build());
    String cql = cqlLibraryController.getLibraryCql("TestCqlLibrary", "1.0.000", Optional.empty());

    verify(cqlLibraryService, times(1))
        .getVersionedCqlLibrary(anyString(), any(), any(), anyBoolean(), any());
    assertEquals("Test Cql", cql);
  }

  @Test
  public void testGetVersionedCqlLibrary() {
    when(cqlLibraryService.getVersionedCqlLibrary(anyString(), any(), any(), anyBoolean(), any()))
        .thenReturn(CqlLibrary.builder().build());
    ResponseEntity<CqlLibrary> versionedCqlLibrary =
        cqlLibraryController.getVersionedCqlLibrary(
            "TestCqlLibrary", "1.0.000", Optional.empty(), true, "test-token");

    verify(cqlLibraryService, times(1))
        .getVersionedCqlLibrary(anyString(), any(), any(), anyBoolean(), any());
    assertEquals(HttpStatus.OK, versionedCqlLibrary.getStatusCode());
  }

  @Test
  void testGetLibraryUsage() {
    String libraryName = "Helper";
    String owner = "john";
    LibraryUsage libraryUsage = LibraryUsage.builder().name(libraryName).owner(owner).build();
    when(cqlLibraryService.findLibraryUsage(anyString())).thenReturn(List.of(libraryUsage));
    ResponseEntity<List<LibraryUsage>> response = cqlLibraryController.getLibraryUsage(libraryName);
    List<LibraryUsage> usage = response.getBody();
    assertThat(usage.size(), is(equalTo(1)));
    assertThat(usage.get(0).getName(), is(equalTo(libraryName)));
    assertThat(usage.get(0).getOwner(), is(equalTo(owner)));
  }

  @Test
  void testDeleteLibraryAlongWithVersions() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("api-key", "key");
    String libraryName = "Helper";
    doNothing().when(cqlLibraryService).deleteLibraryAlongWithVersions(anyString(), anyString());
    ResponseEntity<String> response =
        cqlLibraryController.deleteLibraryAlongWithVersions(request, libraryName, "token", "key");
    assertThat(
        response.getBody(),
        is(equalTo("The library and all its associated versions have been removed successfully.")));
  }
}

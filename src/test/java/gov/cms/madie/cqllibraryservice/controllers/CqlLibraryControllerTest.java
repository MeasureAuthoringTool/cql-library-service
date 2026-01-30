package gov.cms.madie.cqllibraryservice.controllers;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import gov.cms.madie.cqllibraryservice.dto.CqlDiffResultDTO;
import gov.cms.madie.cqllibraryservice.dto.CqlFileComparisonDTO;
import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.dto.LibrarySearchCriteria;
import gov.cms.madie.cqllibraryservice.exceptions.InvalidIdException;
import gov.cms.madie.cqllibraryservice.exceptions.PermissionDeniedException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotDraftableException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.services.*;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.*;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.CqlLibraryDraft;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

  @Mock private CqlDifferentiatorService cqlDifferentiatorService;

  @Mock Principal principal;

  @InjectMocks CqlLibraryController cqlLibraryController;

  @Captor private ArgumentCaptor<CqlLibrary> cqlLibraryArgumentCaptor;

  @Captor private ArgumentCaptor<ActionType> actionTypeArgumentCaptor;

  @Captor private ArgumentCaptor<String> targetIdArgumentCaptor;

  private CqlLibrary cqlLibrary;
  private LibraryListDTO libraryList;

  private LibrarySearchCriteria librarySearchCriteria;

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

    librarySearchCriteria = LibrarySearchCriteria.builder().searchField("test").build();
  }

  @Test
  void getCqlLibrariesWithOwnedOwnershipType() {
    List<LibraryListDTO> cqlLibraries = List.of(libraryList);
    Page<LibraryListDTO> pageResult = new PageImpl<>(cqlLibraries);

    when(cqlLibraryService.getLibrariesByCriteria(
            eq(librarySearchCriteria), eq(OwnershipType.OWNED), any(), any()))
        .thenReturn(pageResult);

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<Page<LibraryListDTO>> response =
        cqlLibraryController.fetchLibrariesByCriteria(
            principal, OwnershipType.OWNED, librarySearchCriteria, 10, 0, "");

    verify(cqlLibraryService, times(1))
        .getLibrariesByCriteria(eq(librarySearchCriteria), eq(OwnershipType.OWNED), any(), any());
    verifyNoMoreInteractions(cqlLibraryService);

    assertNotNull(response.getBody());
    assertFalse(response.getBody().isEmpty());
    assertEquals("testCqlLibraryId", response.getBody().getContent().get(0).getId());
  }

  @Test
  void getCqlLibrariesWithSharedOwnershipType() {
    List<LibraryListDTO> cqlLibraries = List.of(libraryList);
    Page<LibraryListDTO> pageResult = new PageImpl<>(cqlLibraries);

    when(cqlLibraryService.getLibrariesByCriteria(
            eq(librarySearchCriteria), eq(OwnershipType.SHARED), any(), any()))
        .thenReturn(pageResult);

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<Page<LibraryListDTO>> response =
        cqlLibraryController.fetchLibrariesByCriteria(
            principal, OwnershipType.SHARED, librarySearchCriteria, 10, 0, "");

    verify(cqlLibraryService, times(1))
        .getLibrariesByCriteria(eq(librarySearchCriteria), eq(OwnershipType.SHARED), any(), any());
    verifyNoMoreInteractions(cqlLibraryService);

    assertNotNull(response.getBody());
    assertFalse(response.getBody().isEmpty());
    assertEquals("testCqlLibraryId", response.getBody().getContent().get(0).getId());
  }

  @Test
  void getCqlLibrariesWithAllOwnershipType() {
    List<LibraryListDTO> cqlLibraries = List.of(libraryList);
    Page<LibraryListDTO> pageResult = new PageImpl<>(cqlLibraries);

    when(cqlLibraryService.getLibrariesByCriteria(
            eq(librarySearchCriteria), eq(OwnershipType.ALL), any(), any()))
        .thenReturn(pageResult);

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<Page<LibraryListDTO>> response =
        cqlLibraryController.fetchLibrariesByCriteria(
            principal, OwnershipType.ALL, librarySearchCriteria, 10, 0, "");

    verify(cqlLibraryService, times(1))
        .getLibrariesByCriteria(eq(librarySearchCriteria), eq(OwnershipType.ALL), any(), any());
    verifyNoMoreInteractions(cqlLibraryService);

    assertNotNull(response.getBody());
    assertFalse(response.getBody().isEmpty());
    assertEquals("testCqlLibraryId", response.getBody().getContent().get(0).getId());
  }

  @Test
  void getCqlLibrariesWithBadSortInfo() {
    List<LibraryListDTO> cqlLibraries = List.of(libraryList);
    Page<LibraryListDTO> pageResult = new PageImpl<>(cqlLibraries);

    when(cqlLibraryService.getLibrariesByCriteria(
            eq(librarySearchCriteria), eq(OwnershipType.ALL), any(), any()))
        .thenReturn(pageResult);

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<Page<LibraryListDTO>> response =
        cqlLibraryController.fetchLibrariesByCriteria(
            principal,
            OwnershipType.ALL,
            librarySearchCriteria,
            10,
            0,
            "badsortinfo,worsesortinfo,asdf");

    verify(cqlLibraryService, times(1))
        .getLibrariesByCriteria(eq(librarySearchCriteria), eq(OwnershipType.ALL), any(), any());
    verifyNoMoreInteractions(cqlLibraryService);

    assertNotNull(response.getBody());
    assertFalse(response.getBody().isEmpty());
    assertEquals("testCqlLibraryId", response.getBody().getContent().get(0).getId());
  }

  @Test
  void getCqlLibrariesWithGoodAscendingSortInfo() {
    List<LibraryListDTO> cqlLibraries = List.of(libraryList);
    Page<LibraryListDTO> pageResult = new PageImpl<>(cqlLibraries);

    when(cqlLibraryService.getLibrariesByCriteria(
            eq(librarySearchCriteria), eq(OwnershipType.ALL), any(), any()))
        .thenReturn(pageResult);

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<Page<LibraryListDTO>> response =
        cqlLibraryController.fetchLibrariesByCriteria(
            principal, OwnershipType.ALL, librarySearchCriteria, 10, 0, "draft,false");

    verify(cqlLibraryService, times(1))
        .getLibrariesByCriteria(eq(librarySearchCriteria), eq(OwnershipType.ALL), any(), any());
    verifyNoMoreInteractions(cqlLibraryService);

    assertNotNull(response.getBody());
    assertFalse(response.getBody().isEmpty());
    assertEquals("testCqlLibraryId", response.getBody().getContent().get(0).getId());
  }

  @Test
  void getCqlLibrariesWithGoodDescendingSortInfo() {
    List<LibraryListDTO> cqlLibraries = List.of(libraryList);
    Page<LibraryListDTO> pageResult = new PageImpl<>(cqlLibraries);

    when(cqlLibraryService.getLibrariesByCriteria(
            eq(librarySearchCriteria), eq(OwnershipType.ALL), any(), any()))
        .thenReturn(pageResult);

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<Page<LibraryListDTO>> response =
        cqlLibraryController.fetchLibrariesByCriteria(
            principal, OwnershipType.ALL, librarySearchCriteria, 10, 0, "draft,true");

    verify(cqlLibraryService, times(1))
        .getLibrariesByCriteria(eq(librarySearchCriteria), eq(OwnershipType.ALL), any(), any());
    verifyNoMoreInteractions(cqlLibraryService);

    assertNotNull(response.getBody());
    assertFalse(response.getBody().isEmpty());
    assertEquals("testCqlLibraryId", response.getBody().getContent().get(0).getId());
  }

  @Test
  void getCqlLibrariesWithNullSortInfo() {
    List<LibraryListDTO> cqlLibraries = List.of(libraryList);
    Page<LibraryListDTO> pageResult = new PageImpl<>(cqlLibraries);

    when(cqlLibraryService.getLibrariesByCriteria(
            eq(librarySearchCriteria), eq(OwnershipType.ALL), any(), any()))
        .thenReturn(pageResult);

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<Page<LibraryListDTO>> response =
        cqlLibraryController.fetchLibrariesByCriteria(
            principal, OwnershipType.ALL, librarySearchCriteria, 10, 0, null);

    verify(cqlLibraryService, times(1))
        .getLibrariesByCriteria(eq(librarySearchCriteria), eq(OwnershipType.ALL), any(), any());
    verifyNoMoreInteractions(cqlLibraryService);

    assertNotNull(response.getBody());
    assertFalse(response.getBody().isEmpty());
    assertEquals("testCqlLibraryId", response.getBody().getContent().get(0).getId());
  }

  @Test
  void getCqlLibrariesWithNullSearchCriteria() {
    List<LibraryListDTO> cqlLibraries = List.of(libraryList);
    Page<LibraryListDTO> pageResult = new PageImpl<>(cqlLibraries);

    when(cqlLibraryService.getLibrariesByCriteria(eq(null), eq(OwnershipType.ALL), any(), any()))
        .thenReturn(pageResult);

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<Page<LibraryListDTO>> response =
        cqlLibraryController.fetchLibrariesByCriteria(
            principal, OwnershipType.ALL, null, 10, 0, null);

    verify(cqlLibraryService, times(1))
        .getLibrariesByCriteria(eq(null), eq(OwnershipType.ALL), any(), any());
    verifyNoMoreInteractions(cqlLibraryService);

    assertNotNull(response.getBody());
    assertFalse(response.getBody().isEmpty());
    assertEquals("testCqlLibraryId", response.getBody().getContent().get(0).getId());
  }

  @Test
  void getCqlLibrariesWithNoResults() {
    Page<LibraryListDTO> emptyPage = Page.empty();

    when(cqlLibraryService.getLibrariesByCriteria(
            eq(librarySearchCriteria), eq(OwnershipType.ALL), any(), any()))
        .thenReturn(emptyPage);

    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<Page<LibraryListDTO>> response =
        cqlLibraryController.fetchLibrariesByCriteria(
            principal, OwnershipType.ALL, librarySearchCriteria, 10, 0, null);

    verify(cqlLibraryService, times(1))
        .getLibrariesByCriteria(eq(librarySearchCriteria), eq(OwnershipType.ALL), any(), any());

    assertNotNull(response.getBody());
    assertTrue(response.getBody().isEmpty());
    assertEquals(0, response.getBody().getTotalElements());
  }

  @Test
  void testSaveCqlLibrary() {
    ArgumentCaptor<CqlLibrary> saveCqlLibraryArgCaptor = ArgumentCaptor.forClass(CqlLibrary.class);
    doReturn(cqlLibrary).when(cqlLibraryRepository).save(any());
    doNothing().when(librarySetService).createLibrarySet(anyString(), anyString(), anyString());

    CqlLibrary cqlLibrary = CqlLibrary.builder().id("1").build();
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
            targetIdArgumentCaptor.capture(),
            actionTypeArgumentCaptor.capture(),
            anyString(),
            anyString());
    assertThat(targetIdArgumentCaptor.getValue(), is(notNullValue()));
    assertThat(actionTypeArgumentCaptor.getValue(), is(equalTo(ActionType.CREATED)));
  }

  @Test
  public void testGetAllOwners() {
    List<String> mockedResponse = List.of("owner1", "owner2");
    when(librarySetService.getAllOwners(any())).thenReturn(mockedResponse);

    var result = cqlLibraryController.getAllOwners(List.of("set1", "set2"));
    assertEquals(mockedResponse, result.getBody());
  }

  @Test
  public void testGetCqlLibraryThrowsExceptionForNotFound() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    doThrow(new ResourceNotFoundException("CQL Library", "Library1"))
        .when(cqlLibraryService)
        .findCqlLibraryById(anyString(), anyString());
    assertThrows(
        ResourceNotFoundException.class,
        () -> cqlLibraryController.getCqlLibrary("Library1", principal));
  }

  @Test
  public void testGetCqlLibraryReturnsLibrary() {
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");
    CqlLibrary library =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .cql("library testCql version '1.0.000'")
            .model(ModelType.QI_CORE.getValue())
            .build();
    when(cqlLibraryService.findCqlLibraryById(anyString(), anyString())).thenReturn(library);
    ResponseEntity<CqlLibrary> output =
        cqlLibraryController.getCqlLibrary("Library1_ID", principal);
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
    when(principal.getName()).thenReturn("test.user");

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
    when(principal.getName()).thenReturn("test.user");

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
    when(principal.getName()).thenReturn("test.user");

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
    when(principal.getName()).thenReturn("test.user");

    assertThrows(
        InvalidIdException.class,
        () -> cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal));
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForMismatchedIds() {
    final String pathId = "Library1_ID";
    final CqlLibrary updatingLibrary =
        CqlLibrary.builder().id("Library2_ID").cqlLibraryName("NewName").build();
    when(principal.getName()).thenReturn("test.user");

    assertThrows(
        InvalidIdException.class,
        () -> cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal));
  }

  @Test
  public void testUpdateCqlLibrarySuccessfullyUpdates() {
    final String pathId = "Library1_ID";
    final CqlLibrary updatingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .draft(true)
            .build();

    when(principal.getName()).thenReturn("test.user");
    when(cqlLibraryService.updateCqlLibrary(any(CqlLibrary.class), anyString()))
        .thenReturn(updatingLibrary);

    ResponseEntity<CqlLibrary> output =
        cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal);

    assertThat(output.getBody(), is(equalTo(updatingLibrary)));
    verify(cqlLibraryService, times(1)).updateCqlLibrary(updatingLibrary, "test.user");
    verifyNoMoreInteractions(cqlLibraryService);
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
    when(versionService.createDraft(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(draft);
    when(principal.getName()).thenReturn("test.user");
    ResponseEntity<CqlLibrary> output =
        cqlLibraryController.createDraft(
            "Library1_ID",
            CqlLibraryDraft.builder()
                .cqlLibraryName("Library1")
                .model(ModelType.QI_CORE.getValue())
                .cql("library Library1 version '1.0.000'")
                .build(),
            principal);
    assertThat(output, is(notNullValue()));
    assertThat(output.getStatusCode(), is(equalTo(HttpStatus.CREATED)));
    assertThat(output.getBody(), is(equalTo(draft)));
  }

  @Test
  void logsUpdateActionSuccessfully() {
    String savedCqlLibraryId = "testCqlLibraryId";
    String username = "testUser";

    actionLogService.logAction(savedCqlLibraryId, ActionType.UPDATED, username, "actionLog");

    verify(actionLogService, times(1))
        .logAction(
            targetIdArgumentCaptor.capture(),
            actionTypeArgumentCaptor.capture(),
            anyString(),
            anyString());
    assertThat(targetIdArgumentCaptor.getValue(), is(equalTo("testCqlLibraryId")));
    assertThat(actionTypeArgumentCaptor.getValue(), is(equalTo(ActionType.UPDATED)));
  }

  @Test
  public void testCreateDraftReturnsException() {
    when(versionService.createDraft(anyString(), anyString(), anyString(), anyString()))
        .thenThrow(
            new ResourceNotDraftableException(
                "CqlLibrary", "A draft already exists for the CQL Library Group."));
    when(principal.getName()).thenReturn("test.user");
    assertThrows(
        ResourceNotDraftableException.class,
        () ->
            cqlLibraryController.createDraft(
                "Library1_ID",
                CqlLibraryDraft.builder()
                    .cqlLibraryName("Library1")
                    .model(ModelType.QI_CORE.getValue())
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
    when(cqlLibraryService.getVersionedCqlLibrary(
            anyString(), any(), any(), anyBoolean(), anyString(), any()))
        .thenReturn(CqlLibrary.builder().cql("Test Cql").build());
    String cql = cqlLibraryController.getLibraryCql("TestCqlLibrary", "1.0.000", Optional.empty());

    verify(cqlLibraryService, times(1))
        .getVersionedCqlLibrary(anyString(), any(), any(), anyBoolean(), anyString(), any());
    assertEquals("Test Cql", cql);
  }

  @Test
  public void testGetVersionedCqlLibrary() {
    when(cqlLibraryService.getVersionedCqlLibrary(
            anyString(), any(), any(), anyBoolean(), anyString(), any()))
        .thenReturn(CqlLibrary.builder().build());
    ResponseEntity<CqlLibrary> versionedCqlLibrary =
        cqlLibraryController.getVersionedCqlLibrary(
            "TestCqlLibrary", "1.0.000", Optional.empty(), true, "Info", "test-token");

    verify(cqlLibraryService, times(1))
        .getVersionedCqlLibrary(anyString(), any(), any(), anyBoolean(), anyString(), any());
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
    doNothing()
        .when(cqlLibraryService)
        .deleteLibraryAlongWithVersions(anyString(), anyString(), anyString());
    ResponseEntity<String> response =
        cqlLibraryController.deleteLibraryAlongWithVersions(
            request, libraryName, "token", "harpId", "key");
    assertThat(
        response.getBody(),
        is(equalTo("The library and all its associated versions have been removed successfully.")));
  }

  @Test
  public void testUpdateAccessControl() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("api-key", "key");

    AclSpecification aclSpecification = new AclSpecification();
    aclSpecification.setUserId("user_1");
    aclSpecification.setRoles(Set.of(RoleEnum.SHARED_WITH));

    AclOperation aclOperation =
        AclOperation.builder()
            .acls(List.of(aclSpecification))
            .action(AclOperation.AclAction.GRANT)
            .build();

    List<AclSpecification> aclSpecifications = List.of(aclSpecification);

    when(cqlLibraryService.updateAccessControlList(anyString(), any(), anyString()))
        .thenReturn(aclSpecifications);

    ResponseEntity<List<AclSpecification>> output =
        cqlLibraryController.updateAccessControl(request, "1", aclOperation, "key");

    verify(cqlLibraryService, times(1)).updateAccessControlList(anyString(), any(), anyString());
    assertThat(output.getBody(), equalTo(aclSpecifications));
  }

  @Test
  public void testGetLibrariesByLibrarySetId() {
    List<LibraryListDTO> cqlLibraries = List.of(libraryList);
    LibrarySearchCriteria librarySearchCriteria = new LibrarySearchCriteria();

    when(cqlLibraryService.getLibrariesByLibrarySetId(
            eq("test"), eq(true), eq(librarySearchCriteria)))
        .thenReturn(cqlLibraries);

    ResponseEntity<List<LibraryListDTO>> response =
        cqlLibraryController.getLibrariesByLibrarySetId("test", true, librarySearchCriteria);

    verify(cqlLibraryService, times(1))
        .getLibrariesByLibrarySetId(eq("test"), eq(true), eq(librarySearchCriteria));
    verifyNoMoreInteractions(cqlLibraryService);

    assertNotNull(response.getBody());
    assertEquals("testCqlLibraryId", response.getBody().get(0).getId());
  }

  @Test
  void returnsCqlLibraryHistorySuccessfully() {
    String cqlLibraryId = "testLibraryId";
    String username = "testuser";
    when(principal.getName()).thenReturn("testuser");
    List<Action> actions =
        List.of(
            Action.builder().actionType(ActionType.CREATED).build(),
            Action.builder().actionType(ActionType.UPDATED).build());

    when(cqlLibraryService.getCqlLibraryHistory(cqlLibraryId, username)).thenReturn(actions);

    ResponseEntity<List<Action>> response =
        cqlLibraryController.getCqlLibraryHistory(cqlLibraryId, principal);

    assertNotNull(response.getBody());
    assertEquals(2, response.getBody().size());
    assertEquals(ActionType.CREATED, response.getBody().get(0).getActionType());
    assertEquals(ActionType.UPDATED, response.getBody().get(1).getActionType());
  }

  @Test
  void throwsResourceNotFoundExceptionWhenLibraryHistoryNotFound() {
    String cqlLibraryId = "nonExistentLibraryId";
    String username = "testuser";
    when(principal.getName()).thenReturn("testuser");

    when(cqlLibraryService.getCqlLibraryHistory(cqlLibraryId, username))
        .thenThrow(new ResourceNotFoundException("CQL Library", cqlLibraryId));

    assertThrows(
        ResourceNotFoundException.class,
        () -> cqlLibraryController.getCqlLibraryHistory(cqlLibraryId, principal));
  }

  @Test
  void returnsEmptyHistoryWhenNoActionsExist() {
    String cqlLibraryId = "testLibraryId";
    String username = "testuser";
    when(principal.getName()).thenReturn("testuser");

    when(cqlLibraryService.getCqlLibraryHistory(cqlLibraryId, username)).thenReturn(List.of());

    ResponseEntity<List<Action>> response =
        cqlLibraryController.getCqlLibraryHistory(cqlLibraryId, principal);

    assertNotNull(response.getBody());
    assertTrue(response.getBody().isEmpty());
  }

  @Test
  void compareLibrariesReturnsCqlDiffResultForValidLibraryIds() {
    when(principal.getName()).thenReturn("user");
    CqlLibrary oldCqlLibrary =
        CqlLibrary.builder()
            .id("oldLibraryId")
            .cql("library OldLibrary { define: 'Old CQL' }")
            .cqlLibraryName("OldLibrary")
            .build();

    CqlLibrary newCqlLibrary =
        CqlLibrary.builder()
            .id("newLibraryId")
            .cql("library NewLibrary { define: 'New CQL' }")
            .cqlLibraryName("NewLibrary")
            .build();

    List<CqlFileComparisonDTO> comparisons =
        List.of(
            CqlFileComparisonDTO.builder()
                .oldFileName("OldLibrary.cql")
                .newFileName("NewLibrary.cql")
                .oldText("library OldLibrary { define: 'Old CQL' }")
                .newText("library NewLibrary { define: 'New CQL' }")
                .build());

    when(cqlLibraryService.findCqlLibraryById("oldLibraryId", "user")).thenReturn(oldCqlLibrary);
    when(cqlLibraryService.findCqlLibraryById("newLibraryId", "user")).thenReturn(newCqlLibrary);
    when(cqlDifferentiatorService.compareLibraries(anyMap(), anyMap(), eq(true)))
        .thenReturn(comparisons);

    ResponseEntity<CqlDiffResultDTO> response =
        cqlLibraryController.compareLibraries("oldLibraryId", "newLibraryId", true, principal);

    assertNotNull(response.getBody());
    assertEquals("oldLibraryId", response.getBody().getOldLibraryId());
    assertEquals("newLibraryId", response.getBody().getNewLibraryId());
    assertEquals(1, response.getBody().getComparisons().size());
    assertEquals("OldLibrary.cql", response.getBody().getComparisons().get(0).getOldFileName());
    assertEquals("NewLibrary.cql", response.getBody().getComparisons().get(0).getNewFileName());
    assertEquals(
        "library OldLibrary { define: 'Old CQL' }",
        response.getBody().getComparisons().get(0).getOldText());
    assertEquals(
        "library NewLibrary { define: 'New CQL' }",
        response.getBody().getComparisons().get(0).getNewText());
  }

  @Test
  void compareLibrariesThrowsResourceNotFoundExceptionForInvalidOldLibraryId() {
    when(principal.getName()).thenReturn("user");
    when(cqlLibraryService.findCqlLibraryById("oldLibraryId", "user")).thenReturn(null);
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            cqlLibraryController.compareLibraries("oldLibraryId", "newLibraryId", true, principal));
  }

  //
  @Test
  void compareLibrariesThrowsResourceNotFoundExceptionForInvalidNewLibraryId() {
    when(principal.getName()).thenReturn("user");
    CqlLibrary oldCqlLibrary =
        CqlLibrary.builder()
            .id("oldLibraryId")
            .cql("library OldLibrary { define: 'Old CQL' }")
            .cqlLibraryName("OldLibrary")
            .build();

    when(cqlLibraryService.findCqlLibraryById("oldLibraryId", "user")).thenReturn(oldCqlLibrary);
    when(cqlLibraryService.findCqlLibraryById("newLibraryId", "user")).thenReturn(null);

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            cqlLibraryController.compareLibraries("oldLibraryId", "newLibraryId", true, principal));
  }

  //
  @Test
  void compareLibrariesReturnsEmptyComparisonsForLibrariesWithoutCql() {
    when(principal.getName()).thenReturn("user");
    CqlLibrary oldCqlLibrary =
        CqlLibrary.builder().id("oldLibraryId").cql(null).cqlLibraryName("OldLibrary").build();

    CqlLibrary newCqlLibrary =
        CqlLibrary.builder().id("newLibraryId").cql(null).cqlLibraryName("NewLibrary").build();

    when(cqlLibraryService.findCqlLibraryById("oldLibraryId", "user")).thenReturn(oldCqlLibrary);
    when(cqlLibraryService.findCqlLibraryById("newLibraryId", "user")).thenReturn(newCqlLibrary);

    ResponseEntity<CqlDiffResultDTO> response =
        cqlLibraryController.compareLibraries("oldLibraryId", "newLibraryId", true, principal);

    assertNotNull(response.getBody());
    assertEquals("oldLibraryId", response.getBody().getOldLibraryId());
    assertEquals("newLibraryId", response.getBody().getNewLibraryId());
    assertTrue(response.getBody().getComparisons().isEmpty());
  }
}

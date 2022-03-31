package gov.cms.madie.cqllibraryservice.controller;

import gov.cms.madie.cqllibraryservice.exceptions.DuplicateKeyException;
import gov.cms.madie.cqllibraryservice.exceptions.InvalidIdException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.models.CqlLibrary;
import gov.cms.madie.cqllibraryservice.models.ModelType;
import gov.cms.madie.cqllibraryservice.respositories.CqlLibraryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;


import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class CqlLibraryControllerTest {

  @Mock CqlLibraryRepository cqlLibraryRepository;

  @InjectMocks CqlLibraryController cqlLibraryController;

  @Mock Principal principal;

  @Captor private ArgumentCaptor<CqlLibrary> cqlLibraryArgumentCaptor;

  private CqlLibrary cqlLibrary;

  @BeforeEach
  public void setUp() {
    cqlLibrary = new CqlLibrary();
    cqlLibrary.setId("testCqlLibraryId");
    cqlLibrary.setCqlLibraryName("testCqlLibraryName");
  }

  @Test
  void getCqlLibrariesWithoutCurrentUserFilter() {
    List<CqlLibrary> cqlLibraries = List.of(cqlLibrary);
    when(cqlLibraryRepository.findAll()).thenReturn(cqlLibraries);
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<List<CqlLibrary>> response =
        cqlLibraryController.getCqlLibraries(principal, false);
    verify(cqlLibraryRepository, times(1)).findAll();
    verifyNoMoreInteractions(cqlLibraryRepository);
    assertNotNull(response.getBody());
    assertNotNull(response.getBody().get(0));
    assertEquals("testCqlLibraryId", response.getBody().get(0).getId());
  }

  @Test
  void getCqlLibrariesWithCurrentUserFilter() {
    List<CqlLibrary> cqlLibraries = List.of(cqlLibrary);
    when(cqlLibraryRepository.findAllByCreatedBy(anyString())).thenReturn(cqlLibraries);
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("test.user");

    ResponseEntity<List<CqlLibrary>> response =
        cqlLibraryController.getCqlLibraries(principal, true);
    verify(cqlLibraryRepository, times(1)).findAllByCreatedBy(eq("test.user"));
    verifyNoMoreInteractions(cqlLibraryRepository);
    assertNotNull(response.getBody());
    assertNotNull(response.getBody().get(0));
    assertEquals("testCqlLibraryId", response.getBody().get(0).getId());
  }

  @Test
  void testSaveCqlLibrary() {
    ArgumentCaptor<CqlLibrary> saveCqlLibraryArgCaptor = ArgumentCaptor.forClass(CqlLibrary.class);
    doReturn(cqlLibrary).when(cqlLibraryRepository).save(any());

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
  }

  @Test
  public void testGetCqlLibraryThrowsExceptionForNotFound() {
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.empty());
    assertThrows(ResourceNotFoundException.class, () -> cqlLibraryController.getCqlLibrary("Library1"));
  }

  @Test
  public void testGetCqlLibraryReturnsLibrary() {
    CqlLibrary library = CqlLibrary.builder()
        .id("Library1_ID")
        .cqlLibraryName("Library1").model(ModelType.QI_CORE.getValue()).build();
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(library));
    ResponseEntity<CqlLibrary> output = cqlLibraryController.getCqlLibrary("Library1_ID");
    assertNotNull(output);
    assertEquals(library, output.getBody());
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForNullIdOnLibrary() {
    final String pathId = "Library1_ID";
    final CqlLibrary existingLibrary = CqlLibrary.builder()
        .id("Library1_ID")
        .cqlLibraryName("Library1").model(ModelType.QI_CORE.getValue()).build();
    final CqlLibrary updatingLibrary = existingLibrary.toBuilder().id(null).cqlLibraryName("NewName").build();

    assertThrows(InvalidIdException.class, () -> cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal));
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForEmptyIdOnLibrary() {
    final String pathId = "Library1_ID";
    final CqlLibrary existingLibrary = CqlLibrary.builder()
        .id("Library1_ID")
        .cqlLibraryName("Library1").model(ModelType.QI_CORE.getValue()).build();
    final CqlLibrary updatingLibrary = existingLibrary.toBuilder().id("").cqlLibraryName("NewName").build();

    assertThrows(InvalidIdException.class, () -> cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal));
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForNullId() {
    final String pathId = null;
    final CqlLibrary existingLibrary = CqlLibrary.builder()
        .id("Library1_ID")
        .cqlLibraryName("Library1").model(ModelType.QI_CORE.getValue()).build();
    final CqlLibrary updatingLibrary = existingLibrary.toBuilder().id("Library1_ID").cqlLibraryName("NewName").build();

    assertThrows(InvalidIdException.class, () -> cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal));
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForEmpty() {
    final String pathId = "";
    final CqlLibrary existingLibrary = CqlLibrary.builder()
        .id("Library1_ID")
        .cqlLibraryName("Library1").model(ModelType.QI_CORE.getValue()).build();
    final CqlLibrary updatingLibrary = existingLibrary.toBuilder().id("Library1_ID").cqlLibraryName("NewName").build();

    assertThrows(InvalidIdException.class, () -> cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal));
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForMismatchedIds() {
    final String pathId = "Library1_ID";
    final CqlLibrary updatingLibrary = CqlLibrary.builder().id("Library2_ID").cqlLibraryName("NewName").build();

    assertThrows(InvalidIdException.class, () -> cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal));
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForNotFound() {
    final String pathId = "Library1_ID";
    final CqlLibrary updatingLibrary = CqlLibrary.builder().id("Library1_ID").cqlLibraryName("NewName").build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal));
  }

  @Test
  public void testUpdateCqlLibraryThrowsExceptionForNonUniqueNameUpdate() {
    final String pathId = "Library1_ID";
    final CqlLibrary existingLibrary = CqlLibrary.builder()
        .id("Library1_ID")
        .cqlLibraryName("Library1").model(ModelType.QI_CORE.getValue()).build();
    final CqlLibrary otherLibrary = CqlLibrary.builder()
        .id("Library2_ID")
        .cqlLibraryName("NewName").model(ModelType.QI_CORE.getValue()).build();
    final CqlLibrary updatingLibrary = existingLibrary.toBuilder().id("Library1_ID").cqlLibraryName("NewName").build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingLibrary));
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(true);

    assertThrows(DuplicateKeyException.class, () -> cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal));
  }

  @Test
  public void testUpdateCqlLibrarySuccessfullyUpdates() {
    final String pathId = "Library1_ID";
    final Instant createdTime = Instant.now().minus(100, ChronoUnit.MINUTES);
    final CqlLibrary existingLibrary = CqlLibrary.builder()
        .id("Library1_ID")
        .cqlLibraryName("Library1").model(ModelType.QI_CORE.getValue())
        .createdAt(createdTime)
        .createdBy("User1")
        .lastModifiedAt(createdTime)
        .lastModifiedBy("User1")
        .build();
    final CqlLibrary updatingLibrary = existingLibrary.toBuilder().id("Library1_ID").cqlLibraryName("NewName").build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingLibrary));
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(false);
    when(principal.getName()).thenReturn("User2");

    when(cqlLibraryRepository.save(any(CqlLibrary.class))).thenReturn(updatingLibrary);

    ResponseEntity<CqlLibrary> output = cqlLibraryController.updateCqlLibrary(pathId, updatingLibrary, principal);
    assertThat(output.getBody(), is(equalTo(updatingLibrary)));
    verify(cqlLibraryRepository, times(1)).save(cqlLibraryArgumentCaptor.capture());
    CqlLibrary savedValue = cqlLibraryArgumentCaptor.getValue();
    assertThat(savedValue, is(notNullValue()));
    assertThat(savedValue.getId(), is(equalTo("Library1_ID")));
    assertThat(savedValue.getCqlLibraryName(), is(equalTo("NewName")));
    assertThat(savedValue.getCreatedAt(), is(equalTo(createdTime)));
    assertThat(savedValue.getCreatedBy(), is(equalTo("User1")));
    assertThat(savedValue.getLastModifiedAt(), is(notNullValue()));
    assertThat(savedValue.getLastModifiedAt().isAfter(createdTime), is(true));
    assertThat(savedValue.getLastModifiedBy(), is(equalTo("User2")));
  }
}

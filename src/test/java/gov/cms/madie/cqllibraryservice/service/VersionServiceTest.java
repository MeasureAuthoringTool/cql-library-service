package gov.cms.madie.cqllibraryservice.service;

import gov.cms.madie.cqllibraryservice.exceptions.BadRequestObjectException;
import gov.cms.madie.cqllibraryservice.exceptions.PermissionDeniedException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.models.CqlLibrary;
import gov.cms.madie.cqllibraryservice.models.Version;
import gov.cms.madie.cqllibraryservice.respositories.CqlLibraryRepository;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

  @Mock CqlLibraryRepository cqlLibraryRepository;

  @InjectMocks VersionService versionService;

  @Captor private ArgumentCaptor<CqlLibrary> cqlLibraryArgumentCaptor;

  @Test
  void testCreateVersionThrowsExceptionForResourceNotFound() {
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> versionService.createVersion("testCqlLibraryId", true, "testUser"));
  }

  @Test
  void testCreateVersionThrowsExceptionWhenUserIsNotTheOwner() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder().id("testCqlLibraryId").createdBy("testUser").build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));

    assertThrows(
        PermissionDeniedException.class,
        () -> versionService.createVersion(existingCqlLibrary.getId(), true, "testUser1"));
  }

  @Test
  void testCreateVersionThrowsExceptionIfLibraryIsNotDraft() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder().id("testCqlLibraryId").createdBy("testUser").draft(false).build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));

    assertThrows(
        BadRequestObjectException.class,
        () -> versionService.createVersion(existingCqlLibrary.getId(), true, "testUser"));
  }

  @Test
  void testCreateVersionThrowsRunTimeExceptionIfGroupIdIsNull() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder().id("testCqlLibraryId").createdBy("testUser").draft(true).build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));

    assertThrows(
        RuntimeException.class,
        () -> versionService.createVersion(existingCqlLibrary.getId(), true, "testUser"));
  }

  @Test
  void testCreateVersionMajorSuccess() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .createdBy("testUser")
            .draft(true)
            .groupId("testGroupId")
            .version(Version.parse("1.0.000"))
            .build();

    CqlLibrary updatedCqlLibrary = existingCqlLibrary.toBuilder().build();
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));
    when(cqlLibraryRepository.findAll()).thenReturn(List.of(existingCqlLibrary));
    when(cqlLibraryRepository.save(any(CqlLibrary.class))).thenReturn(updatedCqlLibrary);

    versionService.createVersion("testCqlLibraryId", true, "testUser");
    verify(cqlLibraryRepository, times(1)).save(cqlLibraryArgumentCaptor.capture());
    CqlLibrary savedValue = cqlLibraryArgumentCaptor.getValue();

    assertFalse(savedValue.isDraft());
    assertThat(savedValue.getVersion().toString(), is(equalTo("2.0.000")));
    // groupId should remain same
    assertThat(savedValue.getGroupId(), is(equalTo(existingCqlLibrary.getGroupId())));
  }

  @Test
  void testCreateVersionMinorSuccess() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .createdBy("testUser")
            .draft(true)
            .groupId("testGroupId")
            .version(Version.parse("1.0.000"))
            .build();

    CqlLibrary updatedCqlLibrary = existingCqlLibrary.toBuilder().build();
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));
    when(cqlLibraryRepository.findAll()).thenReturn(List.of(existingCqlLibrary));
    when(cqlLibraryRepository.save(any(CqlLibrary.class))).thenReturn(updatedCqlLibrary);

    versionService.createVersion("testCqlLibraryId", false, "testUser");
    verify(cqlLibraryRepository, times(1)).save(cqlLibraryArgumentCaptor.capture());
    CqlLibrary savedValue = cqlLibraryArgumentCaptor.getValue();

    assertFalse(savedValue.isDraft());
    assertThat(savedValue.getVersion().toString(), is(equalTo("1.1.000")));
  }

  @Test
  void testCreateDraftThrowsExceptionForResourceNotFound() {
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> versionService.createVersion("testCqlLibraryId", true, "testUser"));
  }

  @Test
  void testCreateDraftThrowsExceptionForUserIsNotOwner() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder().id("testCqlLibraryId").createdBy("testUser").build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));

    assertThrows(
        PermissionDeniedException.class,
        () -> versionService.createVersion(existingCqlLibrary.getId(), true, "testUser1"));
  }

  @Test
  void testCreateDraftSuccess() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .createdBy("testUser")
            .draft(false)
            .groupId("testGroupId")
            .version(Version.parse("1.0.000"))
            .build();

    CqlLibrary clonedCqlLibrary = existingCqlLibrary.toBuilder().build();
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));
    when(cqlLibraryRepository.save(any(CqlLibrary.class))).thenReturn(clonedCqlLibrary);

    versionService.createDraft("testCqlLibraryId", "testNewCqlLibraryName", "testUser");
    verify(cqlLibraryRepository, times(1)).save(cqlLibraryArgumentCaptor.capture());
    CqlLibrary savedValue = cqlLibraryArgumentCaptor.getValue();

    assertThat(savedValue.getCqlLibraryName(), is(equalTo("testNewCqlLibraryName")));
    assertTrue(savedValue.isDraft());
    // version and groupId should not change
    assertThat(savedValue.getVersion(), is(equalTo(existingCqlLibrary.getVersion())));
    assertThat(savedValue.getGroupId(), is(equalTo(existingCqlLibrary.getGroupId())));
  }
}

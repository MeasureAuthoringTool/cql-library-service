package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.exceptions.*;
import gov.cms.madie.cqllibraryservice.models.CqlLibrary;
import gov.cms.madie.cqllibraryservice.models.Version;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;

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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

  @Mock CqlLibraryRepository cqlLibraryRepository;

  @Mock CqlLibraryService cqlLibraryService;

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
  void testCreateVersionThrowsExceptionWhenCqlHasErrors() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .createdBy("testUser")
            .draft(true)
            .cqlErrors(true)
            .build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));

    assertThrows(
        ResourceCannotBeVersionedException.class,
        () -> versionService.createVersion(existingCqlLibrary.getId(), true, "testUser"));
  }

  @Test
  void testCreateVersionThrowsExceptionWhenCqlisEmpty() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .createdBy("testUser")
            .draft(true)
            .cqlErrors(true)
            .cql("")
            .build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));

    assertThrows(
        ResourceCannotBeVersionedException.class,
        () -> versionService.createVersion(existingCqlLibrary.getId(), true, "testUser"));
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
            .cql("library testCql version '1.0.000'")
            .groupId("testGroupId")
            .version(Version.parse("1.0.000"))
            .build();

    CqlLibrary updatedCqlLibrary = existingCqlLibrary.toBuilder().build();
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));
    when(cqlLibraryRepository.findMaxVersionByGroupId(anyString()))
        .thenReturn(Optional.of(Version.parse("1.0.0")));
    //    when(cqlLibraryRepository.findAll()).thenReturn(List.of(existingCqlLibrary));
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
            .cql("library testCql version '1.0.000'")
            .draft(true)
            .groupId("testGroupId")
            .version(Version.parse("1.0.000"))
            .build();

    CqlLibrary updatedCqlLibrary = existingCqlLibrary.toBuilder().build();
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));
    when(cqlLibraryRepository.findMaxMinorVersionByGroupIdAndVersionMajor(anyString(), anyInt()))
        .thenReturn(Optional.of(Version.parse("1.0.0")));
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
        () -> versionService.createDraft("testCqlLibraryId", "Library1", "testUser"));
  }

  @Test
  void testCreateDraftThrowsExceptionForUserIsNotOwner() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder().id("testCqlLibraryId").createdBy("testUser").build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));
    doNothing().when(cqlLibraryService).checkDuplicateCqlLibraryName(anyString());

    assertThrows(
        PermissionDeniedException.class,
        () -> versionService.createDraft(existingCqlLibrary.getId(), "Library1", "testUser1"));
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
    doNothing().when(cqlLibraryService).checkDuplicateCqlLibraryName(anyString());
    when(cqlLibraryRepository.existsByGroupIdAndDraft(anyString(), anyBoolean())).thenReturn(false);

    versionService.createDraft("testCqlLibraryId", "testNewCqlLibraryName", "testUser");
    verify(cqlLibraryRepository, times(1)).save(cqlLibraryArgumentCaptor.capture());
    CqlLibrary savedValue = cqlLibraryArgumentCaptor.getValue();

    assertThat(savedValue.getCqlLibraryName(), is(equalTo("testNewCqlLibraryName")));
    assertTrue(savedValue.isDraft());
    // version and groupId should not change
    assertThat(savedValue.getVersion(), is(equalTo(existingCqlLibrary.getVersion())));
    assertThat(savedValue.getGroupId(), is(equalTo(existingCqlLibrary.getGroupId())));
  }

  @Test
  void testCreateDraftThrowsExceptionWhenDraftAlreadyExists() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .createdBy("testUser")
            .draft(false)
            .groupId("testGroupId")
            .version(Version.parse("1.0.000"))
            .build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));
    doNothing().when(cqlLibraryService).checkDuplicateCqlLibraryName(anyString());
    when(cqlLibraryRepository.existsByGroupIdAndDraft(anyString(), anyBoolean())).thenReturn(true);

    assertThrows(
        ResourceNotDraftableException.class,
        () -> versionService.createDraft("testCqlLibraryId", "testNewCqlLibraryName", "testUser"));
  }

  @Test
  public void testGetNextVersionReturnsIncrementedMajorVersion() {
    CqlLibrary lib = CqlLibrary.builder().groupId("group1").version(Version.parse("1.0.0")).build();
    when(cqlLibraryRepository.findMaxVersionByGroupId(anyString()))
        .thenReturn(Optional.of(Version.parse("1.0.0")));
    Version nextVersion = versionService.getNextVersion(lib, true);
    assertThat(nextVersion, is(equalTo(Version.parse("2.0.0"))));
  }

  @Test
  public void testGetNextVersionReturnsIncrementedMajorVersionIgnoringMinor() {
    CqlLibrary lib = CqlLibrary.builder().groupId("group1").version(Version.parse("1.0.0")).build();
    when(cqlLibraryRepository.findMaxVersionByGroupId(anyString()))
        .thenReturn(Optional.of(Version.parse("2.4.0")));
    Version nextVersion = versionService.getNextVersion(lib, true);
    assertThat(nextVersion, is(equalTo(Version.parse("3.0.0"))));
  }

  @Test
  public void testGetNextVersionReturnsIncrementedMinorVersion() {
    CqlLibrary lib = CqlLibrary.builder().groupId("group1").version(Version.parse("1.2.0")).build();
    when(cqlLibraryRepository.findMaxMinorVersionByGroupIdAndVersionMajor(anyString(), anyInt()))
        .thenReturn(Optional.of(Version.parse("1.3.0")));
    Version nextVersion = versionService.getNextVersion(lib, false);
    assertThat(nextVersion, is(equalTo(Version.parse("1.4.0"))));
  }

  @Test
  public void testRuntimeExceptionIsRethrownForMaxVersion() {
    Exception cause = new RuntimeException("The CAUSE!!");
    when(cqlLibraryRepository.findMaxVersionByGroupId(anyString())).thenThrow(cause);
    CqlLibrary lib = CqlLibrary.builder().groupId("group1").version(Version.parse("1.0.0")).build();
    assertThrows(
        RuntimeException.class,
        () -> versionService.getNextVersion(lib, true),
        "Unable to update version number");
    verify(cqlLibraryRepository, times(1)).findMaxVersionByGroupId(anyString());
  }

  @Test
  public void testRuntimeExceptionIsRethrownForMaxMinorVersion() {
    Exception cause = new RuntimeException("The CAUSE!!");
    when(cqlLibraryRepository.findMaxMinorVersionByGroupIdAndVersionMajor(anyString(), anyInt()))
        .thenThrow(cause);
    CqlLibrary lib = CqlLibrary.builder().groupId("group1").version(Version.parse("1.0.0")).build();
    assertThrows(
        InternalServerErrorException.class,
        () -> versionService.getNextVersion(lib, false),
        "Unable to update version number");
    verify(cqlLibraryRepository, times(1))
        .findMaxMinorVersionByGroupIdAndVersionMajor(anyString(), anyInt());
  }

  @Test
  public void testIsDraftableReturnsTrueForNull() {
    boolean output = versionService.isDraftable(null);
    assertThat(output, is(true));
  }

  @Test
  public void testIsDraftableReturnsTrueForNoExistingDraft() {
    when(cqlLibraryRepository.existsByGroupIdAndDraft(anyString(), anyBoolean())).thenReturn(false);
    boolean output = versionService.isDraftable(CqlLibrary.builder().groupId("group1").build());
    assertThat(output, is(true));
  }

  @Test
  public void testIsDraftableReturnsFalseForExistingDraft() {
    when(cqlLibraryRepository.existsByGroupIdAndDraft(anyString(), anyBoolean())).thenReturn(true);
    boolean output = versionService.isDraftable(CqlLibrary.builder().groupId("group1").build());
    assertThat(output, is(false));
  }
}

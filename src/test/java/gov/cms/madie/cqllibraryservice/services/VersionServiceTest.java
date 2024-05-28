package gov.cms.madie.cqllibraryservice.services;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import gov.cms.madie.cqllibraryservice.exceptions.*;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;
import gov.cms.madie.models.measure.ElmJson;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

  @Mock CqlLibraryRepository cqlLibraryRepository;

  @Mock CqlLibraryService cqlLibraryService;

  @Mock ElmTranslatorClient elmTranslatorClient;

  @Mock ActionLogService actionLogService;

  @InjectMocks VersionService versionService;

  @Captor private ArgumentCaptor<CqlLibrary> cqlLibraryArgumentCaptor;

  @Captor private ArgumentCaptor<ActionType> actionTypeArgumentCaptor;

  @Captor private ArgumentCaptor<String> targetIdArgumentCaptor;

  @Test
  void testCreateVersionThrowsExceptionForResourceNotFound() {
    doThrow(new ResourceNotFoundException("CQL Library", "testCqlLibraryId"))
        .when(cqlLibraryService)
        .findCqlLibraryById(anyString());
    assertThrows(
        ResourceNotFoundException.class,
        () -> versionService.createVersion("testCqlLibraryId", true, "testUser", "accesstoken"));
  }

  @Test
  void testCreateVersionThrowsExceptionWhenUserIsNotTheOwner() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .cqlLibraryName("TestName1")
            .createdBy("testUser")
            .librarySetId("test")
            .build();
    doThrow(new PermissionDeniedException("CQL Library", "TestName1", "testUser1"))
        .when(cqlLibraryService)
        .findCqlLibraryById(anyString());

    assertThrows(
        PermissionDeniedException.class,
        () ->
            versionService.createVersion(
                existingCqlLibrary.getId(), true, "testUser1", "accesstoken"));
  }

  @Test
  void testCreateVersionThrowsExceptionWhenCqlHasErrors() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .createdBy("testUser")
            .librarySetId("test")
            .draft(true)
            .cqlErrors(true)
            .librarySet(
                LibrarySet.builder().librarySetId("testLibrarySetId").owner("testUser").build())
            .build();
    AclSpecification acl = new AclSpecification();
    acl.setUserId("testUser");
    acl.setRoles(List.of(RoleEnum.SHARED_WITH));

    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingCqlLibrary);
    assertThrows(
        ResourceCannotBeVersionedException.class,
        () ->
            versionService.createVersion(
                existingCqlLibrary.getId(), true, "testUser", "accesstoken"));
  }

  @Test
  void testCreateVersionThrowsExceptionWhenCqlisEmpty() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .createdBy("testUser")
            .librarySetId("id")
            .draft(true)
            .cqlErrors(true)
            .cql("")
            .librarySet(
                LibrarySet.builder().librarySetId("testLibrarySetId").owner("testUser").build())
            .build();

    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingCqlLibrary);
    assertThrows(
        ResourceCannotBeVersionedException.class,
        () ->
            versionService.createVersion(
                existingCqlLibrary.getId(), true, "testUser", "accesstoken"));
  }

  @Test
  void testCreateVersionThrowsExceptionIfLibraryIsNotDraft() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .createdBy("testUser")
            .librarySetId("id")
            .draft(false)
            .librarySet(
                LibrarySet.builder().librarySetId("testLibrarySetId").owner("testUser").build())
            .build();
    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingCqlLibrary);

    assertThrows(
        BadRequestObjectException.class,
        () ->
            versionService.createVersion(
                existingCqlLibrary.getId(), true, "testUser", "accesstoken"));
  }

  @Test
  void testCreateVersionThrowsRunTimeExceptionIfLibrarySetIDIsNull() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .createdBy("testUser")
            .librarySetId("id")
            .draft(true)
            .build();
    AclSpecification acl = new AclSpecification();
    acl.setUserId("testUser");
    acl.setRoles(List.of(RoleEnum.SHARED_WITH));
    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingCqlLibrary);

    assertThrows(
        RuntimeException.class,
        () ->
            versionService.createVersion(
                existingCqlLibrary.getId(), true, "testUser", "accesstoken"));
  }

  @Test
  void testCreateVersionThrowsElmTranslatorErrorException() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .createdBy("testUser")
            .cqlLibraryName("TestLibrary")
            .draft(true)
            .cql("library testCql version '1.0.000'")
            .model(ModelType.QI_CORE.getValue())
            .librarySetId("testLibrarySetId1")
            .version(Version.parse("1.0.000"))
            .librarySet(
                LibrarySet.builder().librarySetId("testLibrarySetId1").owner("testUser").build())
            .build();

    AclSpecification acl = new AclSpecification();
    acl.setUserId("testUser");
    acl.setRoles(List.of(RoleEnum.SHARED_WITH));
    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingCqlLibrary);
    when(cqlLibraryRepository.findMaxVersionByLibrarySetId(anyString()))
        .thenReturn(Optional.of(Version.parse("1.0.0")));
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{}").xml("<></>").build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(true);
    assertThrows(
        CqlElmTranslationErrorException.class,
        () -> versionService.createVersion("testCqlLibraryId", true, "testUser", "accesstoken"));
  }

  @Test
  void testCreateVersionHandlesHasErrorsException() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .createdBy("testUser")
            .cqlLibraryName("TestLibrary")
            .draft(true)
            .cql("library testCql version '1.0.000'")
            .model(ModelType.QI_CORE.getValue())
            .librarySetId("testLibrarySetId")
            .version(Version.parse("1.0.000"))
            .librarySet(
                LibrarySet.builder().librarySetId("testLibrarySetId").owner("testUser").build())
            .build();
    AclSpecification acl = new AclSpecification();
    acl.setUserId("testUser");
    acl.setRoles(List.of(RoleEnum.SHARED_WITH));
    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingCqlLibrary);
    when(cqlLibraryRepository.findMaxVersionByLibrarySetId(anyString()))
        .thenReturn(Optional.of(Version.parse("1.0.0")));
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{}").xml("<></>").build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class)))
        .thenThrow(
            new CqlElmTranslationServiceException("TEST_ERROR", new RuntimeException("CAUSE")));
    assertThrows(
        CqlElmTranslationServiceException.class,
        () -> versionService.createVersion("testCqlLibraryId", true, "testUser", "accesstoken"));
  }

  @Test
  void testCreateVersionMajorSuccess() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .createdBy("testUser")
            .draft(true)
            .cql("library testCql version '1.0.000'")
            .librarySetId("testLibrarySetId")
            .version(Version.parse("1.0.000"))
            .model(ModelType.QI_CORE.toString())
            .librarySet(
                LibrarySet.builder().librarySetId("testLibrarySetId").owner("testUser").build())
            .build();

    CqlLibrary updatedCqlLibrary = existingCqlLibrary.toBuilder().build();
    AclSpecification acl = new AclSpecification();
    acl.setUserId("testUser");
    acl.setRoles(List.of(RoleEnum.SHARED_WITH));
    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingCqlLibrary);
    when(cqlLibraryRepository.findMaxVersionByLibrarySetId(anyString()))
        .thenReturn(Optional.of(Version.parse("1.0.0")));
    when(cqlLibraryRepository.save(any(CqlLibrary.class))).thenReturn(updatedCqlLibrary);
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{}").xml("<></>").build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(false);
    versionService.createVersion("testCqlLibraryId", true, "testUser", "accesstoken");

    verify(cqlLibraryRepository, times(1)).save(cqlLibraryArgumentCaptor.capture());
    CqlLibrary savedValue = cqlLibraryArgumentCaptor.getValue();

    assertFalse(savedValue.isDraft());
    assertThat(savedValue.getVersion().toString(), is(equalTo("2.0.000")));
    // groupId should remain same
    assertThat(savedValue.getLibrarySetId(), is(equalTo(existingCqlLibrary.getLibrarySetId())));

    verify(actionLogService, times(1))
        .logAction(
            targetIdArgumentCaptor.capture(), actionTypeArgumentCaptor.capture(), anyString());
    assertThat(targetIdArgumentCaptor.getValue(), is(equalTo("testLibrarySetId")));
    assertThat(actionTypeArgumentCaptor.getValue(), is(equalTo(ActionType.VERSIONED_MAJOR)));
  }

  @Test
  void testCreateVersionMinorSuccess() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .createdBy("testUser")
            .cql("library testCql version '1.0.000'")
            .draft(true)
            .librarySetId("testLibrarySetId")
            .version(Version.parse("1.0.000"))
            .model(ModelType.QI_CORE.toString())
            .librarySet(
                LibrarySet.builder().librarySetId("testLibrarySetId").owner("testUser").build())
            .build();

    CqlLibrary updatedCqlLibrary = existingCqlLibrary.toBuilder().build();
    AclSpecification acl = new AclSpecification();
    acl.setUserId("testUser");
    acl.setRoles(List.of(RoleEnum.SHARED_WITH));
    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingCqlLibrary);
    when(cqlLibraryRepository.findMaxMinorVersionByLibrarySetIdAndVersionMajor(
            anyString(), anyInt()))
        .thenReturn(Optional.of(Version.parse("1.0.0")));
    when(cqlLibraryRepository.save(any(CqlLibrary.class))).thenReturn(updatedCqlLibrary);
    when(elmTranslatorClient.getElmJson(anyString(), anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{}").xml("<></>").build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(false);
    versionService.createVersion("testCqlLibraryId", false, "testUser", "accesstoken");
    verify(cqlLibraryRepository, times(1)).save(cqlLibraryArgumentCaptor.capture());
    CqlLibrary savedValue = cqlLibraryArgumentCaptor.getValue();

    assertFalse(savedValue.isDraft());
    assertThat(savedValue.getVersion().toString(), is(equalTo("1.1.000")));

    verify(actionLogService, times(1))
        .logAction(
            targetIdArgumentCaptor.capture(), actionTypeArgumentCaptor.capture(), anyString());
    assertThat(targetIdArgumentCaptor.getValue(), is(equalTo("testLibrarySetId")));
    assertThat(actionTypeArgumentCaptor.getValue(), is(equalTo(ActionType.VERSIONED_MINOR)));
  }

  @Test
  void testCreateDraftThrowsExceptionForResourceNotFound() {
    doThrow(new ResourceNotFoundException("CQL Library", "Library1"))
        .when(cqlLibraryService)
        .findCqlLibraryById(anyString());
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            versionService.createDraft(
                "testCqlLibraryId", "Library1", "library testCql version '1.0.000'", "testUser"));
  }

  @Test
  void testCreateDraftThrowsExceptionForAuthorization() {
    AclSpecification aclSpecification = new AclSpecification();
    aclSpecification.setUserId("sharedUser");
    aclSpecification.setRoles(List.of(RoleEnum.SHARED_WITH));

    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .createdBy("testUser")
            .draft(false)
            .librarySetId("testLibrarySetId")
            .version(Version.parse("1.0.000"))
            .librarySet(
                LibrarySet.builder()
                    .librarySetId("testLibrarySetId")
                    .owner("testUser")
                    .acls(List.of(aclSpecification))
                    .build())
            .build();
    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingCqlLibrary);
    doNothing().when(cqlLibraryService).checkDuplicateCqlLibraryName(anyString());

    assertThrows(
        PermissionDeniedException.class,
        () ->
            versionService.createDraft(
                "testCqlLibraryId", "Library1", "library testCql version '1.0.000'", "randomUser"));
  }

  @Test
  void testCreateDraftSuccesfullyDraftsForSharedUser() {
    AclSpecification aclSpecification = new AclSpecification();
    aclSpecification.setUserId("sharedUser");
    aclSpecification.setRoles(List.of(RoleEnum.SHARED_WITH));

    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .createdBy("testUser")
            .draft(false)
            .librarySetId("testLibrarySetId")
            .version(Version.parse("1.0.000"))
            .librarySet(
                LibrarySet.builder()
                    .librarySetId("testLibrarySetId")
                    .owner("testUser")
                    .acls(List.of(aclSpecification))
                    .build())
            .build();

    CqlLibrary clonedCqlLibrary = existingCqlLibrary.toBuilder().build();
    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingCqlLibrary);
    when(cqlLibraryRepository.save(any(CqlLibrary.class))).thenReturn(clonedCqlLibrary);
    doNothing().when(cqlLibraryService).checkDuplicateCqlLibraryName(anyString());
    when(cqlLibraryRepository.existsByLibrarySetIdAndDraft(anyString(), anyBoolean()))
        .thenReturn(false);

    versionService.createDraft(
        "testCqlLibraryId",
        "testNewCqlLibraryName",
        "library testCql version '1.0.000'",
        "sharedUser");
    verify(cqlLibraryRepository, times(1)).save(cqlLibraryArgumentCaptor.capture());
    CqlLibrary savedValue = cqlLibraryArgumentCaptor.getValue();

    assertThat(savedValue.getCqlLibraryName(), is(equalTo("testNewCqlLibraryName")));
    assertTrue(savedValue.isDraft());
    // version and groupId should not change
    assertThat(savedValue.getVersion(), is(equalTo(existingCqlLibrary.getVersion())));
    assertThat(savedValue.getLibrarySetId(), is(equalTo(existingCqlLibrary.getLibrarySetId())));
  }

  @Test
  void testCreateDraftSuccess() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .createdBy("testUser")
            .draft(false)
            .librarySetId("testLibrarySetId")
            .version(Version.parse("1.0.000"))
            .librarySet(
                LibrarySet.builder().librarySetId("testLibrarySetId").owner("testUser").build())
            .build();

    CqlLibrary clonedCqlLibrary = existingCqlLibrary.toBuilder().build();
    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingCqlLibrary);
    when(cqlLibraryRepository.save(any(CqlLibrary.class))).thenReturn(clonedCqlLibrary);
    doNothing().when(cqlLibraryService).checkDuplicateCqlLibraryName(anyString());
    when(cqlLibraryRepository.existsByLibrarySetIdAndDraft(anyString(), anyBoolean()))
        .thenReturn(false);

    versionService.createDraft(
        "testCqlLibraryId",
        "testNewCqlLibraryName",
        "library testCql version '1.0.000'",
        "testUser");
    verify(cqlLibraryRepository, times(1)).save(cqlLibraryArgumentCaptor.capture());
    CqlLibrary savedValue = cqlLibraryArgumentCaptor.getValue();

    assertThat(savedValue.getCqlLibraryName(), is(equalTo("testNewCqlLibraryName")));
    assertTrue(savedValue.isDraft());
    // version and groupId should not change
    assertThat(savedValue.getVersion(), is(equalTo(existingCqlLibrary.getVersion())));
    assertThat(savedValue.getLibrarySetId(), is(equalTo(existingCqlLibrary.getLibrarySetId())));
  }

  @Test
  void testCreateDraftThrowsExceptionWhenDraftAlreadyExists() {
    AclSpecification aclSpecification = new AclSpecification();
    aclSpecification.setUserId("sharedUser");
    aclSpecification.setRoles(List.of(RoleEnum.SHARED_WITH));

    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder()
            .id("testCqlLibraryId")
            .createdBy("testUser")
            .draft(false)
            .librarySetId("testLibrarySetId")
            .version(Version.parse("1.0.000"))
            .librarySet(
                LibrarySet.builder()
                    .librarySetId("testLibrarySetId")
                    .owner("testUser")
                    .acls(List.of(aclSpecification))
                    .build())
            .build();

    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingCqlLibrary);
    doNothing().when(cqlLibraryService).checkDuplicateCqlLibraryName(anyString());
    when(cqlLibraryRepository.existsByLibrarySetIdAndDraft(anyString(), anyBoolean()))
        .thenReturn(true);

    assertThrows(
        ResourceNotDraftableException.class,
        () ->
            versionService.createDraft(
                "testCqlLibraryId",
                "testNewCqlLibraryName",
                "library testCql version '1.0.000'",
                "testUser"));
  }

  @Test
  public void testGetNextVersionReturnsIncrementedMajorVersion() {
    CqlLibrary lib =
        CqlLibrary.builder().librarySetId("librarySet1").version(Version.parse("1.0.0")).build();
    when(cqlLibraryRepository.findMaxVersionByLibrarySetId(anyString()))
        .thenReturn(Optional.of(Version.parse("1.0.0")));
    Version nextVersion = versionService.getNextVersion(lib, true);
    assertThat(nextVersion, is(equalTo(Version.parse("2.0.0"))));
  }

  @Test
  public void testGetNextVersionReturnsIncrementedMajorVersionIgnoringMinor() {
    CqlLibrary lib =
        CqlLibrary.builder().librarySetId("librarySet1").version(Version.parse("1.0.0")).build();
    when(cqlLibraryRepository.findMaxVersionByLibrarySetId(anyString()))
        .thenReturn(Optional.of(Version.parse("2.4.0")));
    Version nextVersion = versionService.getNextVersion(lib, true);
    assertThat(nextVersion, is(equalTo(Version.parse("3.0.0"))));
  }

  @Test
  public void testGetNextVersionReturnsIncrementedMinorVersion() {
    CqlLibrary lib =
        CqlLibrary.builder().librarySetId("librarySet1").version(Version.parse("1.2.0")).build();
    when(cqlLibraryRepository.findMaxMinorVersionByLibrarySetIdAndVersionMajor(
            anyString(), anyInt()))
        .thenReturn(Optional.of(Version.parse("1.3.0")));
    Version nextVersion = versionService.getNextVersion(lib, false);
    assertThat(nextVersion, is(equalTo(Version.parse("1.4.0"))));
  }

  @Test
  public void testRuntimeExceptionIsRethrownForMaxVersion() {
    Exception cause = new RuntimeException("The CAUSE!!");
    when(cqlLibraryRepository.findMaxVersionByLibrarySetId(anyString())).thenThrow(cause);
    CqlLibrary lib =
        CqlLibrary.builder().librarySetId("librarySet1").version(Version.parse("1.0.0")).build();
    assertThrows(
        RuntimeException.class,
        () -> versionService.getNextVersion(lib, true),
        "Unable to update version number");
    verify(cqlLibraryRepository, times(1)).findMaxVersionByLibrarySetId(anyString());
  }

  @Test
  public void testRuntimeExceptionIsRethrownForMaxMinorVersion() {
    Exception cause = new RuntimeException("The CAUSE!!");
    when(cqlLibraryRepository.findMaxMinorVersionByLibrarySetIdAndVersionMajor(
            anyString(), anyInt()))
        .thenThrow(cause);
    CqlLibrary lib =
        CqlLibrary.builder().librarySetId("librarySet1").version(Version.parse("1.0.0")).build();
    assertThrows(
        InternalServerErrorException.class,
        () -> versionService.getNextVersion(lib, false),
        "Unable to update version number");
    verify(cqlLibraryRepository, times(1))
        .findMaxMinorVersionByLibrarySetIdAndVersionMajor(anyString(), anyInt());
  }

  @Test
  public void testIsDraftableReturnsTrueForNull() {
    boolean output = versionService.isDraftable(null);
    assertThat(output, is(true));
  }

  @Test
  public void testIsDraftableReturnsTrueForNoExistingDraft() {
    when(cqlLibraryRepository.existsByLibrarySetIdAndDraft(anyString(), anyBoolean()))
        .thenReturn(false);
    boolean output =
        versionService.isDraftable(CqlLibrary.builder().librarySetId("librarySet1").build());
    assertThat(output, is(true));
  }

  @Test
  public void testIsDraftableReturnsFalseForExistingDraft() {
    when(cqlLibraryRepository.existsByLibrarySetIdAndDraft(anyString(), anyBoolean()))
        .thenReturn(true);
    boolean output =
        versionService.isDraftable(CqlLibrary.builder().librarySetId("librarySet1").build());
    assertThat(output, is(false));
  }
}

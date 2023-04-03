package gov.cms.madie.cqllibraryservice.services;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import gov.cms.madie.cqllibraryservice.exceptions.*;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.measure.ElmJson;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

  @Mock CqlLibraryRepository cqlLibraryRepository;

  @Mock CqlLibraryService cqlLibraryService;

  @Mock RestTemplate restTemplate;

  @Mock ElmTranslatorClient elmTranslatorClient;

  @Mock ActionLogService actionLogService;

  @InjectMocks VersionService versionService;

  @Captor private ArgumentCaptor<CqlLibrary> cqlLibraryArgumentCaptor;

  @Captor private ArgumentCaptor<ActionType> actionTypeArgumentCaptor;

  @Captor private ArgumentCaptor<String> targetIdArgumentCaptor;

  @BeforeEach
  public void setup() {
    ReflectionTestUtils.setField(versionService, "madieFhirService", "http://test.hapiFhir");
    ReflectionTestUtils.setField(versionService, "librariesUri", "/fhir/libraries");
  }

  @Test
  void testCreateVersionThrowsExceptionForResourceNotFound() {
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> versionService.createVersion("testCqlLibraryId", true, "testUser", "accesstoken"));
  }

  @Test
  void testCreateVersionThrowsExceptionWhenUserIsNotTheOwner() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder().id("testCqlLibraryId").createdBy("testUser").build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));

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
            .draft(true)
            .cqlErrors(true)
            .build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));

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
            .draft(true)
            .cqlErrors(true)
            .cql("")
            .build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));

    assertThrows(
        ResourceCannotBeVersionedException.class,
        () ->
            versionService.createVersion(
                existingCqlLibrary.getId(), true, "testUser", "accesstoken"));
  }

  @Test
  void testCreateVersionThrowsExceptionIfLibraryIsNotDraft() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder().id("testCqlLibraryId").createdBy("testUser").draft(false).build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));

    assertThrows(
        BadRequestObjectException.class,
        () ->
            versionService.createVersion(
                existingCqlLibrary.getId(), true, "testUser", "accesstoken"));
  }

  @Test
  void testCreateVersionThrowsRunTimeExceptionIfGroupIdIsNull() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder().id("testCqlLibraryId").createdBy("testUser").draft(true).build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));

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
            .groupId("testGroupId")
            .version(Version.parse("1.0.000"))
            .build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));
    when(cqlLibraryRepository.findMaxVersionByGroupId(anyString()))
        .thenReturn(Optional.of(Version.parse("1.0.0")));
    when(elmTranslatorClient.getElmJson(anyString(), anyString()))
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
            .groupId("testGroupId")
            .version(Version.parse("1.0.000"))
            .build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));
    when(cqlLibraryRepository.findMaxVersionByGroupId(anyString()))
        .thenReturn(Optional.of(Version.parse("1.0.0")));
    when(elmTranslatorClient.getElmJson(anyString(), anyString()))
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
            .groupId("testGroupId")
            .version(Version.parse("1.0.000"))
            .model(ModelType.QI_CORE.toString())
            .build();

    CqlLibrary updatedCqlLibrary = existingCqlLibrary.toBuilder().build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));
    when(cqlLibraryRepository.findMaxVersionByGroupId(anyString()))
        .thenReturn(Optional.of(Version.parse("1.0.0")));
    //    when(cqlLibraryRepository.findAll()).thenReturn(List.of(existingCqlLibrary));
    when(cqlLibraryRepository.save(any(CqlLibrary.class))).thenReturn(updatedCqlLibrary);
    when(restTemplate.exchange(
            any(URI.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(ResponseEntity.ok("http://Library"));
    when(elmTranslatorClient.getElmJson(anyString(), anyString()))
        .thenReturn(ElmJson.builder().json("{}").xml("<></>").build());
    when(elmTranslatorClient.hasErrors(any(ElmJson.class))).thenReturn(false);
    versionService.createVersion("testCqlLibraryId", true, "testUser", "accesstoken");

    verify(cqlLibraryRepository, times(1)).save(cqlLibraryArgumentCaptor.capture());
    CqlLibrary savedValue = cqlLibraryArgumentCaptor.getValue();

    assertFalse(savedValue.isDraft());
    assertThat(savedValue.getVersion().toString(), is(equalTo("2.0.000")));
    // groupId should remain same
    assertThat(savedValue.getGroupId(), is(equalTo(existingCqlLibrary.getGroupId())));

    verify(actionLogService, times(1))
        .logAction(
            targetIdArgumentCaptor.capture(), actionTypeArgumentCaptor.capture(), anyString());
    assertThat(targetIdArgumentCaptor.getValue(), is(equalTo("testGroupId")));
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
            .groupId("testGroupId")
            .version(Version.parse("1.0.000"))
            .model(ModelType.QI_CORE.toString())
            .build();

    CqlLibrary updatedCqlLibrary = existingCqlLibrary.toBuilder().build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));
    when(cqlLibraryRepository.findMaxMinorVersionByGroupIdAndVersionMajor(anyString(), anyInt()))
        .thenReturn(Optional.of(Version.parse("1.0.0")));
    when(cqlLibraryRepository.save(any(CqlLibrary.class))).thenReturn(updatedCqlLibrary);
    when(restTemplate.exchange(
            any(URI.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class)))
        .thenReturn(ResponseEntity.ok("http://Library"));
    when(elmTranslatorClient.getElmJson(anyString(), anyString()))
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
    assertThat(targetIdArgumentCaptor.getValue(), is(equalTo("testGroupId")));
    assertThat(actionTypeArgumentCaptor.getValue(), is(equalTo(ActionType.VERSIONED_MINOR)));
  }

  @Test
  void testCreateDraftThrowsExceptionForResourceNotFound() {
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            versionService.createDraft(
                "testCqlLibraryId", "Library1", "library testCql version '1.0.000'", "testUser"));
  }

  @Test
  void testCreateDraftThrowsExceptionForUserIsNotOwner() {
    CqlLibrary existingCqlLibrary =
        CqlLibrary.builder().id("testCqlLibraryId").createdBy("testUser").build();

    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.of(existingCqlLibrary));
    doNothing().when(cqlLibraryService).checkDuplicateCqlLibraryName(anyString());

    assertThrows(
        PermissionDeniedException.class,
        () ->
            versionService.createDraft(
                existingCqlLibrary.getId(),
                "Library1",
                "library testCql version '1.0.000'",
                "testUser1"));
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
        () ->
            versionService.createDraft(
                "testCqlLibraryId",
                "testNewCqlLibraryName",
                "library testCql version '1.0.000'",
                "testUser"));
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

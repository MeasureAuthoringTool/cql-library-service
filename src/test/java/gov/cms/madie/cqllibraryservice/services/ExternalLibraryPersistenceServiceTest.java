package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.models.ExternalLibrary;
import gov.cms.madie.cqllibraryservice.repositories.ExternalLibraryRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.library.LibrarySet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalLibraryPersistenceServiceTest {

  @InjectMocks private ExternalLibraryPersistenceService persistenceService;
  @Mock private ExternalLibraryRepository externalLibraryRepository;
  @Mock private LibrarySetRepository librarySetRepository;
  @Mock private ActionLogService actionLogService;

  private static final String NAMESPACE = "http://hl7.org/fhir/us/qicore";
  private static final String LIBRARY_NAME = "FHIRHelpers";
  private static final String VERSION = "4.3.000";

  private ExternalLibrary buildCandidate(String libraryName, String version) {
    return ExternalLibrary.builder()
        .libraryName(libraryName)
        .version(version)
        .packageCanonical(NAMESPACE)
        .namespacePrefix("hl7.fhir.us.qicore")
        .cqlContent("library " + libraryName + " version '" + version + "'")
        .build();
  }

  // ---------------------------------------------------------------------------
  // persistLibraries tests
  // ---------------------------------------------------------------------------

  @Test
  void persistLibrariesCreateNew() {
    ExternalLibrary candidate = buildCandidate(LIBRARY_NAME, VERSION);
    when(externalLibraryRepository.existsByPackageCanonicalAndLibraryNameAndVersion(
            NAMESPACE, LIBRARY_NAME, VERSION))
        .thenReturn(false);
    when(externalLibraryRepository.findByPackageCanonicalAndLibraryName(NAMESPACE, LIBRARY_NAME))
        .thenReturn(Optional.empty());
    when(librarySetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(externalLibraryRepository.save(any())).thenReturn(candidate);

    int count = persistenceService.persistLibraries(List.of(candidate));

    assertThat(count).isEqualTo(1);
    assertThat(candidate.getLibrarySetId()).isNotBlank();
    verify(librarySetRepository, times(1)).save(any(LibrarySet.class));
    verify(actionLogService, times(1))
        .logAction(
            anyString(), eq(ActionType.CREATED), nullable(String.class), eq("librarySetActionLog"));
    verify(externalLibraryRepository, times(1)).save(any());
  }

  @Test
  void persistLibrariesDuplicateLibraryIsSkipped() {
    ExternalLibrary candidate = buildCandidate(LIBRARY_NAME, VERSION);
    when(externalLibraryRepository.existsByPackageCanonicalAndLibraryNameAndVersion(
            NAMESPACE, LIBRARY_NAME, VERSION))
        .thenReturn(true);

    int count = persistenceService.persistLibraries(List.of(candidate));

    assertThat(count).isEqualTo(0);
    verify(externalLibraryRepository, never()).save(any());
  }

  @Test
  void persistLibrariesMultipleVersionsOfSameLibrary() {
    ExternalLibrary v1 = buildCandidate(LIBRARY_NAME, "4.1.000");
    ExternalLibrary v2 = buildCandidate(LIBRARY_NAME, "4.3.000");
    ExternalLibrary existingLibrary = buildCandidate(LIBRARY_NAME, "4.0.000");
    existingLibrary.setLibrarySetId("set-abc");

    when(externalLibraryRepository.existsByPackageCanonicalAndLibraryNameAndVersion(
            anyString(), eq(LIBRARY_NAME), anyString()))
        .thenReturn(false);
    when(externalLibraryRepository.findByPackageCanonicalAndLibraryName(NAMESPACE, LIBRARY_NAME))
        .thenReturn(Optional.of(existingLibrary));
    when(externalLibraryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    int count = persistenceService.persistLibraries(List.of(v1, v2));

    assertThat(count).isEqualTo(2);
    verify(externalLibraryRepository, times(2)).save(any());
    // Library set should be reused, not recreated
    verify(librarySetRepository, never()).save(any());
  }

  // ---------------------------------------------------------------------------
  // findOrCreateLibrarySet tests
  // ---------------------------------------------------------------------------

  @Test
  void findOrCreateLibrarySetExistingSetIsReused() {
    ExternalLibrary existing = buildCandidate(LIBRARY_NAME, VERSION);
    existing.setLibrarySetId("existing-id");
    when(externalLibraryRepository.findByPackageCanonicalAndLibraryName(NAMESPACE, LIBRARY_NAME))
        .thenReturn(Optional.of(existing));

    String id = persistenceService.findOrCreateLibrarySet(NAMESPACE, LIBRARY_NAME);

    assertThat(id).isEqualTo("existing-id");
    verify(librarySetRepository, never()).save(any());
    verify(actionLogService, never()).logAction(anyString(), any(), anyString(), anyString());
  }

  @Test
  void findOrCreateLibrarySetNoExistingSetCreatesNew() {
    when(externalLibraryRepository.findByPackageCanonicalAndLibraryName(NAMESPACE, LIBRARY_NAME))
        .thenReturn(Optional.empty());
    when(librarySetRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    String id = persistenceService.findOrCreateLibrarySet(NAMESPACE, LIBRARY_NAME);

    assertThat(id).isNotBlank();
    ArgumentCaptor<LibrarySet> captor = ArgumentCaptor.forClass(LibrarySet.class);
    verify(librarySetRepository).save(captor.capture());
    assertThat(captor.getValue().getLibrarySetId()).isEqualTo(id);
    verify(actionLogService, times(1))
        .logAction(
            eq(id), eq(ActionType.CREATED), nullable(String.class), eq("librarySetActionLog"));
  }

  @Test
  void findOrCreateLibrarySetDuplicateNotCreatedWhenCalledTwice() {
    ExternalLibrary existing = buildCandidate(LIBRARY_NAME, VERSION);
    existing.setLibrarySetId("set-id");
    when(externalLibraryRepository.findByPackageCanonicalAndLibraryName(NAMESPACE, LIBRARY_NAME))
        .thenReturn(Optional.of(existing));

    persistenceService.findOrCreateLibrarySet(NAMESPACE, LIBRARY_NAME);
    persistenceService.findOrCreateLibrarySet(NAMESPACE, LIBRARY_NAME);

    verify(librarySetRepository, never()).save(any());
  }

  @Test
  void persistLibrariesLibrarySetIdAssignedBeforeSave() {
    ExternalLibrary candidate = buildCandidate(LIBRARY_NAME, VERSION);
    when(externalLibraryRepository.existsByPackageCanonicalAndLibraryNameAndVersion(
            NAMESPACE, LIBRARY_NAME, VERSION))
        .thenReturn(false);
    ExternalLibrary existingLibrary = buildCandidate(LIBRARY_NAME, "4.0.000");
    existingLibrary.setLibrarySetId("set-xyz");
    when(externalLibraryRepository.findByPackageCanonicalAndLibraryName(NAMESPACE, LIBRARY_NAME))
        .thenReturn(Optional.of(existingLibrary));
    when(externalLibraryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    persistenceService.persistLibraries(List.of(candidate));

    ArgumentCaptor<ExternalLibrary> captor = ArgumentCaptor.forClass(ExternalLibrary.class);
    verify(externalLibraryRepository).save(captor.capture());
    assertThat(captor.getValue().getLibrarySetId()).isEqualTo("set-xyz");
  }
}

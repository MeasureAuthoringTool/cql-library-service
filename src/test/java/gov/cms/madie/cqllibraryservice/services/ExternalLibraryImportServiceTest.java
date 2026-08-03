package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.models.ExternalLibrary;
import gov.cms.madie.cqllibraryservice.models.PackageStatus;
import gov.cms.madie.cqllibraryservice.models.PackageTrackingRecord;
import gov.cms.madie.cqllibraryservice.repositories.PackageTrackingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalLibraryImportServiceTest {

  @InjectMocks private ExternalLibraryImportService importService;
  @Mock private PackageCacheManagerAdapter packageCacheManagerAdapter;
  @Mock private ExternalLibraryDiscoveryService externalLibraryDiscoveryService;
  @Mock private ExternalLibraryPersistenceService externalLibraryPersistenceService;
  @Mock private PackageTrackingRepository packageTrackingRepository;

  private static final String PKG_ID = "hl7.fhir.us.qicore";
  private static final String PKG_VERSION = "6.0.0";
  private static final String USERNAME = "admin.user";

  private PackageTrackingRecord buildRecord(PackageStatus status) {
    return PackageTrackingRecord.builder()
        .id("rec-1")
        .packageId(PKG_ID)
        .version(PKG_VERSION)
        .status(status)
        .build();
  }

  private void stubTrackingLookup(PackageTrackingRecord rootRecord) {
    when(packageTrackingRepository.findByPackageIdAndVersion(anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              String lookupPackageId = invocation.getArgument(0);
              String lookupVersion = invocation.getArgument(1);
              if (PKG_ID.equals(lookupPackageId) && PKG_VERSION.equals(lookupVersion)) {
                return Optional.of(rootRecord);
              }
              return Optional.empty();
            });
  }

  // ---------------------------------------------------------------------------
  // markAsProcessing tests
  // ---------------------------------------------------------------------------

  @Test
  void markAsProcessingExistingRecordUpdatedToProcessing() {
    PackageTrackingRecord existing = buildRecord(PackageStatus.DOWNLOADED);
    when(packageTrackingRepository.findByPackageIdAndVersion(PKG_ID, PKG_VERSION))
        .thenReturn(Optional.of(existing));
    when(packageTrackingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    PackageTrackingRecord result = importService.markAsProcessing(PKG_ID, PKG_VERSION);

    assertThat(result.getStatus()).isEqualTo(PackageStatus.PROCESSING);
    assertThat(result.getImportStartedAt()).isNotNull();
    assertThat(result.getDiscoveredLibraryCount()).isEqualTo(0);
    assertThat(result.getPersistedLibraryCount()).isEqualTo(0);
  }

  @Test
  void markAsProcessingNoExistingRecordCreatesNew() {
    when(packageTrackingRepository.findByPackageIdAndVersion(PKG_ID, PKG_VERSION))
        .thenReturn(Optional.empty());
    when(packageTrackingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    PackageTrackingRecord result = importService.markAsProcessing(PKG_ID, PKG_VERSION);

    assertThat(result.getStatus()).isEqualTo(PackageStatus.PROCESSING);
  }

  // ---------------------------------------------------------------------------
  // importLibraries – status outcome tests
  // ---------------------------------------------------------------------------

  @Test
  void importLibrariesWithLibrariesFoundStatusBecomesInstalled() throws Exception {
    PackageTrackingRecord record = buildRecord(PackageStatus.DOWNLOADED);
    stubTrackingLookup(record);
    when(packageTrackingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // Simulate one resolved package path via the adapter callback.
    doAnswer(
            inv -> {
              PackageDownloadedCallback cb = inv.getArgument(2);
              cb.onDownloaded(PKG_ID, PKG_VERSION, "/cache/hl7.fhir.us.qicore#6.0.0");
              return List.of("/cache/hl7.fhir.us.qicore#6.0.0");
            })
        .when(packageCacheManagerAdapter)
        .loadPackageWithDependencies(eq(PKG_ID), eq(PKG_VERSION), any());

    ExternalLibrary lib =
        ExternalLibrary.builder().libraryName("FHIRHelpers").version("4.3.000").build();
    when(externalLibraryDiscoveryService.discoverLibrariesForPackage(
            anyString(), anyString(), anyString()))
        .thenReturn(List.of(lib));
    when(externalLibraryPersistenceService.persistLibraries(anyList())).thenReturn(1);

    importService.importLibraries(PKG_ID, PKG_VERSION);

    ArgumentCaptor<PackageTrackingRecord> captor =
        ArgumentCaptor.forClass(PackageTrackingRecord.class);
    verify(packageTrackingRepository, atLeastOnce()).save(captor.capture());

    // Last save should reflect INSTALLED status.
    PackageTrackingRecord lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
    assertThat(lastSaved.getStatus()).isEqualTo(PackageStatus.INSTALLED);
    assertThat(lastSaved.getPersistedLibraryCount()).isEqualTo(1);
    assertThat(lastSaved.getDiscoveredLibraryCount()).isEqualTo(1);
  }

  @Test
  void importLibrariesNoLibrariesFoundStatusBecomesProcessed() throws Exception {
    PackageTrackingRecord record = buildRecord(PackageStatus.DOWNLOADED);
    stubTrackingLookup(record);
    when(packageTrackingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    doAnswer(
            inv -> {
              PackageDownloadedCallback cb = inv.getArgument(2);
              cb.onDownloaded(PKG_ID, PKG_VERSION, "/cache/some-path");
              return List.of("/cache/some-path");
            })
        .when(packageCacheManagerAdapter)
        .loadPackageWithDependencies(eq(PKG_ID), eq(PKG_VERSION), any());

    when(externalLibraryDiscoveryService.discoverLibrariesForPackage(
            anyString(), anyString(), anyString()))
        .thenReturn(List.of());
    when(externalLibraryPersistenceService.persistLibraries(anyList())).thenReturn(0);

    importService.importLibraries(PKG_ID, PKG_VERSION);

    ArgumentCaptor<PackageTrackingRecord> captor =
        ArgumentCaptor.forClass(PackageTrackingRecord.class);
    verify(packageTrackingRepository, atLeastOnce()).save(captor.capture());

    PackageTrackingRecord lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
    assertThat(lastSaved.getStatus()).isEqualTo(PackageStatus.PROCESSED);
    assertThat(lastSaved.getPersistedLibraryCount()).isEqualTo(0);
  }

  @Test
  void importLibrariesExceptionDuringPackageResolutionDoesNotPersistPackageStatus()
      throws Exception {
    when(packageCacheManagerAdapter.loadPackageWithDependencies(eq(PKG_ID), eq(PKG_VERSION), any()))
        .thenThrow(new RuntimeException("Cache unavailable"));

    importService.importLibraries(PKG_ID, PKG_VERSION);

    ArgumentCaptor<PackageTrackingRecord> captor =
        ArgumentCaptor.forClass(PackageTrackingRecord.class);
    verify(packageTrackingRepository, never()).save(captor.capture());
    verify(externalLibraryDiscoveryService, never())
        .discoverLibrariesForPackage(anyString(), anyString(), anyString());
    verify(externalLibraryPersistenceService, never()).persistLibraries(anyList());
  }

  @Test
  void importLibrariesPackageFailureStoresErrorMessageAndErrorStatus() throws Exception {
    PackageTrackingRecord record = buildRecord(PackageStatus.DOWNLOADED);
    stubTrackingLookup(record);
    when(packageTrackingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    String expectedError = "Unexpected failure during import";
    doAnswer(
            inv -> {
              PackageDownloadedCallback cb = inv.getArgument(2);
              cb.onDownloaded(PKG_ID, PKG_VERSION, "/cache/some-path");
              return List.of("/cache/some-path");
            })
        .when(packageCacheManagerAdapter)
        .loadPackageWithDependencies(eq(PKG_ID), eq(PKG_VERSION), any());
    when(externalLibraryDiscoveryService.discoverLibrariesForPackage(
            anyString(), anyString(), anyString()))
        .thenThrow(new RuntimeException(expectedError));

    importService.importLibraries(PKG_ID, PKG_VERSION);

    ArgumentCaptor<PackageTrackingRecord> captor =
        ArgumentCaptor.forClass(PackageTrackingRecord.class);
    verify(packageTrackingRepository, atLeastOnce()).save(captor.capture());
    PackageTrackingRecord lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
    assertThat(lastSaved.getErrorMessage()).isEqualTo(expectedError);
    assertThat(lastSaved.getStatus()).isEqualTo(PackageStatus.ERROR);
    assertThat(lastSaved.getImportCompletedAt()).isNotNull();
  }

  @Test
  void importLibrariesMultipleDependenciesAllScanned() throws Exception {
    PackageTrackingRecord record = buildRecord(PackageStatus.DOWNLOADED);
    stubTrackingLookup(record);
    List<PackageTrackingRecord> savedSnapshots = new ArrayList<>();
    when(packageTrackingRepository.save(any()))
        .thenAnswer(
            inv -> {
              PackageTrackingRecord saved = inv.getArgument(0);
              savedSnapshots.add(
                  PackageTrackingRecord.builder()
                      .packageId(saved.getPackageId())
                      .version(saved.getVersion())
                      .status(saved.getStatus())
                      .discoveredLibraryCount(saved.getDiscoveredLibraryCount())
                      .persistedLibraryCount(saved.getPersistedLibraryCount())
                      .importStartedAt(saved.getImportStartedAt())
                      .importCompletedAt(saved.getImportCompletedAt())
                      .build());
              return saved;
            });

    // Two packages (root + one dependency).
    doAnswer(
            inv -> {
              PackageDownloadedCallback cb = inv.getArgument(2);
              cb.onDownloaded(PKG_ID, PKG_VERSION, "/cache/root");
              cb.onDownloaded("hl7.fhir.r4.core", "4.0.1", "/cache/dep");
              return List.of("/cache/root", "/cache/dep");
            })
        .when(packageCacheManagerAdapter)
        .loadPackageWithDependencies(eq(PKG_ID), eq(PKG_VERSION), any());

    when(externalLibraryDiscoveryService.discoverLibrariesForPackage(
            anyString(), anyString(), anyString()))
        .thenReturn(List.of());
    when(externalLibraryPersistenceService.persistLibraries(anyList())).thenReturn(0);

    importService.importLibraries(PKG_ID, PKG_VERSION);

    // discoverLibraries called once per resolved package
    verify(externalLibraryDiscoveryService, times(2))
        .discoverLibrariesForPackage(anyString(), anyString(), anyString());

    assertThat(savedSnapshots)
        .anyMatch(
            saved ->
                "hl7.fhir.r4.core".equals(saved.getPackageId())
                    && "4.0.1".equals(saved.getVersion())
                    && saved.getStatus() == PackageStatus.PROCESSING)
        .anyMatch(
            saved ->
                "hl7.fhir.r4.core".equals(saved.getPackageId())
                    && "4.0.1".equals(saved.getVersion())
                    && saved.getStatus() == PackageStatus.PROCESSED);
  }
}

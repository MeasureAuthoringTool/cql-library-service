package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.DownloadedPackageResult;
import gov.cms.madie.cqllibraryservice.models.PackageDownloadStatus;
import gov.cms.madie.cqllibraryservice.models.PackageTrackingRecord;
import gov.cms.madie.cqllibraryservice.repositories.PackageTrackingRepository;
import gov.cms.madie.cqllibraryservice.services.PackageCacheManagerAdapter.PackageDownloadedCallback;
import gov.cms.madie.models.scanner.VirusScanResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FhirPackageDownloadServiceImplTest {

  @InjectMocks private FhirPackageDownloadServiceImpl downloadService;
  @Mock private PackageCacheManagerAdapter packageCacheManagerAdapter;
  @Mock private PackageTrackingRepository packageTrackingRepository;
  @Mock private VirusScanClient virusScanClient;
  @Captor private ArgumentCaptor<PackageTrackingRecord> trackingRecordCaptor;

  private static final String PACKAGE_ID = "hl7.fhir.us.qicore";
  private static final String VERSION = "7.0.2";
  private static final String USERNAME = "admin.user";
  private static final String ROOT_PATH = "/cache/hl7.fhir.us.qicore#7.0.2";
  private static final String DEP_1 = "hl7.fhir.us.core";
  private static final String DEP_2 = "hl7.fhir.uv.extensions";

  private void stubAdapterWithPaths(List<String> paths) throws Exception {
    when(packageCacheManagerAdapter.loadPackageWithDependencies(
            eq(PACKAGE_ID), eq(VERSION), any(PackageDownloadedCallback.class)))
        .thenAnswer(
            invocation -> {
              PackageDownloadedCallback cb = invocation.getArgument(2);
              List<String> keptPaths = new java.util.ArrayList<>();
              for (String path : paths) {
                if (cb.onDownloaded(PACKAGE_ID, VERSION, path)) {
                  keptPaths.add(path);
                }
              }
              return keptPaths;
            });
  }

  @Test
  void testDownloadPackageSuccess() throws Exception {
    when(packageTrackingRepository.findByPackageIdAndVersion(PACKAGE_ID, VERSION))
        .thenReturn(Optional.empty());
    when(packageTrackingRepository.save(any(PackageTrackingRecord.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    stubAdapterWithPaths(List.of(ROOT_PATH));
    when(virusScanClient.scanFile(any(FileSystemResource.class)))
        .thenReturn(VirusScanResponseDto.builder().filesScanned(1).cleanFileCount(1).build());

    DownloadedPackageResult result = downloadService.downloadPackage(PACKAGE_ID, VERSION, USERNAME);

    assertTrue(result.isSuccess());
    assertEquals(PACKAGE_ID, result.getPackageId());
    assertEquals(VERSION, result.getVersion());
    assertEquals(ROOT_PATH, result.getPackageLocation());
    assertNull(result.getErrorMessage());

    verify(packageTrackingRepository, atLeast(2)).save(trackingRecordCaptor.capture());
    PackageTrackingRecord lastSaved =
        trackingRecordCaptor.getAllValues().get(trackingRecordCaptor.getAllValues().size() - 1);
    assertEquals(PackageDownloadStatus.DOWNLOADED, lastSaved.getStatus());
    assertNotNull(lastSaved.getDownloadedAt());
  }

  @Test
  void testDownloadPackageCached() throws Exception {
    when(packageTrackingRepository.findByPackageIdAndVersion(PACKAGE_ID, VERSION))
        .thenReturn(Optional.empty());
    when(packageTrackingRepository.save(any(PackageTrackingRecord.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    stubAdapterWithPaths(List.of(ROOT_PATH));
    when(virusScanClient.scanFile(any(FileSystemResource.class)))
        .thenReturn(VirusScanResponseDto.builder().filesScanned(1).cleanFileCount(1).build());

    DownloadedPackageResult result = downloadService.downloadPackage(PACKAGE_ID, VERSION, USERNAME);

    assertTrue(result.isSuccess());
    assertEquals(PACKAGE_ID, result.getPackageId());
    assertEquals(VERSION, result.getVersion());
    assertNull(result.getErrorMessage());
  }

  @Test
  void testDownloadPackageScansEachPackageImmediately() throws Exception {
    String depPath1 = "/cache/hl7.fhir.us.core#6.1.0";
    String depPath2 = "/cache/hl7.fhir.uv.extensions#1.0.0";
    when(packageTrackingRepository.findByPackageIdAndVersion(PACKAGE_ID, VERSION))
        .thenReturn(Optional.empty());
    when(packageTrackingRepository.save(any(PackageTrackingRecord.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(packageCacheManagerAdapter.loadPackageWithDependencies(
            eq(PACKAGE_ID), eq(VERSION), any(PackageDownloadedCallback.class)))
        .thenAnswer(
            invocation -> {
              PackageDownloadedCallback cb = invocation.getArgument(2);
              List<String> keptPaths = new java.util.ArrayList<>();
              if (cb.onDownloaded(PACKAGE_ID, VERSION, ROOT_PATH)) {
                keptPaths.add(ROOT_PATH);
              }
              if (cb.onDownloaded(DEP_1, "6.1.0", depPath1)) {
                keptPaths.add(depPath1);
              }
              if (cb.onDownloaded(DEP_2, "1.0.0", depPath2)) {
                keptPaths.add(depPath2);
              }
              return keptPaths;
            });
    when(virusScanClient.scanFile(any(FileSystemResource.class)))
        .thenReturn(VirusScanResponseDto.builder().filesScanned(1).cleanFileCount(1).build());

    DownloadedPackageResult result = downloadService.downloadPackage(PACKAGE_ID, VERSION, USERNAME);

    assertTrue(result.isSuccess());
    assertEquals(ROOT_PATH, result.getPackageLocation());
    verify(virusScanClient, times(3)).scanFile(any(FileSystemResource.class));

    verify(packageTrackingRepository, atLeast(4)).save(trackingRecordCaptor.capture());
    PackageTrackingRecord lastSaved =
        trackingRecordCaptor.getAllValues().get(trackingRecordCaptor.getAllValues().size() - 1);
    assertEquals(PackageDownloadStatus.DOWNLOADED, lastSaved.getStatus());
    assertEquals(
        List.of("hl7.fhir.us.core#6.1.0", "hl7.fhir.uv.extensions#1.0.0"), lastSaved.getChildIgs());
  }

  @Test
  void testDownloadPackageVirusScanDetectsInfectionAndDeletesFile() throws Exception {
    Path infectedDir = Files.createTempDirectory("infected-pkg");
    when(packageTrackingRepository.findByPackageIdAndVersion(PACKAGE_ID, VERSION))
        .thenReturn(Optional.empty());
    when(packageTrackingRepository.save(any(PackageTrackingRecord.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(packageCacheManagerAdapter.loadPackageWithDependencies(
            eq(PACKAGE_ID), eq(VERSION), any(PackageDownloadedCallback.class)))
        .thenAnswer(
            invocation -> {
              PackageDownloadedCallback cb = invocation.getArgument(2);
              cb.onDownloaded(PACKAGE_ID, VERSION, infectedDir.toString());
              return List.of();
            });
    when(virusScanClient.scanFile(any(FileSystemResource.class)))
        .thenReturn(VirusScanResponseDto.builder().filesScanned(1).cleanFileCount(0).build());

    DownloadedPackageResult result = downloadService.downloadPackage(PACKAGE_ID, VERSION, USERNAME);

    assertFalse(result.isSuccess());
    assertNotNull(result.getErrorMessage());
    assertTrue(result.getErrorMessage().contains("infected"));
    assertFalse(Files.exists(infectedDir), "Infected package directory should have been deleted");

    verify(packageTrackingRepository, atLeast(2)).save(trackingRecordCaptor.capture());
    PackageTrackingRecord lastSaved =
        trackingRecordCaptor.getAllValues().get(trackingRecordCaptor.getAllValues().size() - 1);
    assertEquals(PackageDownloadStatus.ERROR_INFECTED_SO_REVIEW, lastSaved.getStatus());
  }

  @Test
  void testDownloadPackageContinuesAfterInfectedDependency() throws Exception {
    String cleanPath = "/cache/hl7.fhir.us.core#6.1.0";
    String infectedPath = "/cache/evil.pkg#1.0.0";
    String thirdPath = "/cache/hl7.fhir.uv.extensions#1.0.0";
    when(packageTrackingRepository.findByPackageIdAndVersion(PACKAGE_ID, VERSION))
        .thenReturn(Optional.empty());
    when(packageTrackingRepository.save(any(PackageTrackingRecord.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(packageCacheManagerAdapter.loadPackageWithDependencies(
            eq(PACKAGE_ID), eq(VERSION), any(PackageDownloadedCallback.class)))
        .thenAnswer(
            invocation -> {
              PackageDownloadedCallback cb = invocation.getArgument(2);
              List<String> keptPaths = new java.util.ArrayList<>();
              if (cb.onDownloaded(PACKAGE_ID, VERSION, ROOT_PATH)) {
                keptPaths.add(ROOT_PATH);
              }
              if (cb.onDownloaded(DEP_1, "6.1.0", cleanPath)) {
                keptPaths.add(cleanPath);
              }
              if (cb.onDownloaded("evil.pkg", "1.0.0", infectedPath)) {
                keptPaths.add(infectedPath);
              }
              if (cb.onDownloaded(DEP_2, "1.0.0", thirdPath)) {
                keptPaths.add(thirdPath);
              }
              return keptPaths;
            });
    when(virusScanClient.scanFile(any(FileSystemResource.class)))
        .thenReturn(VirusScanResponseDto.builder().filesScanned(1).cleanFileCount(1).build())
        .thenReturn(VirusScanResponseDto.builder().filesScanned(1).cleanFileCount(1).build())
        .thenReturn(VirusScanResponseDto.builder().filesScanned(1).cleanFileCount(0).build())
        .thenReturn(VirusScanResponseDto.builder().filesScanned(1).cleanFileCount(1).build());

    DownloadedPackageResult result = downloadService.downloadPackage(PACKAGE_ID, VERSION, USERNAME);

    assertTrue(result.isSuccess());
    assertEquals(ROOT_PATH, result.getPackageLocation());
    verify(virusScanClient, times(4)).scanFile(any(FileSystemResource.class));

    verify(packageTrackingRepository, atLeast(2)).save(trackingRecordCaptor.capture());
    assertTrue(
        trackingRecordCaptor.getAllValues().stream()
            .anyMatch(r -> PackageDownloadStatus.ERROR_INFECTED_SO_REVIEW.equals(r.getStatus())));
  }

  @Test
  void testDownloadPackageInvalidPackageId() throws Exception {
    String invalidPackageId = "some.invalid.package";
    when(packageTrackingRepository.findByPackageIdAndVersion(invalidPackageId, VERSION))
        .thenReturn(Optional.empty());
    when(packageTrackingRepository.save(any(PackageTrackingRecord.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(packageCacheManagerAdapter.loadPackageWithDependencies(
            eq(invalidPackageId), eq(VERSION), any(PackageDownloadedCallback.class)))
        .thenThrow(new IOException("Unable to find package some.invalid.package#7.0.2"));

    DownloadedPackageResult result =
        downloadService.downloadPackage(invalidPackageId, VERSION, USERNAME);

    assertFalse(result.isSuccess());
    assertEquals(invalidPackageId, result.getPackageId());
    assertTrue(result.getErrorMessage().contains("Unable to find package"));

    verify(packageTrackingRepository, atLeast(2)).save(trackingRecordCaptor.capture());
    PackageTrackingRecord lastSaved =
        trackingRecordCaptor.getAllValues().get(trackingRecordCaptor.getAllValues().size() - 1);
    assertEquals(PackageDownloadStatus.DOWNLOAD_FAILED, lastSaved.getStatus());
  }

  @Test
  void testDownloadPackageInvalidVersion() throws Exception {
    String invalidVersion = "999.999.999";
    when(packageTrackingRepository.findByPackageIdAndVersion(PACKAGE_ID, invalidVersion))
        .thenReturn(Optional.empty());
    when(packageTrackingRepository.save(any(PackageTrackingRecord.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(packageCacheManagerAdapter.loadPackageWithDependencies(
            eq(PACKAGE_ID), eq(invalidVersion), any(PackageDownloadedCallback.class)))
        .thenThrow(new IOException("Package version not found: hl7.fhir.us.qicore#999.999.999"));

    DownloadedPackageResult result =
        downloadService.downloadPackage(PACKAGE_ID, invalidVersion, USERNAME);

    assertFalse(result.isSuccess());
    assertNotNull(result.getErrorMessage());

    verify(packageTrackingRepository, atLeast(2)).save(trackingRecordCaptor.capture());
    PackageTrackingRecord lastSaved =
        trackingRecordCaptor.getAllValues().get(trackingRecordCaptor.getAllValues().size() - 1);
    assertEquals(PackageDownloadStatus.DOWNLOAD_FAILED, lastSaved.getStatus());
  }

  @Test
  void testDownloadPackageRegistryFailure() throws Exception {
    when(packageTrackingRepository.findByPackageIdAndVersion(PACKAGE_ID, VERSION))
        .thenReturn(Optional.empty());
    when(packageTrackingRepository.save(any(PackageTrackingRecord.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(packageCacheManagerAdapter.loadPackageWithDependencies(
            eq(PACKAGE_ID), eq(VERSION), any(PackageDownloadedCallback.class)))
        .thenThrow(new IOException("Connection timed out"));

    DownloadedPackageResult result = downloadService.downloadPackage(PACKAGE_ID, VERSION, USERNAME);

    assertFalse(result.isSuccess());
    assertEquals("Connection timed out", result.getErrorMessage());

    verify(packageTrackingRepository, atLeast(2)).save(trackingRecordCaptor.capture());
    PackageTrackingRecord lastSaved =
        trackingRecordCaptor.getAllValues().get(trackingRecordCaptor.getAllValues().size() - 1);
    assertEquals(PackageDownloadStatus.DOWNLOAD_FAILED, lastSaved.getStatus());
  }

  @Test
  void testDownloadPackageUnexpectedRuntimeException() throws Exception {
    when(packageTrackingRepository.findByPackageIdAndVersion(PACKAGE_ID, VERSION))
        .thenReturn(Optional.empty());
    when(packageTrackingRepository.save(any(PackageTrackingRecord.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(packageCacheManagerAdapter.loadPackageWithDependencies(
            eq(PACKAGE_ID), eq(VERSION), any(PackageDownloadedCallback.class)))
        .thenThrow(new RuntimeException("Unexpected error"));

    DownloadedPackageResult result = downloadService.downloadPackage(PACKAGE_ID, VERSION, USERNAME);

    assertFalse(result.isSuccess());
    assertNull(result.getPackageLocation());
    assertEquals("Unexpected error", result.getErrorMessage());
  }

  @Test
  void testDownloadPackageUpdatesExistingTrackingRecord() throws Exception {
    PackageTrackingRecord existingRecord =
        PackageTrackingRecord.builder()
            .id("existing-id")
            .packageId(PACKAGE_ID)
            .version(VERSION)
            .status(PackageDownloadStatus.DOWNLOAD_FAILED)
            .errorMessage("Previous failure")
            .build();
    when(packageTrackingRepository.findByPackageIdAndVersion(PACKAGE_ID, VERSION))
        .thenReturn(Optional.of(existingRecord));
    when(packageTrackingRepository.save(any(PackageTrackingRecord.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    stubAdapterWithPaths(List.of(ROOT_PATH));
    when(virusScanClient.scanFile(any(FileSystemResource.class)))
        .thenReturn(VirusScanResponseDto.builder().filesScanned(1).cleanFileCount(1).build());

    DownloadedPackageResult result = downloadService.downloadPackage(PACKAGE_ID, VERSION, USERNAME);

    assertTrue(result.isSuccess());
    verify(packageTrackingRepository, atLeast(2)).save(trackingRecordCaptor.capture());
    PackageTrackingRecord lastSaved =
        trackingRecordCaptor.getAllValues().get(trackingRecordCaptor.getAllValues().size() - 1);
    assertEquals(PackageDownloadStatus.DOWNLOADED, lastSaved.getStatus());
    assertNull(lastSaved.getErrorMessage());
  }

  @Test
  void testDownloadPackageVirusScanReturnsNull() throws Exception {
    when(packageTrackingRepository.findByPackageIdAndVersion(PACKAGE_ID, VERSION))
        .thenReturn(Optional.empty());
    when(packageTrackingRepository.save(any(PackageTrackingRecord.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    stubAdapterWithPaths(List.of(ROOT_PATH));
    when(virusScanClient.scanFile(any(FileSystemResource.class))).thenReturn(null);

    DownloadedPackageResult result = downloadService.downloadPackage(PACKAGE_ID, VERSION, USERNAME);

    assertFalse(result.isSuccess());
    assertNotNull(result.getErrorMessage());
    assertTrue(result.getErrorMessage().contains("no result"));

    verify(packageTrackingRepository, atLeast(2)).save(trackingRecordCaptor.capture());
    PackageTrackingRecord lastSaved =
        trackingRecordCaptor.getAllValues().get(trackingRecordCaptor.getAllValues().size() - 1);
    assertEquals(PackageDownloadStatus.ERROR_INFECTED_SO_REVIEW, lastSaved.getStatus());
  }
}

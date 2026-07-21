package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.DownloadedPackageResult;
import gov.cms.madie.cqllibraryservice.models.PackageDownloadStatus;
import gov.cms.madie.cqllibraryservice.models.PackageTrackingRecord;
import gov.cms.madie.cqllibraryservice.repositories.PackageTrackingRepository;
import gov.cms.madie.models.scanner.VirusScanResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

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

  @TempDir Path tempDir;

  private static final String PACKAGE_ID = "hl7.fhir.us.qicore";
  private static final String VERSION = "7.0.2";
  private static final String USERNAME = "admin.user";
  private static final String DEP_1 = "hl7.fhir.us.core";
  private static final String DEP_2 = "hl7.fhir.uv.extensions";

  private String rootPath;

  @BeforeEach
  void setUp() throws IOException {
    rootPath = Files.createDirectory(tempDir.resolve("hl7.fhir.us.qicore#7.0.2")).toString();
  }

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
    stubAdapterWithPaths(List.of(rootPath));
    when(virusScanClient.scanFiles(any()))
        .thenReturn(VirusScanResponseDto.builder().filesScanned(0).cleanFileCount(0).build());

    DownloadedPackageResult result = downloadService.downloadPackage(PACKAGE_ID, VERSION, USERNAME);

    assertTrue(result.isSuccess());
    assertEquals(PACKAGE_ID, result.getPackageId());
    assertEquals(VERSION, result.getVersion());
    assertEquals(rootPath, result.getPackageLocation());
    assertNull(result.getErrorMessage());

    verify(packageTrackingRepository, atLeast(2)).save(trackingRecordCaptor.capture());
    PackageTrackingRecord lastSaved =
        trackingRecordCaptor.getAllValues().get(trackingRecordCaptor.getAllValues().size() - 1);
    assertEquals(PackageDownloadStatus.DOWNLOADED, lastSaved.getStatus());
    assertNotNull(lastSaved.getDownloadedAt());
  }

  @Test
  void testDownloadPackageScansEachPackageImmediately() throws Exception {
    Path depDir1 = Files.createDirectory(tempDir.resolve("hl7.fhir.us.core#6.1.0"));
    Path depDir2 = Files.createDirectory(tempDir.resolve("hl7.fhir.uv.extensions#1.0.0"));
    String depPath1 = depDir1.toString();
    String depPath2 = depDir2.toString();
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
              if (cb.onDownloaded(PACKAGE_ID, VERSION, rootPath)) {
                keptPaths.add(rootPath);
              }
              if (cb.onDownloaded(DEP_1, "6.1.0", depPath1)) {
                keptPaths.add(depPath1);
              }
              if (cb.onDownloaded(DEP_2, "1.0.0", depPath2)) {
                keptPaths.add(depPath2);
              }
              return keptPaths;
            });
    when(virusScanClient.scanFiles(any()))
        .thenReturn(VirusScanResponseDto.builder().filesScanned(0).cleanFileCount(0).build());

    DownloadedPackageResult result = downloadService.downloadPackage(PACKAGE_ID, VERSION, USERNAME);

    assertTrue(result.isSuccess());
    assertEquals(rootPath, result.getPackageLocation());
    verify(virusScanClient, times(3)).scanFiles(any());

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
    when(virusScanClient.scanFiles(any()))
        .thenReturn(VirusScanResponseDto.builder().filesScanned(0).infectedFileCount(1).build());

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
    Path depDir1 = Files.createDirectory(tempDir.resolve("hl7.fhir.us.core#6.1.0"));
    Path infectedDep = Files.createDirectory(tempDir.resolve("evil.pkg#1.0.0"));
    Path depDir2 = Files.createDirectory(tempDir.resolve("hl7.fhir.uv.extensions#1.0.0"));
    String cleanPath = depDir1.toString();
    String infectedPath = infectedDep.toString();
    String thirdPath = depDir2.toString();
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
              if (cb.onDownloaded(PACKAGE_ID, VERSION, rootPath)) {
                keptPaths.add(rootPath);
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
    when(virusScanClient.scanFiles(any()))
        .thenReturn(VirusScanResponseDto.builder().filesScanned(0).cleanFileCount(0).build())
        .thenReturn(VirusScanResponseDto.builder().filesScanned(0).cleanFileCount(0).build())
        .thenReturn(
            VirusScanResponseDto.builder()
                .filesScanned(0)
                .cleanFileCount(0)
                .infectedFileCount(1)
                .build())
        .thenReturn(VirusScanResponseDto.builder().filesScanned(0).cleanFileCount(0).build());

    DownloadedPackageResult result = downloadService.downloadPackage(PACKAGE_ID, VERSION, USERNAME);

    assertTrue(result.isSuccess());
    assertEquals(rootPath, result.getPackageLocation());
    verify(virusScanClient, times(4)).scanFiles(any());

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
    stubAdapterWithPaths(List.of(rootPath));
    when(virusScanClient.scanFiles(any()))
        .thenReturn(VirusScanResponseDto.builder().filesScanned(0).cleanFileCount(0).build());

    DownloadedPackageResult result = downloadService.downloadPackage(PACKAGE_ID, VERSION, USERNAME);

    assertTrue(result.isSuccess());
    verify(packageTrackingRepository, atLeast(2)).save(trackingRecordCaptor.capture());
    PackageTrackingRecord lastSaved =
        trackingRecordCaptor.getAllValues().get(trackingRecordCaptor.getAllValues().size() - 1);
    assertEquals(PackageDownloadStatus.DOWNLOADED, lastSaved.getStatus());
    assertNull(lastSaved.getErrorMessage());
  }

  @Test
  void testDownloadPackageVirusScanServiceDownBlocksRootPackageDownloadAndDeletesResidual()
      throws Exception {
    Path pkgDir = Files.createDirectory(tempDir.resolve("root-pkg-down"));
    String pkgPath = pkgDir.toString();
    when(packageTrackingRepository.findByPackageIdAndVersion(PACKAGE_ID, VERSION))
        .thenReturn(Optional.empty());
    when(packageTrackingRepository.save(any(PackageTrackingRecord.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(packageCacheManagerAdapter.loadPackageWithDependencies(
            eq(PACKAGE_ID), eq(VERSION), any(PackageDownloadedCallback.class)))
        .thenAnswer(
            invocation -> {
              PackageDownloadedCallback cb = invocation.getArgument(2);
              cb.onDownloaded(PACKAGE_ID, VERSION, pkgPath);
              return List.of();
            });
    when(virusScanClient.scanFiles(any()))
        .thenThrow(new ResourceAccessException("Connection refused: virus scan service is down"));

    DownloadedPackageResult result = downloadService.downloadPackage(PACKAGE_ID, VERSION, USERNAME);

    assertFalse(result.isSuccess());
    assertNull(result.getPackageLocation());
    assertNotNull(result.getErrorMessage());
    assertTrue(result.getErrorMessage().contains("Virus scan service error"));
    assertFalse(
        Files.exists(pkgDir),
        "Residual package directory should have been deleted on scan failure");

    verify(packageTrackingRepository, atLeast(2)).save(trackingRecordCaptor.capture());
    PackageTrackingRecord lastSaved =
        trackingRecordCaptor.getAllValues().get(trackingRecordCaptor.getAllValues().size() - 1);
    assertEquals(PackageDownloadStatus.DOWNLOAD_FAILED, lastSaved.getStatus());
  }

  @Test
  void testDownloadPackageVirusScanServiceDownBlocksDownloadWhenDependencyFailsAndDeletesResidual()
      throws Exception {
    Path depDir = Files.createDirectory(tempDir.resolve("hl7.fhir.us.core#6.1.0"));
    String depPath = depDir.toString();
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
              // Root package scans cleanly
              if (cb.onDownloaded(PACKAGE_ID, VERSION, rootPath)) {
                keptPaths.add(rootPath);
              }
              // Dependency scan throws — VirusScanServiceException propagates out
              cb.onDownloaded(DEP_1, "6.1.0", depPath);
              return keptPaths;
            });
    when(virusScanClient.scanFiles(any()))
        .thenReturn(VirusScanResponseDto.builder().filesScanned(0).cleanFileCount(0).build())
        .thenThrow(new ResourceAccessException("Connection refused: virus scan service is down"));

    DownloadedPackageResult result = downloadService.downloadPackage(PACKAGE_ID, VERSION, USERNAME);

    assertFalse(result.isSuccess());
    assertNull(result.getPackageLocation());
    assertNotNull(result.getErrorMessage());
    assertTrue(result.getErrorMessage().contains("Virus scan service error"));
    assertFalse(
        Files.exists(depDir),
        "Residual dependency directory should have been deleted on scan failure");

    verify(packageTrackingRepository, atLeast(2)).save(trackingRecordCaptor.capture());
    // Both the dependency and the root package should be marked DOWNLOAD_FAILED
    assertTrue(
        trackingRecordCaptor.getAllValues().stream()
                .filter(r -> PackageDownloadStatus.DOWNLOAD_FAILED.equals(r.getStatus()))
                .count()
            >= 2);
  }

  @Test
  void testDownloadPackageCollectFilesFailureDeletesResidualDownload() throws Exception {
    // Simulate a packagePath that exists as a file (not a dir) to force collectFiles failure
    // by passing a directory path that suddenly disappears mid-scan
    Path pkgDir = Files.createDirectory(tempDir.resolve("vanishing-pkg"));
    String pkgPath = pkgDir.toString();
    // Delete the directory after creating it so collectFiles will fail with NoSuchFileException
    Files.delete(pkgDir);
    // Re-create it so deletePackage can try to delete it (simulates partial download)
    Files.createDirectory(pkgDir);

    when(packageTrackingRepository.findByPackageIdAndVersion(PACKAGE_ID, VERSION))
        .thenReturn(Optional.empty());
    when(packageTrackingRepository.save(any(PackageTrackingRecord.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(packageCacheManagerAdapter.loadPackageWithDependencies(
            eq(PACKAGE_ID), eq(VERSION), any(PackageDownloadedCallback.class)))
        .thenAnswer(
            invocation -> {
              PackageDownloadedCallback cb = invocation.getArgument(2);
              // Delete dir just before callback so collectFiles fails
              Files.delete(pkgDir);
              cb.onDownloaded(PACKAGE_ID, VERSION, pkgPath);
              return List.of();
            });

    DownloadedPackageResult result = downloadService.downloadPackage(PACKAGE_ID, VERSION, USERNAME);

    assertFalse(result.isSuccess());
    assertNotNull(result.getErrorMessage());

    verify(packageTrackingRepository, atLeast(2)).save(trackingRecordCaptor.capture());
    PackageTrackingRecord lastSaved =
        trackingRecordCaptor.getAllValues().get(trackingRecordCaptor.getAllValues().size() - 1);
    assertEquals(PackageDownloadStatus.DOWNLOAD_FAILED, lastSaved.getStatus());
  }
}

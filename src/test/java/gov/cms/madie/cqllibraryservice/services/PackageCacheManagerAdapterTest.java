package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.services.PackageCacheManagerAdapter.PackageDownloadedCallback;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PackageCacheManagerAdapterTest {

  @Mock private FilesystemPackageCacheManager cacheManager;

  private PackageCacheManagerAdapter adapter;

  private static final String PACKAGE_ID = "hl7.fhir.us.qicore";
  private static final String VERSION = "7.0.2";
  private static final String ROOT_PATH = "/cache/hl7.fhir.us.qicore#7.0.2";
  private static final String DEP_1_ID = "hl7.fhir.us.core";
  private static final String DEP_1_VERSION = "6.1.0";
  private static final String DEP_1_PATH = "/cache/hl7.fhir.us.core#6.1.0";
  private static final String DEP_2_ID = "hl7.fhir.uv.extensions";
  private static final String DEP_2_VERSION = "1.0.0";
  private static final String DEP_2_PATH = "/cache/hl7.fhir.uv.extensions#1.0.0";

  @BeforeEach
  void setUp() throws Exception {
    adapter = new PackageCacheManagerAdapter("") {};
    var field = PackageCacheManagerAdapter.class.getDeclaredField("cacheManager");
    field.setAccessible(true);
    field.set(adapter, cacheManager);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private void stubPackage(String packageId, String version, String path) throws Exception {
    stubPackage(packageId, version, path, Map.of());
  }

  private void stubPackage(String packageId, String version, String path, Map<String, String> deps)
      throws Exception {
    NpmPackage pkg = mock(NpmPackage.class);
    when(pkg.getPath()).thenReturn(path);

    if (!deps.isEmpty()) {
      JsonObject npm = mock(JsonObject.class);
      JsonObject dependencyObj = mock(JsonObject.class);
      when(pkg.getNpm()).thenReturn(npm);
      when(npm.getJsonObject("dependencies")).thenReturn(dependencyObj);
      when(dependencyObj.getNames()).thenReturn(new ArrayList<>(deps.keySet()));
      deps.forEach((id, ver) -> when(dependencyObj.asString(id)).thenReturn(ver));
    }

    when(cacheManager.loadPackage(packageId, version)).thenReturn(pkg);
  }

  // ---------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------

  @Test
  void loadPackageWithDependenciesRootOnlyCallsCallbackAndReturnsPath() throws Exception {
    stubPackage(PACKAGE_ID, VERSION, ROOT_PATH);
    PackageDownloadedCallback cb = mock(PackageDownloadedCallback.class);
    when(cb.onDownloaded(PACKAGE_ID, VERSION, ROOT_PATH)).thenReturn(true);

    List<String> result = adapter.loadPackageWithDependencies(PACKAGE_ID, VERSION, cb);

    assertEquals(List.of(ROOT_PATH), result);
    verify(cb).onDownloaded(PACKAGE_ID, VERSION, ROOT_PATH);
  }

  @Test
  void loadPackageWithDependenciesLoadsAllDepsAndCallsCallbackForEach() throws Exception {
    Map<String, String> deps = new LinkedHashMap<>();
    deps.put(DEP_1_ID, DEP_1_VERSION);
    deps.put(DEP_2_ID, DEP_2_VERSION);
    stubPackage(PACKAGE_ID, VERSION, ROOT_PATH, deps);
    stubPackage(DEP_1_ID, DEP_1_VERSION, DEP_1_PATH);
    stubPackage(DEP_2_ID, DEP_2_VERSION, DEP_2_PATH);

    PackageDownloadedCallback cb = mock(PackageDownloadedCallback.class);
    when(cb.onDownloaded(any(), any(), any())).thenReturn(true);

    List<String> result = adapter.loadPackageWithDependencies(PACKAGE_ID, VERSION, cb);

    assertEquals(List.of(ROOT_PATH, DEP_1_PATH, DEP_2_PATH), result);
    verify(cb).onDownloaded(PACKAGE_ID, VERSION, ROOT_PATH);
    verify(cb).onDownloaded(DEP_1_ID, DEP_1_VERSION, DEP_1_PATH);
    verify(cb).onDownloaded(DEP_2_ID, DEP_2_VERSION, DEP_2_PATH);
  }

  @Test
  void loadPackageWithDependenciesCallbackReturnsFalseStopsProcessingThatPackage()
      throws Exception {
    stubPackage(PACKAGE_ID, VERSION, ROOT_PATH);
    PackageDownloadedCallback cb = mock(PackageDownloadedCallback.class);
    when(cb.onDownloaded(PACKAGE_ID, VERSION, ROOT_PATH)).thenReturn(false);

    List<String> result = adapter.loadPackageWithDependencies(PACKAGE_ID, VERSION, cb);

    assertTrue(result.isEmpty());
    verify(cb).onDownloaded(PACKAGE_ID, VERSION, ROOT_PATH);
  }

  @Test
  void loadPackageWithDependenciesPackageNotFoundReturnsEmptyList() throws Exception {
    when(cacheManager.loadPackage(PACKAGE_ID, VERSION)).thenReturn(null);
    PackageDownloadedCallback cb = mock(PackageDownloadedCallback.class);

    List<String> result = adapter.loadPackageWithDependencies(PACKAGE_ID, VERSION, cb);

    assertTrue(result.isEmpty());
    verifyNoInteractions(cb);
  }

  @Test
  void loadPackageWithDependenciesCacheManagerThrowsPropagatesException() throws Exception {
    when(cacheManager.loadPackage(PACKAGE_ID, VERSION))
        .thenThrow(new IOException("Registry unavailable"));
    PackageDownloadedCallback cb = mock(PackageDownloadedCallback.class);

    assertThrows(
        IOException.class, () -> adapter.loadPackageWithDependencies(PACKAGE_ID, VERSION, cb));
    verifyNoInteractions(cb);
  }

  @Test
  void loadPackageWithDependenciesDependencyLoadFailsContinuesWithSiblings() throws Exception {
    Map<String, String> deps = new LinkedHashMap<>();
    deps.put(DEP_1_ID, DEP_1_VERSION);
    deps.put(DEP_2_ID, DEP_2_VERSION);
    stubPackage(PACKAGE_ID, VERSION, ROOT_PATH, deps);
    when(cacheManager.loadPackage(DEP_1_ID, DEP_1_VERSION))
        .thenThrow(new IOException("Dependency unavailable"));
    stubPackage(DEP_2_ID, DEP_2_VERSION, DEP_2_PATH);

    PackageDownloadedCallback cb = mock(PackageDownloadedCallback.class);
    when(cb.onDownloaded(any(), any(), any())).thenReturn(true);

    List<String> result = adapter.loadPackageWithDependencies(PACKAGE_ID, VERSION, cb);

    // Root and the second dep are collected despite dep1 failing.
    assertEquals(List.of(ROOT_PATH, DEP_2_PATH), result);
    verify(cb).onDownloaded(PACKAGE_ID, VERSION, ROOT_PATH);
    verify(cb, never()).onDownloaded(eq(DEP_1_ID), any(), any());
    verify(cb).onDownloaded(DEP_2_ID, DEP_2_VERSION, DEP_2_PATH);
  }

  @Test
  void loadPackageWithDependenciesCircularDependencyVisitedOnce() throws Exception {
    // Root declares dep1, dep1 declares root — cycle detection must prevent infinite loop.
    Map<String, String> rootDeps = new LinkedHashMap<>();
    rootDeps.put(DEP_1_ID, DEP_1_VERSION);
    stubPackage(PACKAGE_ID, VERSION, ROOT_PATH, rootDeps);

    Map<String, String> dep1Deps = new LinkedHashMap<>();
    dep1Deps.put(PACKAGE_ID, VERSION); // points back to root
    stubPackage(DEP_1_ID, DEP_1_VERSION, DEP_1_PATH, dep1Deps);

    PackageDownloadedCallback cb = mock(PackageDownloadedCallback.class);
    when(cb.onDownloaded(any(), any(), any())).thenReturn(true);

    List<String> result = adapter.loadPackageWithDependencies(PACKAGE_ID, VERSION, cb);

    assertEquals(List.of(ROOT_PATH, DEP_1_PATH), result);
    // Root was only visited once.
    verify(cb, times(1)).onDownloaded(PACKAGE_ID, VERSION, ROOT_PATH);
    verify(cb, times(1)).onDownloaded(DEP_1_ID, DEP_1_VERSION, DEP_1_PATH);
    verify(cacheManager, times(1)).loadPackage(PACKAGE_ID, VERSION);
  }

  @Test
  void loadPackageWithDependenciesNullPathSkipsCallbackButLoadsDeps() throws Exception {
    NpmPackage pkg = mock(NpmPackage.class);
    when(pkg.getPath()).thenReturn(null);

    JsonObject npm = mock(JsonObject.class);
    JsonObject depObj = mock(JsonObject.class);
    when(pkg.getNpm()).thenReturn(npm);
    when(npm.getJsonObject("dependencies")).thenReturn(depObj);
    when(depObj.getNames()).thenReturn(List.of(DEP_1_ID));
    when(depObj.asString(DEP_1_ID)).thenReturn(DEP_1_VERSION);
    when(cacheManager.loadPackage(PACKAGE_ID, VERSION)).thenReturn(pkg);

    stubPackage(DEP_1_ID, DEP_1_VERSION, DEP_1_PATH);

    PackageDownloadedCallback cb = mock(PackageDownloadedCallback.class);
    when(cb.onDownloaded(any(), any(), any())).thenReturn(true);

    List<String> result = adapter.loadPackageWithDependencies(PACKAGE_ID, VERSION, cb);

    // Root had a null path so it is not collected, but its dependency is.
    assertEquals(List.of(DEP_1_PATH), result);
    verify(cb, never()).onDownloaded(eq(PACKAGE_ID), any(), any());
    verify(cb).onDownloaded(DEP_1_ID, DEP_1_VERSION, DEP_1_PATH);
  }

  @Test
  void loadPackageWithDependenciesNoDependenciesNodeReturnsRootOnly() throws Exception {
    NpmPackage pkg = mock(NpmPackage.class);
    when(pkg.getPath()).thenReturn(ROOT_PATH);
    JsonObject npm = mock(JsonObject.class);
    when(pkg.getNpm()).thenReturn(npm);
    when(npm.getJsonObject("dependencies")).thenReturn(null);
    when(cacheManager.loadPackage(PACKAGE_ID, VERSION)).thenReturn(pkg);

    PackageDownloadedCallback cb = mock(PackageDownloadedCallback.class);
    when(cb.onDownloaded(PACKAGE_ID, VERSION, ROOT_PATH)).thenReturn(true);

    List<String> result = adapter.loadPackageWithDependencies(PACKAGE_ID, VERSION, cb);

    assertEquals(List.of(ROOT_PATH), result);
  }
}

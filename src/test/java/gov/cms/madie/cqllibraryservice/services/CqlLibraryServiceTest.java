package gov.cms.madie.cqllibraryservice.services;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.cms.madie.cqllibraryservice.exceptions.DuplicateKeyException;
import gov.cms.madie.cqllibraryservice.exceptions.GeneralConflictException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class CqlLibraryServiceTest {

  @InjectMocks private CqlLibraryService cqlLibraryService;
  @Mock private CqlLibraryRepository cqlLibraryRepository;

  @Test
  public void testCheckDuplicateCqlLibraryNameDoesNotThrowException() {
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(false);
    cqlLibraryService.checkDuplicateCqlLibraryName("Lib1");
    verify(cqlLibraryRepository, times(1)).existsByCqlLibraryName(eq("Lib1"));
  }

  @Test
  public void testCheckDuplicateCqlLibraryNameThrowsExceptionForExistingName() {
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(true);
    assertThrows(
        DuplicateKeyException.class, () -> cqlLibraryService.checkDuplicateCqlLibraryName("Lib1"));
  }

  @Test
  public void testIsCqlLibraryNameChangedReturnsFalseForSame() {
    CqlLibrary lib1 = CqlLibrary.builder().cqlLibraryName("Lib1").build();
    CqlLibrary lib2 = CqlLibrary.builder().cqlLibraryName("Lib1").build();
    boolean output = cqlLibraryService.isCqlLibraryNameChanged(lib1, lib2);
    assertThat(output, is(false));
  }

  @Test
  public void testIsCqlLibraryNameChangedReturnsFalseForNulls() {
    CqlLibrary lib1 = CqlLibrary.builder().build();
    CqlLibrary lib2 = CqlLibrary.builder().build();
    boolean output = cqlLibraryService.isCqlLibraryNameChanged(lib1, lib2);
    assertThat(output, is(false));
  }

  @Test
  public void testIsCqlLibraryNameChangedReturnsTrueForLib1Null() {
    CqlLibrary lib1 = CqlLibrary.builder().cqlLibraryName(null).build();
    CqlLibrary lib2 = CqlLibrary.builder().cqlLibraryName("Lib1").build();
    boolean output = cqlLibraryService.isCqlLibraryNameChanged(lib1, lib2);
    assertThat(output, is(true));
  }

  @Test
  public void testIsCqlLibraryNameChangedReturnsTrueForLib2Null() {
    CqlLibrary lib1 = CqlLibrary.builder().cqlLibraryName("Lib1").build();
    CqlLibrary lib2 = CqlLibrary.builder().cqlLibraryName(null).build();
    boolean output = cqlLibraryService.isCqlLibraryNameChanged(lib1, lib2);
    assertThat(output, is(true));
  }

  @Test
  public void testIsCqlLibraryNameChangedReturnsTrueForDifferent() {
    CqlLibrary lib1 = CqlLibrary.builder().cqlLibraryName("Lib1").build();
    CqlLibrary lib2 = CqlLibrary.builder().cqlLibraryName("Lib2").build();
    boolean output = cqlLibraryService.isCqlLibraryNameChanged(lib1, lib2);
    assertThat(output, is(true));
  }

  @Test
  public void testGetVersionedCqlLibrary() {
    List<CqlLibrary> cqlLibraries = new ArrayList<>();
    var cqlLibrary1 =
        CqlLibrary.builder()
            .cqlLibraryName("TestFHIRHelpers")
            .version(Version.builder().major(1).minor(0).revisionNumber(0).build())
            .model("QI-Core v4.1.1")
            .draft(false)
            .build();
    cqlLibraries.add(cqlLibrary1);
    when(cqlLibraryRepository.findAllByCqlLibraryNameAndDraftAndVersionAndModel(
            any(), anyBoolean(), any(), anyString()))
        .thenReturn(cqlLibraries);
    CqlLibrary versionedCqlLibrary =
        cqlLibraryService.getVersionedCqlLibrary(
            "TestFHIRHelpers", "1.0.000", Optional.of("QI-Core v4.1.1"));
    assertNotNull(versionedCqlLibrary);
    assertEquals(cqlLibrary1.getCqlLibraryName(), versionedCqlLibrary.getCqlLibraryName());
    assertEquals(cqlLibrary1.getVersion(), versionedCqlLibrary.getVersion());
    assertEquals(cqlLibrary1.getModel(), versionedCqlLibrary.getModel());
  }

  @Test
  public void testGetVersionedCqlLibraryWhenModelIsNotProvided() {
    List<CqlLibrary> cqlLibraries = new ArrayList<>();
    var cqlLibrary =
        CqlLibrary.builder()
            .cqlLibraryName("TestFHIRHelpers")
            .version(Version.builder().major(1).minor(0).revisionNumber(0).build())
            .model("QI-Core v4.1.1")
            .draft(false)
            .build();
    cqlLibraries.add(cqlLibrary);
    when(cqlLibraryRepository.findAllByCqlLibraryNameAndDraftAndVersion(any(), anyBoolean(), any()))
        .thenReturn(cqlLibraries);
    CqlLibrary versionedCqlLibrary =
        cqlLibraryService.getVersionedCqlLibrary("TestFHIRHelpers", "1.0.000", Optional.empty());
    assertNotNull(versionedCqlLibrary);
    assertEquals(cqlLibrary.getCqlLibraryName(), versionedCqlLibrary.getCqlLibraryName());
    assertEquals(cqlLibrary.getVersion(), versionedCqlLibrary.getVersion());
    assertEquals(cqlLibrary.getModel(), versionedCqlLibrary.getModel());
  }

  @Test
  public void testGetVersionedCqlShouldThrowExceptionWhenNoLibrariesAreFound() {
    List<CqlLibrary> cqlLibraries = new ArrayList<>();
    when(cqlLibraryRepository.findAllByCqlLibraryNameAndDraftAndVersion(any(), anyBoolean(), any()))
        .thenReturn(cqlLibraries);
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            cqlLibraryService.getVersionedCqlLibrary(
                "TestFHIRHelpers", "1.0.000", Optional.empty()));
  }

  @Test
  public void testGetVersionedCqlShouldThrowExceptionWhenMoreThanOneLibraryIsFound() {
    List<CqlLibrary> cqlLibraries = new ArrayList<>();
    var cqlLibrary1 =
        CqlLibrary.builder()
            .cqlLibraryName("TestFHIRHelpers")
            .version(Version.builder().major(1).minor(0).revisionNumber(0).build())
            .model("FHIR")
            .draft(false)
            .build();
    var cqlLibrary2 =
        CqlLibrary.builder()
            .cqlLibraryName("TestFHIRHelpers")
            .version(Version.builder().major(1).minor(0).revisionNumber(0).build())
            .model("QI-Core v4.1.1")
            .draft(false)
            .build();
    cqlLibraries.add(cqlLibrary1);
    cqlLibraries.add(cqlLibrary2);
    when(cqlLibraryRepository.findAllByCqlLibraryNameAndDraftAndVersion(any(), anyBoolean(), any()))
        .thenReturn(cqlLibraries);
    assertThrows(
        GeneralConflictException.class,
        () ->
            cqlLibraryService.getVersionedCqlLibrary(
                "TestFHIRHelpers", "1.0.000", Optional.empty()));
  }
}

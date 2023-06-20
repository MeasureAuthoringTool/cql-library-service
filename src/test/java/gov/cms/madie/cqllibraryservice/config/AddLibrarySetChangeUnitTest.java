package gov.cms.madie.cqllibraryservice.config;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;

import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;

@ExtendWith(MockitoExtension.class)
public class AddLibrarySetChangeUnitTest {

  private CqlLibrary library1;
  private CqlLibrary library3;
  private CqlLibrary library4;
  private LibrarySet librarySet1;
  private LibrarySet librarySet2;
  private LibrarySet librarySet3;
  @Mock private CqlLibraryRepository libraryRepository;
  @Mock private LibrarySetRepository librarySetRepository;
  @InjectMocks private AddLibrarySetChangeUnit addLibrarySetChangeUnit;

  @BeforeEach
  public void setUp() {
    library1 =
        CqlLibrary.builder()
            .id("testId")
            .cqlLibraryName("testCqlLibraryName")
            .model("testModel")
            .createdBy("testCreatedBy1")
            .librarySetId("testCqlLibrarySetId1")
            .build();

    library3 =
        CqlLibrary.builder()
            .id("testId3")
            .cqlLibraryName("testCqlLibraryName")
            .model("testModel")
            .createdBy("testCreatedBy3")
            .librarySetId("testCqlLibrarySetId1")
            .build();
    library4 =
        CqlLibrary.builder()
            .id("testId4")
            .cqlLibraryName("testCqlLibraryName")
            .model("testModel")
            .createdBy("testCreatedBy1")
            .librarySetId("testCqlLibrarySetId4")
            .build();

    librarySet1 =
        LibrarySet.builder().librarySetId("testCqlLibrarySetId1").owner("testCreatedBy1").build();
    librarySet2 =
        LibrarySet.builder().librarySetId("testCqlLibrarySetId1").owner("testCreatedBy3").build();
    librarySet3 =
        LibrarySet.builder().librarySetId("testCqlLibrarySetId4").owner("testCreatedBy1").build();
  }

  @Test
  public void addLibrarySetValues() {
    when(libraryRepository.findByCqlLibrarySetId())
        .thenReturn((List.of(library1, library3, library4)));

    addLibrarySetChangeUnit.addLibrarySetValues(librarySetRepository, libraryRepository);
    verify(libraryRepository, new Times(1)).findByCqlLibrarySetId();
    verify(librarySetRepository, new Times(1)).save(librarySet1);
    verify(librarySetRepository, new Times(1)).save(librarySet2);
    verify(librarySetRepository, new Times(1)).save(librarySet3);
  }

  @Test
  public void rollbackExecution() {
    addLibrarySetChangeUnit.rollbackExecution(librarySetRepository);
    verify(librarySetRepository, new Times(1)).deleteAll();
  }
}

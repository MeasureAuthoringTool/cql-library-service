package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;
import org.bson.Document;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@EnableMongoRepositories(basePackages = "com.gov.madie.measure.repository")
public class LibraryAclRepositoryImplTest {

  @Mock MongoTemplate mongoTemplate;

  @InjectMocks LibraryAclRepositoryImpl libraryAclRepository;

  private CqlLibrary library1;
  private CqlLibrary library2;
  private CqlLibrary library3;
  private CqlLibrary library4;
  private CqlLibrary library5;

  @BeforeEach
  void setup() {
    LibrarySet librarySet1 = LibrarySet.builder().owner("p1").librarySetId("id-1").build();
    LibrarySet librarySet2 = LibrarySet.builder().owner("p2").librarySetId("id-1").build();
    library1 =
        CqlLibrary.builder()
            .createdBy("p1")
            .id("1")
            .cqlLibraryName("test measure 1")
            .librarySetId("1-1")
            .version(Version.parse("0.0.000"))
            .model("QI-Core")
            .draft(true)
            .build();
    library2 =
        CqlLibrary.builder()
            .createdBy("p1")
            .id("2")
            .cqlLibraryName("test measure 2")
            .librarySetId("2-2")
            .version(Version.parse("0.0.000"))
            .model("QI-Core")
            .draft(true)
            .build();
    library3 =
        CqlLibrary.builder()
            .createdBy("p1")
            .id("3")
            .cqlLibraryName("library3")
            .librarySetId("id-2")
            .version(Version.parse("1.0.000"))
            .model("QDM")
            .draft(false)
            .build();
    library4 =
        CqlLibrary.builder()
            .createdBy("p2")
            .id("4")
            .cqlLibraryName("library4")
            .librarySetId("id-2")
            .version(Version.parse("1.0.000"))
            .model("QDM")
            .draft(false)
            .build();
    library5 =
        CqlLibrary.builder()
            .createdBy("p2")
            .id("5")
            .cqlLibraryName("library5")
            .librarySetId("id-2")
            .version(Version.parse("1.0.000"))
            .model("QDM")
            .draft(false)
            .build();

    library1.setLibrarySet(librarySet1);
    library2.setLibrarySet(librarySet1);
    library3.setLibrarySet(librarySet1);
    library4.setLibrarySet(librarySet2);
    library5.setLibrarySet(librarySet2);
  }

  @Test
  public void testfindAllMyLibraries() {
    AggregationResults allResults =
        new AggregationResults<>(List.of(library1, library2, library3), new Document());

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(allResults)
        .thenReturn(allResults);

    List<CqlLibrary> list = libraryAclRepository.findAllLibrariesByUser("p1");
    assertEquals(list.size(), 3);
  }
}

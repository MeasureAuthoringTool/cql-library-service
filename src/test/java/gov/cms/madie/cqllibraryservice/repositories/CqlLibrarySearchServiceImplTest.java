package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.dto.FacetDTO;
import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.dto.MadieFeatureFlag;
import gov.cms.madie.cqllibraryservice.services.AppConfigService;
import org.bson.Document;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@EnableMongoRepositories(basePackages = "com.gov.madie.measure.repository")
public class CqlLibrarySearchServiceImplTest {

  @Mock MongoTemplate mongoTemplate;
  @Mock AppConfigService appConfigService;
  @InjectMocks CqlLibrarySearchServiceImpl libraryAclRepository;

  private LibraryListDTO library1;
  private LibraryListDTO library2;
  private LibraryListDTO library3;
  private LibraryListDTO library4;
  private LibraryListDTO library5;

  @BeforeEach
  void setup() {
    library1 =
        LibraryListDTO.builder()
            .id("1")
            .cqlLibraryName("test library 1")
            .librarySetId("1-1")
            .build();
    library2 =
        LibraryListDTO.builder()
            .id("2")
            .cqlLibraryName("test library 2")
            .librarySetId("1-2")
            .build();
    library3 =
        LibraryListDTO.builder()
            .id("3")
            .cqlLibraryName("test library 3")
            .librarySetId("1-3")
            .build();
    library4 =
        LibraryListDTO.builder()
            .id("4")
            .cqlLibraryName("test library 4")
            .librarySetId("1-4")
            .build();
    library5 =
        LibraryListDTO.builder()
            .id("5")
            .cqlLibraryName("test library 5")
            .librarySetId("1-5")
            .build();
  }

  @Test
  public void testFindLibraries() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LIBRARY_SEARCH)).thenReturn(false);
    // page size 3 from 0-2
    PageRequest pageRequest = PageRequest.of(0, 3);
    List<LibraryListDTO> allLibraries = List.of(library1, library2, library3, library4, library5);

    FacetDTO facetDTO =
        FacetDTO.builder()
            .queryResults(List.of(library1, library2, library3))
            .count(Arrays.asList(allLibraries.toArray()))
            .build();

    AggregationResults pagedResults = new AggregationResults<>(List.of(facetDTO), new Document());

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(pagedResults);

    Page<LibraryListDTO> page =
        libraryAclRepository.searchLibrariesByCriteria("john", pageRequest, null, true);
    assertEquals(page.getTotalElements(), 5);
    assertEquals(page.getTotalPages(), 2);
    assertEquals(page.getContent().size(), 3);
    List<LibraryListDTO> page1Libraries = page.getContent();
    assertEquals(page1Libraries.get(0).getId(), library1.getId());
    assertEquals(page1Libraries.get(1).getId(), library2.getId());
    assertEquals(page1Libraries.get(2).getId(), library3.getId());
  }

  @Test
  public void testFindMyActiveLibrariesWithSearchTerm() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LIBRARY_SEARCH)).thenReturn(false);
    PageRequest pageRequest = PageRequest.of(0, 3);

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(library1, library2)).count(List.of(1, 2)).build();
    AggregationResults pagedResults = new AggregationResults<>(List.of(facetDTO), new Document());

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenReturn(pagedResults);

    String librarySearchCriteria = "test";
    Page<LibraryListDTO> page =
        libraryAclRepository.searchLibrariesByCriteria(
            "john", pageRequest, librarySearchCriteria, true);
    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    List<LibraryListDTO> page1Libraries = page.getContent();
    assertEquals(page1Libraries.get(0).getCqlLibraryName(), library1.getCqlLibraryName());
    assertEquals(page1Libraries.get(1).getCqlLibraryName(), library2.getCqlLibraryName());
  }
}

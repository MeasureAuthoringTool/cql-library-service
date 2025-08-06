package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.dto.*;
import gov.cms.madie.cqllibraryservice.services.AppConfigService;
import gov.cms.madie.models.library.CqlLibrary;
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
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@EnableMongoRepositories(basePackages = "com.gov.madie.measure.repository")
public class CqlLibrarySearchServiceImplTest {

  @Mock MongoTemplate mongoTemplate;
  @Mock AppConfigService appConfigService;
  @InjectMocks CqlLibrarySearchServiceImpl cqlLibrarySearchServiceImpl;

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
        cqlLibrarySearchServiceImpl.searchLibrariesByCriteria("john", pageRequest, null, true);
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

    var librarySearchCriteria = LibrarySearchCriteria.builder().searchField("test").build();
    Page<LibraryListDTO> page =
        cqlLibrarySearchServiceImpl.searchLibrariesByCriteria(
            "john", pageRequest, librarySearchCriteria, true);
    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);
    List<LibraryListDTO> page1Libraries = page.getContent();
    assertEquals(page1Libraries.get(0).getCqlLibraryName(), library1.getCqlLibraryName());
    assertEquals(page1Libraries.get(1).getCqlLibraryName(), library2.getCqlLibraryName());
  }

  @Test
  public void testFindLibrariesInSets() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LIBRARY_SEARCH)).thenReturn(true);
    // page size 3 from 0-2
    PageRequest pageRequest = PageRequest.of(0, 3);

    LibrarySetMatchCountDTO match1 = new LibrarySetMatchCountDTO("setIdi", 2, "lib1");
    LibrarySetMatchCountDTO match2 = new LibrarySetMatchCountDTO("setId2", 1, "lib3");
    List<LibrarySetMatchCountDTO> matchResults = List.of(match1, match2);

    List<LibraryListDTO> allLibraries = List.of(library1, library2, library3, library4, library5);
    FacetDTO facetDTO =
        FacetDTO.builder()
            .queryResults(List.of(library1, library2, library3))
            .count(Arrays.asList(allLibraries.toArray()))
            .build();

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenAnswer(
            invocation -> {
              Class<?> outputClass = invocation.getArgument(2);
              if (outputClass.equals(FacetDTO.class)) {
                return new AggregationResults<>(List.of(facetDTO), new Document());
              } else if (outputClass.equals(LibrarySetMatchCountDTO.class)) {
                return new AggregationResults<>(matchResults, new Document());
              } else if (outputClass.equals(LibraryListDTO.class)) {
                return new AggregationResults<>(
                    List.of(library1, library2, library3), new Document());
              }
              return null;
            });

    Page<LibraryListDTO> page =
        cqlLibrarySearchServiceImpl.searchLibrariesByCriteria("john", pageRequest, null, true);
    assertEquals(page.getTotalElements(), 3);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 3);
    List<LibraryListDTO> page1Libraries = page.getContent();
    assertEquals(page1Libraries.get(0).getId(), library1.getId());
    assertEquals(page1Libraries.get(1).getId(), library2.getId());
    assertEquals(page1Libraries.get(2).getId(), library3.getId());
  }

  @Test
  public void testFindLibrariesInSetsWhenNoMatchFound() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LIBRARY_SEARCH)).thenReturn(true);
    PageRequest pageRequest = PageRequest.of(0, 3);

    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(CqlLibrary.class), eq(LibrarySetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(Collections.emptyList(), new Document()));

    Page<LibraryListDTO> page =
        cqlLibrarySearchServiceImpl.searchLibrariesByCriteria("john", pageRequest, null, true);

    assertTrue(page.getContent().isEmpty());
    assertEquals(0, page.getTotalElements());
  }

  @Test
  public void testSearchCriteriaWithEmptySearchField() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LIBRARY_SEARCH)).thenReturn(false);
    PageRequest pageRequest = PageRequest.of(0, 2);

    LibrarySearchCriteria criteria = LibrarySearchCriteria.builder().searchField("").build();

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(library1)).count(List.of(1)).build();

    when(mongoTemplate.aggregate(any(Aggregation.class), eq(CqlLibrary.class), eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    Page<LibraryListDTO> page =
        cqlLibrarySearchServiceImpl.searchLibrariesByCriteria("user", pageRequest, criteria, true);

    assertEquals(1, page.getTotalElements());
    assertEquals(1, page.getContent().size());
    assertEquals(library1.getId(), page.getContent().get(0).getId());
  }

  @Test
  public void testSearchLibrariesWithoutFilteringByUser() {
    when(appConfigService.isFlagEnabled(MadieFeatureFlag.LIBRARY_SEARCH)).thenReturn(false);
    PageRequest pageRequest = PageRequest.of(0, 1);

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(library1)).count(List.of(1)).build();

    when(mongoTemplate.aggregate(any(Aggregation.class), eq(CqlLibrary.class), eq(FacetDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(facetDTO), new Document()));

    Page<LibraryListDTO> page =
        cqlLibrarySearchServiceImpl.searchLibrariesByCriteria("john", pageRequest, null, false);

    assertEquals(1, page.getTotalElements());
    assertEquals(1, page.getContent().size());
  }

  @Test
  void testFindLibrariesByLibrarySetIdWithoutSearchCriteriaAndWithoutSorting() {
    String librarySetId = "set123";
    boolean sortByLatestVersion = false;

    List<LibraryListDTO> mockResults = List.of(new LibraryListDTO(), new LibraryListDTO());

    AggregationResults<LibraryListDTO> mockAggregationResults =
        new AggregationResults<>(mockResults, new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(CqlLibrary.class), eq(LibraryListDTO.class)))
        .thenReturn(mockAggregationResults);

    List<LibraryListDTO> results =
        cqlLibrarySearchServiceImpl.findLibrariesByLibrarySetId(
            librarySetId, sortByLatestVersion, null);

    assertEquals(2, results.size());
    verify(mongoTemplate)
        .aggregate(any(Aggregation.class), eq(CqlLibrary.class), eq(LibraryListDTO.class));
  }

  @Test
  void testFindLibrariesByLibrarySetIdWithoutSearchCriteriaWithSorting() {
    String librarySetId = "set123";
    boolean sortByLatestVersion = true;

    List<LibraryListDTO> mockResults = List.of(new LibraryListDTO());

    AggregationResults<LibraryListDTO> mockAggregationResults =
        new AggregationResults<>(mockResults, new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(CqlLibrary.class), eq(LibraryListDTO.class)))
        .thenReturn(mockAggregationResults);

    List<LibraryListDTO> results =
        cqlLibrarySearchServiceImpl.findLibrariesByLibrarySetId(
            librarySetId, sortByLatestVersion, null);

    assertEquals(1, results.size());
    verify(mongoTemplate)
        .aggregate(any(Aggregation.class), eq(CqlLibrary.class), eq(LibraryListDTO.class));
  }

  @Test
  void testFindLibrariesByLibrarySetIdWithSearchCriteriaLibraryAndVersion() {
    String librarySetId = "set456";
    boolean sortByLatestVersion = false;

    LibrarySearchCriteria searchCriteria = new LibrarySearchCriteria();
    searchCriteria.setSearchField("sample");
    searchCriteria.setOptionalSearchProperties(List.of("library", "version"));

    List<LibraryListDTO> mockResults = List.of();

    AggregationResults<LibraryListDTO> mockAggregationResults =
        new AggregationResults<>(mockResults, new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(CqlLibrary.class), eq(LibraryListDTO.class)))
        .thenReturn(mockAggregationResults);

    List<LibraryListDTO> results =
        cqlLibrarySearchServiceImpl.findLibrariesByLibrarySetId(
            librarySetId, sortByLatestVersion, searchCriteria);

    assertNotNull(results);
    verify(mongoTemplate)
        .aggregate(any(Aggregation.class), eq(CqlLibrary.class), eq(LibraryListDTO.class));
  }

  @Test
  void testFindLibrariesByLibrarySetIdWithSearchCriteria() {
    String librarySetId = "set789";
    boolean sortByLatestVersion = false;

    LibrarySearchCriteria searchCriteria = new LibrarySearchCriteria();
    searchCriteria.setSearchField("1.0.0");
    searchCriteria.setOptionalSearchProperties(List.of("version"));

    AggregationResults<LibraryListDTO> mockAggregationResults =
        new AggregationResults<>(Collections.emptyList(), new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(CqlLibrary.class), eq(LibraryListDTO.class)))
        .thenReturn(mockAggregationResults);

    List<LibraryListDTO> results =
        cqlLibrarySearchServiceImpl.findLibrariesByLibrarySetId(
            librarySetId, sortByLatestVersion, searchCriteria);

    assertNotNull(results);
    verify(mongoTemplate)
        .aggregate(any(Aggregation.class), eq(CqlLibrary.class), eq(LibraryListDTO.class));
  }
}

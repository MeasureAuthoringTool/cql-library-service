package gov.cms.madie.cqllibraryservice.repositories;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import gov.cms.madie.cqllibraryservice.dto.*;
import gov.cms.madie.cqllibraryservice.locks.CqlLibraryLock;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@ExtendWith(MockitoExtension.class)
@EnableMongoRepositories(basePackages = "com.gov.madie.measure.repository")
public class CqlLibrarySearchServiceImplTest {

  @Mock MongoTemplate mongoTemplate;
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
  public void testFindOwnedLibraries() {
    // page size 3 from 0-2
    PageRequest pageRequest = PageRequest.of(0, 3);

    List<LibraryListDTO> ownedLibraries = List.of(library1, library2, library3);

    FacetDTO facetDTO =
        FacetDTO.builder()
            .queryResults(ownedLibraries)
            .count(Arrays.asList(ownedLibraries.toArray()))
            .build();

    List<LibrarySetMatchCountDTO> matchResults =
        List.of(
            new LibrarySetMatchCountDTO("setId1", 2, "lib1"),
            new LibrarySetMatchCountDTO("setId2", 1, "lib3"));

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenAnswer(
            invocation -> {
              Class<?> outputClass = invocation.getArgument(2);
              if (outputClass.equals(FacetDTO.class)) {
                return new AggregationResults<>(List.of(facetDTO), new Document());
              } else if (outputClass.equals(LibrarySetMatchCountDTO.class)) {
                return new AggregationResults<>(matchResults, new Document());
              } else if (outputClass.equals(LibraryListDTO.class)) {
                return new AggregationResults<>(ownedLibraries, new Document());
              }
              return null;
            });

    Page<LibraryListDTO> page =
        cqlLibrarySearchServiceImpl.searchLibrariesByCriteria(
            "john", pageRequest, null, OwnershipType.OWNED);

    assertEquals(page.getTotalElements(), 3);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 3);

    List<LibraryListDTO> page1Libraries = page.getContent();
    assertEquals(page1Libraries.get(0).getId(), library1.getId());
    assertEquals(page1Libraries.get(1).getId(), library2.getId());
    assertEquals(page1Libraries.get(2).getId(), library3.getId());
  }

  @Test
  public void testSearchLibrariesByCriteriaBuildsValidReviewPipeline() {
    PageRequest pageRequest = PageRequest.of(0, 3);

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenAnswer(
            invocation -> {
              Class<?> outputClass = invocation.getArgument(2);
              if (outputClass.equals(LibrarySetMatchCountDTO.class)) {
                return new AggregationResults<>(
                    List.of(new LibrarySetMatchCountDTO("1-1", 1, "1")), new Document());
              }
              FacetDTO facetDTO =
                  FacetDTO.builder().queryResults(List.of(library1)).count(List.of()).build();
              return new AggregationResults<>(List.of(facetDTO), new Document());
            });

    LibrarySearchCriteria criteria = new LibrarySearchCriteria("Ready", List.of("review"));
    cqlLibrarySearchServiceImpl.searchLibrariesByCriteria(
        "john", pageRequest, criteria, OwnershipType.OWNED);

    ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
    verify(mongoTemplate, atLeastOnce()).aggregate(captor.capture(), (Class<?>) any(), any());

    String pipelines =
        captor.getAllValues().stream()
            .map(agg -> agg.toPipeline(Aggregation.DEFAULT_CONTEXT).toString())
            .collect(Collectors.joining("\n"));

    assertTrue(pipelines.contains("cqlLibraryReview"));
    assertTrue(pipelines.contains("libraryId"));
    assertTrue(pipelines.contains("toString"));
    // Derives the display label via $switch, one case per in-review status
    assertTrue(pipelines.contains("READY_FOR_REVIEW"));
    assertTrue(pipelines.contains("Ready"));
    assertTrue(pipelines.contains("IN_PROGRESS"));
    assertTrue(pipelines.contains("In Progress"));
    assertTrue(pipelines.contains("COMPLETE"));
    assertTrue(pipelines.contains("Complete"));
    assertTrue(pipelines.contains("reviewStatus"));
  }

  @Test
  public void testFindSharedLibraries() {
    // page size 3 from 0-2
    PageRequest pageRequest = PageRequest.of(0, 3);

    List<LibraryListDTO> sharedLibraries = List.of(library1, library2, library3);

    FacetDTO facetDTO =
        FacetDTO.builder()
            .queryResults(sharedLibraries)
            .count(Arrays.asList(sharedLibraries.toArray()))
            .build();

    List<LibrarySetMatchCountDTO> matchResults =
        List.of(
            new LibrarySetMatchCountDTO("setId1", 2, "lib1"),
            new LibrarySetMatchCountDTO("setId2", 1, "lib3"));

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenAnswer(
            invocation -> {
              Class<?> outputClass = invocation.getArgument(2);
              if (outputClass.equals(FacetDTO.class)) {
                return new AggregationResults<>(List.of(facetDTO), new Document());
              } else if (outputClass.equals(LibrarySetMatchCountDTO.class)) {
                return new AggregationResults<>(matchResults, new Document());
              } else if (outputClass.equals(LibraryListDTO.class)) {
                return new AggregationResults<>(sharedLibraries, new Document());
              }
              return null;
            });

    Page<LibraryListDTO> page =
        cqlLibrarySearchServiceImpl.searchLibrariesByCriteria(
            "john", pageRequest, null, OwnershipType.SHARED);

    assertEquals(page.getTotalElements(), 3);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 3);

    List<LibraryListDTO> page1Libraries = page.getContent();
    assertEquals(page1Libraries.get(0).getId(), library1.getId());
    assertEquals(page1Libraries.get(1).getId(), library2.getId());
    assertEquals(page1Libraries.get(2).getId(), library3.getId());
  }

  @Test
  public void testFindOwnedLibrariesWithSearchTerm() {
    // page size 3 from 0-2
    PageRequest pageRequest = PageRequest.of(0, 3);

    List<LibraryListDTO> ownedLibraries = List.of(library1, library2);

    FacetDTO facetDTO =
        FacetDTO.builder()
            .queryResults(ownedLibraries)
            .count(Arrays.asList(ownedLibraries.toArray()))
            .build();

    List<LibrarySetMatchCountDTO> matchResults =
        List.of(
            new LibrarySetMatchCountDTO("setId1", 1, "lib1"),
            new LibrarySetMatchCountDTO("setId2", 1, "lib2"));

    when(mongoTemplate.aggregate(any(Aggregation.class), (Class<?>) any(), any()))
        .thenAnswer(
            invocation -> {
              Class<?> outputClass = invocation.getArgument(2);
              if (outputClass.equals(FacetDTO.class)) {
                return new AggregationResults<>(List.of(facetDTO), new Document());
              } else if (outputClass.equals(LibrarySetMatchCountDTO.class)) {
                return new AggregationResults<>(matchResults, new Document());
              } else if (outputClass.equals(LibraryListDTO.class)) {
                return new AggregationResults<>(ownedLibraries, new Document());
              }
              return null;
            });

    var librarySearchCriteria = LibrarySearchCriteria.builder().searchField("test").build();

    Page<LibraryListDTO> page =
        cqlLibrarySearchServiceImpl.searchLibrariesByCriteria(
            "john", pageRequest, librarySearchCriteria, OwnershipType.OWNED);

    assertEquals(page.getTotalElements(), 2);
    assertEquals(page.getTotalPages(), 1);
    assertEquals(page.getContent().size(), 2);

    List<LibraryListDTO> page1Libraries = page.getContent();
    assertEquals(library1.getCqlLibraryName(), page1Libraries.get(0).getCqlLibraryName());
    assertEquals(library2.getCqlLibraryName(), page1Libraries.get(1).getCqlLibraryName());
  }

  @Test
  public void testFindOwnedLibrariesInSets() {
    // page size 3 from 0-2
    PageRequest pageRequest = PageRequest.of(0, 3);

    LibrarySetMatchCountDTO match1 = new LibrarySetMatchCountDTO("setIdi", 2, "lib1");
    LibrarySetMatchCountDTO match2 = new LibrarySetMatchCountDTO("setId2", 1, "lib3");
    List<LibrarySetMatchCountDTO> matchResults = List.of(match1, match2);

    List<LibraryListDTO> ownedLibraries = List.of(library1, library2, library3, library4, library5);
    FacetDTO facetDTO =
        FacetDTO.builder()
            .queryResults(List.of(library1, library2, library3))
            .count(Arrays.asList(ownedLibraries.toArray()))
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
        cqlLibrarySearchServiceImpl.searchLibrariesByCriteria(
            "john", pageRequest, null, OwnershipType.OWNED);
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
    PageRequest pageRequest = PageRequest.of(0, 3);

    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(CqlLibrary.class), eq(LibrarySetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(Collections.emptyList(), new Document()));

    Page<LibraryListDTO> page =
        cqlLibrarySearchServiceImpl.searchLibrariesByCriteria(
            "john", pageRequest, null, OwnershipType.OWNED);

    assertTrue(page.getContent().isEmpty());
    assertEquals(0, page.getTotalElements());
  }

  @Test
  public void testLockInfoRemovedForCurrentUser() {
    PageRequest pageRequest = PageRequest.of(0, 1);

    LibrarySetMatchCountDTO match1 = new LibrarySetMatchCountDTO("setIdi", 2, "lib1");
    LibrarySetMatchCountDTO match2 = new LibrarySetMatchCountDTO("setId2", 1, "lib3");
    List<LibrarySetMatchCountDTO> matchResults = List.of(match1, match2);

    LibraryListDTO lockedByCurrentUser =
        LibraryListDTO.builder()
            .id("lock-lib-1")
            .librarySetId("set-lock-1")
            .cqlLibraryName("Locked Library")
            .cqlLibraryLock(
                CqlLibraryLock.builder().cqlLibraryId("lock-lib-1").lockedBy("john").build())
            .build();

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(lockedByCurrentUser)).count(List.of(1)).build();

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
        cqlLibrarySearchServiceImpl.searchLibrariesByCriteria(
            "john", pageRequest, null, OwnershipType.OWNED);

    assertEquals(2, page.getTotalElements());
    assertNull(page.getContent().get(0).getCqlLibraryLock());
  }

  @Test
  public void testLockInfoRetainedForDifferentUser() {
    PageRequest pageRequest = PageRequest.of(0, 1);

    LibraryListDTO lockedByOther =
        LibraryListDTO.builder()
            .id("lock-lib-2")
            .librarySetId("set-lock-2")
            .cqlLibraryName("Locked Library Other")
            .cqlLibraryLock(
                CqlLibraryLock.builder().cqlLibraryId("lock-lib-2").lockedBy("someoneElse").build())
            .build();
    LibrarySetMatchCountDTO match1 = new LibrarySetMatchCountDTO("setIdi", 2, "lib1");
    LibrarySetMatchCountDTO match2 = new LibrarySetMatchCountDTO("setId2", 1, "lib3");
    List<LibrarySetMatchCountDTO> matchResults = List.of(match1, match2);

    FacetDTO facetDTO =
        FacetDTO.builder().queryResults(List.of(lockedByOther)).count(List.of(1)).build();

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
        cqlLibrarySearchServiceImpl.searchLibrariesByCriteria(
            "john", pageRequest, null, OwnershipType.OWNED);

    assertEquals(2, page.getTotalElements());
    assertNotNull(
        page.getContent().get(0).getCqlLibraryLock(), "Lock from another user should be retained");
    assertEquals(
        "someoneElse",
        page.getContent().get(0).getCqlLibraryLock().getLockedBy(),
        "LockedBy mismatch");
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
  void testFindLibrariesByLibrarySetIdJoinsReviewWhenFilteringByReview() {
    LibrarySearchCriteria searchCriteria = new LibrarySearchCriteria();
    searchCriteria.setSearchField("Ready");
    searchCriteria.setOptionalSearchProperties(List.of("review"));

    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(CqlLibrary.class), eq(LibraryListDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(new LibraryListDTO()), new Document()));

    cqlLibrarySearchServiceImpl.findLibrariesByLibrarySetId("set-1", false, searchCriteria);

    ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
    verify(mongoTemplate)
        .aggregate(captor.capture(), eq(CqlLibrary.class), eq(LibraryListDTO.class));

    String pipeline = captor.getValue().toString();
    assertTrue(pipeline.contains("cqlLibraryReview"));
    assertTrue(pipeline.contains("reviewStatus"));
    assertTrue(pipeline.contains("READY_FOR_REVIEW"));
  }

  @Test
  void testFindLibrariesByLibrarySetIdSkipsReviewJoinForNonReviewSearches() {
    LibrarySearchCriteria searchCriteria = new LibrarySearchCriteria();
    searchCriteria.setSearchField("sample");
    searchCriteria.setOptionalSearchProperties(List.of("library"));

    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(CqlLibrary.class), eq(LibraryListDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(new LibraryListDTO()), new Document()));

    cqlLibrarySearchServiceImpl.findLibrariesByLibrarySetId("set-1", false, searchCriteria);

    ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
    verify(mongoTemplate)
        .aggregate(captor.capture(), eq(CqlLibrary.class), eq(LibraryListDTO.class));

    assertFalse(captor.getValue().toString().contains("cqlLibraryReview"));
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

  @Test
  void testFindLibrariesForAccessReportEmptyList() {
    List<LibraryListDTO> results =
        cqlLibrarySearchServiceImpl.findLibrariesForAccessReport(Collections.emptyList());

    assertNotNull(results);
    assertTrue(results.isEmpty());
    verifyNoInteractions(mongoTemplate);
  }

  @Test
  void testFindLibrariesForAccessReportNullList() {
    List<LibraryListDTO> results = cqlLibrarySearchServiceImpl.findLibrariesForAccessReport(null);

    assertNotNull(results);
    assertTrue(results.isEmpty());
    verifyNoInteractions(mongoTemplate);
  }

  @Test
  void testFindLibrariesForAccessReport() {
    List<String> libraryIds = List.of("lib1", "lib2");

    gov.cms.madie.models.access.AclSpecification acl1 =
        new gov.cms.madie.models.access.AclSpecification();
    acl1.setUserId("sharedUser1");

    gov.cms.madie.models.access.AclSpecification acl2 =
        new gov.cms.madie.models.access.AclSpecification();
    acl2.setUserId("sharedUser2");

    LibrarySet librarySet1 =
        LibrarySet.builder()
            .librarySetId("libSet1")
            .owner("owner1")
            .acls(List.of(acl1, acl2))
            .build();

    LibrarySet librarySet2 =
        LibrarySet.builder().librarySetId("libSet2").owner("owner2").acls(null).build();

    LibraryListDTO dto1 =
        LibraryListDTO.builder()
            .id("lib1")
            .cqlLibraryName("Test Library 1")
            .model("QI-Core v4.1.1")
            .librarySetId("libSet1")
            .librarySet(librarySet1)
            .build();

    LibraryListDTO dto2 =
        LibraryListDTO.builder()
            .id("lib2")
            .cqlLibraryName("Test Library 2")
            .model("FHIR")
            .librarySetId("libSet2")
            .librarySet(librarySet2)
            .build();

    List<LibraryListDTO> mockResults = List.of(dto1, dto2);

    AggregationResults<LibraryListDTO> mockAggregationResults =
        new AggregationResults<>(mockResults, new Document());

    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(CqlLibrary.class), eq(LibraryListDTO.class)))
        .thenReturn(mockAggregationResults);

    List<LibraryListDTO> results =
        cqlLibrarySearchServiceImpl.findLibrariesForAccessReport(libraryIds);

    assertNotNull(results);
    assertEquals(2, results.size());

    // Verify first result
    assertEquals("lib1", results.get(0).getId());
    assertEquals("Test Library 1", results.get(0).getCqlLibraryName());
    assertEquals("QI-Core v4.1.1", results.get(0).getModel());
    assertEquals("owner1", results.get(0).getLibrarySet().getOwner());
    assertEquals(2, results.get(0).getLibrarySet().getAcls().size());

    // Verify second result with no ACLs
    assertEquals("lib2", results.get(1).getId());
    assertEquals("Test Library 2", results.get(1).getCqlLibraryName());
    assertEquals("FHIR", results.get(1).getModel());
    assertEquals("owner2", results.get(1).getLibrarySet().getOwner());
    assertNull(results.get(1).getLibrarySet().getAcls());

    verify(mongoTemplate)
        .aggregate(any(Aggregation.class), eq(CqlLibrary.class), eq(LibraryListDTO.class));
  }
}

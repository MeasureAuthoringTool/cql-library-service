package gov.cms.madie.cqllibraryservice.repositories;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import gov.cms.madie.cqllibraryservice.dto.*;
import gov.cms.madie.cqllibraryservice.models.ExternalLibrary;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.library.CqlLibrary;
import java.util.List;
import java.util.stream.Stream;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

@ExtendWith(MockitoExtension.class)
class CqlLibrarySourceSearchTest {
  @Mock private MongoTemplate mongoTemplate;
  @InjectMocks private CqlLibrarySearchServiceImpl searchService;

  static Stream<Arguments> sourcesAndOwnership() {
    return Stream.of(OwnershipType.ALL, OwnershipType.OWNED, OwnershipType.SHARED)
        .flatMap(
            ownership ->
                Stream.of(null, LibrarySource.ALL, LibrarySource.EXTERNAL, LibrarySource.MADIE)
                    .map(source -> Arguments.of(ownership, source)));
  }

  @ParameterizedTest
  @MethodSource("sourcesAndOwnership")
  void sourceIsAppliedToBothMatchingAndResults(OwnershipType ownership, LibrarySource source) {
    LibrarySource effectiveSource =
        ownership != OwnershipType.ALL
            ? LibrarySource.MADIE
            : source == null ? LibrarySource.ALL : source;
    Class<?> collectionType =
        effectiveSource == LibrarySource.EXTERNAL ? ExternalLibrary.class : CqlLibrary.class;
    LibraryListDTO library = LibraryListDTO.builder().id("library").librarySetId("set").build();
    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(collectionType), eq(LibrarySetMatchCountDTO.class)))
        .thenReturn(
            new AggregationResults<>(
                List.of(new LibrarySetMatchCountDTO("set", 1, "library")), new Document()));
    when(mongoTemplate.aggregate(any(Aggregation.class), eq(collectionType), eq(FacetDTO.class)))
        .thenReturn(
            new AggregationResults<>(
                List.of(FacetDTO.builder().queryResults(List.of(library)).build()),
                new Document()));

    var page =
        searchService.searchLibrariesByCriteria(
            "user",
            PageRequest.of(0, 10, Sort.by("version")),
            LibrarySearchCriteria.builder().librarySource(source).build(),
            ownership);

    assertEquals(1, page.getTotalElements());
    ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
    verify(mongoTemplate, times(2))
        .aggregate(captor.capture(), eq(collectionType), any(Class.class));
    for (Aggregation aggregation : captor.getAllValues()) {
      List<Document> pipeline = aggregation.toPipeline(Aggregation.DEFAULT_CONTEXT);
      if (effectiveSource == LibrarySource.ALL) {
        assertEquals(
            "externalLibrary", pipeline.get(0).get("$unionWith", Document.class).getString("coll"));
      } else {
        assertFalse(pipeline.stream().anyMatch(stage -> stage.containsKey("$unionWith")));
        assertTrue(
            pipeline
                .get(0)
                .containsKey(effectiveSource == LibrarySource.EXTERNAL ? "$project" : "$lookup"));
      }
    }
  }

  @ParameterizedTest
  @EnumSource(LibrarySource.class)
  void expansionUsesTheSelectedSource(LibrarySource source) {
    Class<?> collectionType =
        source == LibrarySource.EXTERNAL ? ExternalLibrary.class : CqlLibrary.class;
    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(collectionType), eq(LibraryListDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()));

    searchService.findLibrariesByLibrarySetId(
        "set", true, LibrarySearchCriteria.builder().librarySource(source).build());

    ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
    verify(mongoTemplate).aggregate(captor.capture(), eq(collectionType), eq(LibraryListDTO.class));
    List<Document> pipeline = captor.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT);
    assertEquals(
        source == LibrarySource.ALL,
        pipeline.stream().anyMatch(stage -> stage.containsKey("$unionWith")));
    assertTrue(pipeline.stream().anyMatch(stage -> stage.containsKey("$sort")));
  }

  @Test
  void expansionWithoutSourceIncludesBothSources() {
    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(CqlLibrary.class), eq(LibraryListDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()));

    searchService.findLibrariesByLibrarySetId("set", false, null);

    ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
    verify(mongoTemplate)
        .aggregate(captor.capture(), eq(CqlLibrary.class), eq(LibraryListDTO.class));
    assertTrue(
        captor.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT).stream()
            .anyMatch(stage -> stage.containsKey("$unionWith")));
  }

  @Test
  void emptyExternalSearchDoesNotRunResultAggregation() {
    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(ExternalLibrary.class), eq(LibrarySetMatchCountDTO.class)))
        .thenReturn(new AggregationResults<>(List.of(), new Document()));

    var page =
        searchService.searchLibrariesByCriteria(
            "user",
            PageRequest.of(0, 10),
            LibrarySearchCriteria.builder().librarySource(LibrarySource.EXTERNAL).build(),
            OwnershipType.ALL);

    assertTrue(page.isEmpty());
    assertEquals(0, page.getTotalElements());
    verify(mongoTemplate, times(1))
        .aggregate(any(Aggregation.class), eq(ExternalLibrary.class), any(Class.class));
  }
}

package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.dto.*;
import gov.cms.madie.cqllibraryservice.services.AppConfigService;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.library.CqlLibrary;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static gov.cms.madie.cqllibraryservice.utils.SearchUtils.appendAdditionalSearchCriteria;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;

@Repository
public class CqlLibrarySearchServiceImpl implements CqlLibrarySearchService {
  private final MongoTemplate mongoTemplate;

  private final AppConfigService appConfigService;

  private LookupOperation getLookupOperation() {
    return LookupOperation.newLookup()
        .from("librarySet")
        .localField("librarySetId")
        .foreignField("librarySetId")
        .as("librarySet");
  }

  private Criteria getAclCriteria(String userId) {
    return new Criteria()
        .orOperator(
            Criteria.where("librarySet.owner").is(userId),
            Criteria.where("librarySet.acls.userId"),
            Criteria.where("librarySet.acls")
                .elemMatch(
                    Criteria.where("userId")
                        .regex("^\\Q" + userId + "\\E$", "i")
                        .and("roles")
                        .in(RoleEnum.SHARED_WITH)));
  }

  public CqlLibrarySearchServiceImpl(
      MongoTemplate mongoTemplate, AppConfigService appConfigService) {
    this.mongoTemplate = mongoTemplate;
    this.appConfigService = appConfigService;
  }

  @Override
  public Page<LibraryListDTO> searchLibrariesByCriteria(
      String userId,
      Pageable pageable,
      LibrarySearchCriteria librarySearchCriteria,
      boolean filterByCurrentUser) {
    LookupOperation lookupOperation = getLookupOperation();
    UnwindOperation unwindOperation = unwind("librarySet");

    Criteria criteria = Criteria.where("active").is(true);

    if (librarySearchCriteria != null
        && StringUtils.isNotBlank(librarySearchCriteria.getSearchField())) {
      appendAdditionalSearchCriteria(criteria, librarySearchCriteria);
    }
    Criteria librarySetCriteria = new Criteria();
    if (filterByCurrentUser && StringUtils.isNotBlank(userId)) {
      librarySetCriteria = getAclCriteria(userId);
    }
    MatchOperation matchOperation = match(new Criteria().andOperator(criteria, librarySetCriteria));

    FacetOperation facets =
        facet(sortByCount("id"))
            .as("count")
            .and(
                sort(pageable.getSort()),
                skip(pageable.getOffset()),
                limit(pageable.getPageSize()),
                project(LibraryListDTO.class))
            .as("queryResults");

    if (appConfigService.isFlagEnabled(MadieFeatureFlag.LIBRARY_SEARCH)) {
      // Find all the libraries that matches the given Criteria and fetch unique librarySetIds
      List<LibrarySetMatchCountDTO> matchedLibrarySetCounts =
          mongoTemplate
              .aggregate(
                  newAggregation(
                      lookupOperation,
                      unwindOperation,
                      matchOperation,
                      group("librarySetId")
                          .count()
                          .as("matchCount")
                          .first("_id")
                          .as("matchedLibraryId")),
                  CqlLibrary.class,
                  LibrarySetMatchCountDTO.class)
              .getMappedResults();

      Map<String, LibrarySetMatchCountDTO> matchInfoMap =
          matchedLibrarySetCounts.stream()
              .collect(
                  Collectors.toMap(LibrarySetMatchCountDTO::getLibrarySetId, Function.identity()));

      List<String> matchedLibrarySetIds = new ArrayList<>(matchInfoMap.keySet());

      if (matchedLibrarySetIds.isEmpty()) {
        return new PageImpl<>(Collections.emptyList(), pageable, 0);
      }

      // Fetch all libraries associated to each LibrarySetId
      MatchOperation matchLibrarySetIds =
          match(Criteria.where("librarySetId").in(matchedLibrarySetIds));

      // Sort those libraries based on version and draft status
      SortOperation sortByVersionAndDraft = sort(Sort.by(Sort.Direction.DESC, "draft", "version"));
      GroupOperation groupByLibrarySet = group("librarySetId").first("$$ROOT").as("selectedDoc");

      ReplaceRootOperation replaceRoot = replaceRoot("selectedDoc");

      Aggregation pipeline =
          newAggregation(
              lookupOperation,
              unwindOperation,
              matchLibrarySetIds,
              sortByVersionAndDraft,
              groupByLibrarySet,
              replaceRoot,
              sort(Sort.by(Sort.Direction.DESC, "lastModifiedAt")),
              skip(pageable.getOffset()),
              limit(pageable.getPageSize()));
      List<LibraryListDTO> libraries =
          mongoTemplate
              .aggregate(pipeline, CqlLibrary.class, LibraryListDTO.class)
              .getMappedResults();
      for (LibraryListDTO dto : libraries) {
        LibrarySetMatchCountDTO matchInfo = matchInfoMap.get(dto.getLibrarySetId());

        if (matchInfo != null) {
          boolean hasAssociated;
          if (matchInfo.getMatchCount() > 1) {
            hasAssociated = true;
          } else {
            String selectedId = dto.getId();
            String matchedId = matchInfo.getMatchedLibraryId();
            hasAssociated = matchedId != null && !matchedId.equals(selectedId);
          }
          dto.setHasAssociatedLibraries(hasAssociated);
        } else {
          dto.setHasAssociatedLibraries(false);
        }
      }
      long totalSize = matchInfoMap.size();
      return new PageImpl<>(libraries, pageable, totalSize);

    } else {
      Aggregation pipeline =
          newAggregation(lookupOperation, unwindOperation, matchOperation, facets);

      List<FacetDTO> results =
          mongoTemplate.aggregate(pipeline, CqlLibrary.class, FacetDTO.class).getMappedResults();

      return new PageImpl<>(
          results.get(0).getQueryResults(), pageable, results.get(0).getCount().size());
    }
  }

  public List<LibraryListDTO> findLibrariesByLibrarySetId(
      String librarySetId,
      boolean sortByLatestVersion,
      LibrarySearchCriteria librarySearchCriteria) {
    Criteria criteria = Criteria.where("active").is(true).and("librarySetId").is(librarySetId);

    if (librarySearchCriteria != null
        && StringUtils.isNotBlank(librarySearchCriteria.getSearchField())) {
      appendAdditionalSearchCriteria(criteria, librarySearchCriteria);
    }

    MatchOperation matchOperation = match(criteria);

    Aggregation aggregation;
    if (sortByLatestVersion) {
      SortOperation sortOperation = sort(Sort.by(Sort.Direction.DESC, "version"));
      aggregation = newAggregation(matchOperation, sortOperation);
    } else {
      aggregation = newAggregation(matchOperation);
    }
    var result = mongoTemplate.aggregate(aggregation, CqlLibrary.class, LibraryListDTO.class);
    return result.getMappedResults();
  }
}

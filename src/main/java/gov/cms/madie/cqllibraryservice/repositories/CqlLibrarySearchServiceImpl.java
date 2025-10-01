package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.dto.*;
import gov.cms.madie.cqllibraryservice.services.AppConfigService;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.OwnershipType;
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
import org.springframework.data.mongodb.core.aggregation.ArrayOperators;

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

  private Criteria getAclCriteria(String userId, OwnershipType ownershipType) {
    if (ownershipType == OwnershipType.OWNED) {
      return Criteria.where("librarySet.owner").is(userId);
    } else if (ownershipType == OwnershipType.SHARED) {
      return Criteria.where("librarySet.acls")
          .elemMatch(
              Criteria.where("userId")
                  .regex("^\\Q" + userId + "\\E$", "i")
                  .and("roles")
                  .in(RoleEnum.SHARED_WITH));
    }
    return new Criteria();
  }

  // Build lock-related aggregation stages only if LOCKING feature is enabled
  private List<AggregationOperation> buildLockLookupStages() {
    if (!appConfigService.isFlagEnabled(MadieFeatureFlag.LOCKING)) {
      return Collections.emptyList();
    }
    List<AggregationOperation> stages = new ArrayList<>();
    LookupOperation lockLookupOperation =
        LookupOperation.newLookup()
            .from("cqlLibraryLock")
            .localField("_id")
            .foreignField("_id")
            .as("cqlLibraryLock");
    AddFieldsOperation flattenLockOperation =
        addFields()
            .addFieldWithValue(
                "cqlLibraryLock",
                ArrayOperators.ArrayElemAt.arrayOf("$cqlLibraryLock").elementAt(0))
            .build();
    stages.add(lockLookupOperation);
    stages.add(flattenLockOperation);
    return stages;
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
      OwnershipType ownershipType) {
    LookupOperation lookupOperation = getLookupOperation();
    UnwindOperation unwindOperation = unwind("librarySet");

    Criteria criteria = Criteria.where("active").is(true);

    if (librarySearchCriteria != null
        && StringUtils.isNotBlank(librarySearchCriteria.getSearchField())) {
      appendAdditionalSearchCriteria(criteria, librarySearchCriteria);
    }

    Criteria librarySetCriteria = new Criteria();
    if (StringUtils.isNotBlank(userId)) {
      librarySetCriteria = getAclCriteria(userId, ownershipType);
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

      MatchOperation matchLibrarySetIds =
          match(Criteria.where("librarySetId").in(matchedLibrarySetIds));

      SortOperation sortByVersionAndDraft = sort(Sort.by(Sort.Direction.DESC, "draft", "version"));
      GroupOperation groupByLibrarySet = group("librarySetId").first("$$ROOT").as("selectedDoc");

      ReplaceRootOperation replaceRoot = replaceRoot("selectedDoc");

      List<AggregationOperation> lockStages = buildLockLookupStages();

      List<AggregationOperation> ops = new ArrayList<>();
      ops.add(lookupOperation);
      ops.add(unwindOperation);
      ops.add(matchLibrarySetIds);
      ops.add(sortByVersionAndDraft);
      ops.add(groupByLibrarySet);
      ops.add(replaceRoot);
      ops.addAll(lockStages);
      ops.add(facets);

      Aggregation pipeline = newAggregation(ops);
      List<FacetDTO> results =
          mongoTemplate.aggregate(pipeline, CqlLibrary.class, FacetDTO.class).getMappedResults();
      for (LibraryListDTO dto : results.get(0).getQueryResults()) {
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
        if (dto.getCqlLibraryLock() != null
            && userId != null
            && userId.equalsIgnoreCase(dto.getCqlLibraryLock().getLockedBy())) {
          dto.setCqlLibraryLock(null); // Don't show lock info to the user who locked it
        }
      }
      long totalSize = matchInfoMap.size();
      return new PageImpl<>(results.get(0).getQueryResults(), pageable, totalSize);

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

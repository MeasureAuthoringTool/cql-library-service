package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.dto.*;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.common.ReviewStatus;
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

  // Build lock-related aggregation stages
  private List<AggregationOperation> buildLockLookupStages() {
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

  /** Matches one review status on the joined review document and projects its display label. */
  private ConditionalOperators.Switch.CaseOperator reviewStatusCase(
      ReviewStatus status, String label) {
    return ConditionalOperators.Switch.CaseOperator.when(
            ComparisonOperators.Eq.valueOf(
                    ArrayOperators.ArrayElemAt.arrayOf("$review.status").elementAt(0))
                .equalToValue(status.name()))
        .then(label);
  }

  private List<AggregationOperation> buildReviewLookupStages() {
    List<AggregationOperation> stages = new ArrayList<>();
    AddFieldsOperation addLibraryIdStringOperation =
        addFields()
            .addFieldWithValue("libraryIdString", ConvertOperators.valueOf("_id").convertToString())
            .build();
    LookupOperation reviewLookupOperation =
        LookupOperation.newLookup()
            .from("cqlLibraryReview")
            .localField("libraryIdString")
            .foreignField("libraryId")
            .as("review");
    AddFieldsOperation reviewStatusOperation =
        addFields()
            .addFieldWithValue(
                "reviewStatus",
                ConditionalOperators.Switch.switchCases(
                        reviewStatusCase(ReviewStatus.READY_FOR_REVIEW, "Ready"),
                        reviewStatusCase(ReviewStatus.IN_PROGRESS, "In Progress"),
                        reviewStatusCase(ReviewStatus.COMPLETE, "Complete"))
                    .defaultTo(""))
            .build();
    stages.add(addLibraryIdStringOperation);
    stages.add(reviewLookupOperation);
    stages.add(reviewStatusOperation);
    return stages;
  }

  private boolean isReviewSearch(LibrarySearchCriteria librarySearchCriteria) {
    return librarySearchCriteria != null
        && librarySearchCriteria.getOptionalSearchProperties() != null
        && librarySearchCriteria.getOptionalSearchProperties().contains("review");
  }

  public CqlLibrarySearchServiceImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Page<LibraryListDTO> searchLibrariesByCriteria(
      String userId,
      Pageable pageable,
      LibrarySearchCriteria librarySearchCriteria,
      OwnershipType ownershipType) {
    LookupOperation lookupOperation = getLookupOperation();
    UnwindOperation unwindOperation = unwind("librarySet");
    List<AggregationOperation> reviewStages = buildReviewLookupStages();

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

    // Find all the libraries that matches the given Criteria and fetch unique librarySetIds
    List<AggregationOperation> matchOps = new ArrayList<>();
    matchOps.add(lookupOperation);
    matchOps.add(unwindOperation);
    matchOps.addAll(reviewStages);
    matchOps.add(matchOperation);
    matchOps.add(
        group("librarySetId").count().as("matchCount").first("_id").as("matchedLibraryId"));

    List<LibrarySetMatchCountDTO> matchedLibrarySetCounts =
        mongoTemplate
            .aggregate(newAggregation(matchOps), CqlLibrary.class, LibrarySetMatchCountDTO.class)
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
    ops.addAll(reviewStages);
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
  }

  public List<LibraryListDTO> findLibrariesByLibrarySetId(
      String librarySetId,
      boolean sortByLatestVersion,
      LibrarySearchCriteria librarySearchCriteria) {
    Criteria criteria = Criteria.where("active").is(true).and("librarySetId").is(librarySetId);

    LookupOperation lookupOperation = getLookupOperation();
    UnwindOperation unwindOperation = unwind("librarySet");

    List<AggregationOperation> operations = new ArrayList<>();
    operations.add(lookupOperation);
    operations.add(unwindOperation);

    if (librarySearchCriteria != null
        && StringUtils.isNotBlank(librarySearchCriteria.getSearchField())) {
      if (isReviewSearch(librarySearchCriteria)) {
        operations.addAll(buildReviewLookupStages());
      }
      appendAdditionalSearchCriteria(criteria, librarySearchCriteria);
    }

    operations.add(match(criteria));

    if (sortByLatestVersion) {
      operations.add(sort(Sort.by(Sort.Direction.DESC, "version")));
    }

    var result =
        mongoTemplate.aggregate(newAggregation(operations), CqlLibrary.class, LibraryListDTO.class);
    return result.getMappedResults();
  }

  @Override
  public List<LibraryListDTO> findLibrariesForAccessReport(List<String> libraryIds) {
    if (libraryIds == null || libraryIds.isEmpty()) {
      return Collections.emptyList();
    }

    MatchOperation matchByIds = match(Criteria.where("_id").in(libraryIds));
    LookupOperation lookupOperation = getLookupOperation();
    UnwindOperation unwindOperation = unwind("librarySet", true);

    Aggregation aggregation =
        newAggregation(matchByIds, lookupOperation, unwindOperation, project(LibraryListDTO.class));

    return mongoTemplate
        .aggregate(aggregation, CqlLibrary.class, LibraryListDTO.class)
        .getMappedResults();
  }
}

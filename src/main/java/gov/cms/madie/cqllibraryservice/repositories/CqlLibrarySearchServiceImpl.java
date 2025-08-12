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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static gov.cms.madie.cqllibraryservice.utils.SearchUtils.appendAdditionalSearchCriteria;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.core.aggregation.ConditionalOperators.Cond.when;

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
      if (ownershipType == OwnershipType.OWNED) {
        librarySetCriteria = Criteria.where("librarySet.owner").is(userId);
      } else if (ownershipType == OwnershipType.SHARED) {
        librarySetCriteria =
            Criteria.where("librarySet.acls")
                .elemMatch(
                    Criteria.where("userId")
                        .regex("^\\Q" + userId + "\\E$", "i")
                        .and("roles")
                        .in(RoleEnum.SHARED_WITH));
      }
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

    Aggregation pipeline;
    if (appConfigService.isFlagEnabled(MadieFeatureFlag.LIBRARY_SEARCH)) {
      // Find all the libraries that matches the given Criteria and fetch unique librarySetIds
      List<String> matchedLibrarySetIds =
          mongoTemplate
              .aggregate(
                  newAggregation(
                      lookupOperation, unwindOperation, matchOperation, group("librarySetId")),
                  CqlLibrary.class,
                  LibrarySetIdDTO.class)
              .getMappedResults()
              .stream()
              .map(LibrarySetIdDTO::getId)
              .collect(Collectors.toList());

      // Fetch all libraries associated to each LibrarySetId
      MatchOperation matchLibrarySetIds =
          match(Criteria.where("librarySetId").in(matchedLibrarySetIds));

      // Sort those libraries based on version and draft status
      SortOperation sortByVersionAndDraft = sort(Sort.by(Sort.Direction.DESC, "draft", "version"));

      // Group all libraries that has same librarySetId and get the count and also first document
      // which will be the latest library in the LibrarySet
      GroupOperation groupByLibrarySet =
          group("librarySetId").count().as("count").first("$$ROOT").as("selectedDoc");

      AddFieldsOperation addFieldsOperation =
          addFields()
              .addField("selectedDoc.hasAssociatedLibraries")
              .withValueOf(
                  when(ComparisonOperators.Gt.valueOf("count").greaterThanValue(1))
                      .then(true)
                      .otherwise(false))
              .build();

      ReplaceRootOperation replaceRootOperation = replaceRoot("selectedDoc");

      pipeline =
          newAggregation(
              lookupOperation,
              unwindOperation,
              matchLibrarySetIds,
              sortByVersionAndDraft,
              groupByLibrarySet,
              addFieldsOperation,
              replaceRootOperation,
              sort(Sort.by(Sort.Direction.DESC, "lastModifiedAt")),
              skip(pageable.getOffset()),
              facets);
    } else {
      pipeline = newAggregation(lookupOperation, unwindOperation, matchOperation, facets);
    }

    List<FacetDTO> results =
        mongoTemplate.aggregate(pipeline, CqlLibrary.class, FacetDTO.class).getMappedResults();
    if (appConfigService.isFlagEnabled(MadieFeatureFlag.LIBRARY_SEARCH)) {
      long totalSize = 0;
      if (results != null && !results.isEmpty()) {
        List<?> countList = results.get(0).getCount();
        if (countList != null && !countList.isEmpty()) {
          Object totalCount = countList.get(0);
          if (totalCount instanceof Map<?, ?>) {
            Object count = ((Map<?, ?>) totalCount).get("count");
            totalSize = ((Number) count).longValue();
          }
        }
      }
      return new PageImpl<>(results.get(0).getQueryResults(), pageable, totalSize);
    }

    return new PageImpl<>(
        results.get(0).getQueryResults(), pageable, results.get(0).getCount().size());
  }

  public List<LibraryListDTO> findLibrariesByLibrarySetId(
      String librarySetId, boolean sortByLatestVersion) {
    LookupOperation lookupOperation = getLookupOperation();
    UnwindOperation unwindOperation = unwind("librarySet");

    Criteria measureCriteria =
        Criteria.where("active").is(true).and("librarySetId").is(librarySetId);

    MatchOperation matchOperation = match(measureCriteria);
    Aggregation aggregation;
    if (sortByLatestVersion) {
      SortOperation sortOperation = sort(Sort.by(Sort.Direction.DESC, "version"));
      aggregation = newAggregation(lookupOperation, unwindOperation, matchOperation, sortOperation);
    } else {
      aggregation = newAggregation(lookupOperation, unwindOperation, matchOperation);
    }
    return mongoTemplate
        .aggregate(aggregation, CqlLibrary.class, LibraryListDTO.class)
        .getMappedResults();
  }
}

package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.dto.FacetDTO;
import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.dto.MadieFeatureFlag;
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

import java.util.List;
import java.util.Map;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;

@Repository
public class CqlLibrarySearchServiceImpl implements CqlLibrarySearchService {
  private final MongoTemplate mongoTemplate;

  private AppConfigService appConfigService;

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
      String userId, Pageable pageable, String searchCriteria, boolean filterByCurrentUser) {
    LookupOperation lookupOperation = getLookupOperation();
    Criteria libraryNameCriteria = new Criteria();
    if (StringUtils.isNotBlank(searchCriteria)) {
      libraryNameCriteria.and("cqlLibraryName").regex(searchCriteria, "i");
    }
    Criteria userCriteria = new Criteria();
    if (filterByCurrentUser && StringUtils.isNotBlank(userId)) {
      userCriteria =
          new Criteria()
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
    MatchOperation matchOperation =
        match(new Criteria().andOperator(libraryNameCriteria, userCriteria));
    FacetOperation facets =
        facet(sortByCount("id"))
            .as("count")
            .and(
                sort(pageable.getSort()),
                skip(pageable.getOffset()),
                limit(pageable.getPageSize()),
                project(LibraryListDTO.class))
            .as("queryResults");
    Aggregation pipeline = null;
    if (appConfigService.isFlagEnabled(MadieFeatureFlag.LIBRARY_SEARCH)) {
      SortOperation sortOperation =
          sort(
              Sort.by(
                  Sort.Direction.DESC, "version.major", "version.minor", "version.revisionNumber"));
      GroupOperation groupOperation = group("librarySetId").push("$$ROOT").as("docs");
      ProjectionOperation projectionOperation =
          project()
              .and(
                  ConditionalOperators.when(
                          ComparisonOperators.Gt.valueOf(
                                  ArrayOperators.Size.lengthOfArray(
                                      ArrayOperators.Filter.filter("docs")
                                          .as("item")
                                          .by(
                                              ComparisonOperators.Eq.valueOf("item.draft")
                                                  .equalToValue(true))))
                              .greaterThanValue(0))
                      .thenValueOf(
                          ArrayOperators.ArrayElemAt.arrayOf(
                                  ArrayOperators.Filter.filter("docs")
                                      .as("item")
                                      .by(
                                          ComparisonOperators.Eq.valueOf("item.draft")
                                              .equalToValue(true)))
                              .elementAt(0))
                      .otherwiseValueOf(ArrayOperators.ArrayElemAt.arrayOf("docs").elementAt(0)))
              .as("selectedDoc");

      ReplaceRootOperation replaceRootOperation = replaceRoot("selectedDoc");

      pipeline =
          newAggregation(
              lookupOperation,
              matchOperation,
              sortOperation,
              groupOperation,
              projectionOperation,
              replaceRootOperation,
              facets);
    } else {
      pipeline = newAggregation(lookupOperation, matchOperation, facets);
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
}

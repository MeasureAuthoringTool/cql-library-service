package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.dto.*;
import gov.cms.madie.cqllibraryservice.services.AppConfigService;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.library.CqlLibrary;
import org.apache.commons.collections4.CollectionUtils;
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
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNumeric;
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

  private void appendAdditionalSearchCriteria(
      Criteria criteria, LibrarySearchCriteria libraryNameCriteria) {
    // Build the orOperator for the remaining properties
    String searchField = libraryNameCriteria.getSearchField();
    List<Criteria> orConditions = new ArrayList<>();

    for (String property : libraryNameCriteria.getOptionalSearchProperties()) {
      // this needs to run whenever we have multiple, however we need to force a search even if
      // the searchField split is less than 3 if the version is the only category that is applied
      switch (property) {
        case "version":
          String[] versionParts = searchField.split("\\.");
          if (versionParts.length == 3
              && isNumeric(versionParts[0])
              && isNumeric(versionParts[1])
              && isNumeric(versionParts[2])) {
            Criteria otherCriteria = Criteria.where("version").is(Version.parse(searchField));
            orConditions.add(otherCriteria);
          }
          if (versionParts.length == 2
              && isNumeric(versionParts[0])
              && isNumeric(versionParts[1])) {
            int major = Integer.parseInt(versionParts[0]);
            int minor = Integer.parseInt(versionParts[1]);
            Criteria otherCriteria =
                Criteria.where("version.major").is(major).and("version.minor").is(minor);
            Criteria additionalCriteria =
                Criteria.where("version.minor").is(major).and("version.revisionNumber").is(minor);
            orConditions.add(otherCriteria);
            orConditions.add(additionalCriteria);
          }
          if (versionParts.length == 1) {
            if (isNumeric(versionParts[0])) {
              int anyMatch = Integer.parseInt(versionParts[0]);
              Criteria majorMatch = Criteria.where("version.major").is(anyMatch);
              Criteria minorMatch = Criteria.where("version.minor").is(anyMatch);
              Criteria patchMatch = Criteria.where("version.revisionNumber").is(anyMatch);
              orConditions.add(majorMatch);
              orConditions.add(minorMatch);
              orConditions.add(patchMatch);
            } else {
              if (libraryNameCriteria.getOptionalSearchProperties().size() == 1) {
                Criteria noVersionMatch = Criteria.where("version.major").is(versionParts[0]);
                orConditions.add(noVersionMatch);
              }
            }
          }
          //  if its a bad version that's a random string, and there are no other optional params
          // provided, we need to force this criteria search
          break;
        case "library":
          orConditions.add(
              Criteria.where("cqlLibraryName")
                  .regex(".*" + Pattern.quote(searchField) + ".*", "i"));
          break;
        case "model":
          orConditions.add(
              Criteria.where("model").regex(".*" + Pattern.quote(searchField) + ".*", "i"));
          break;
        default:
          if (!StringUtils.isBlank(property)) {
            orConditions.add(
                Criteria.where(property).regex(libraryNameCriteria.getSearchField(), "i"));
          }
      }
    }
    Criteria allOrConditions = new Criteria();
    if (!orConditions.isEmpty()) {
      allOrConditions.orOperator(orConditions);
    }
    criteria.andOperator(allOrConditions);
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

    if (librarySearchCriteria != null) {
      if (CollectionUtils.isEmpty(librarySearchCriteria.getOptionalSearchProperties())
          && StringUtils.isNotBlank(librarySearchCriteria.getSearchField())) {
        criteria.and("cqlLibraryName").regex(librarySearchCriteria.getSearchField(), "i");
      } else if (CollectionUtils.isNotEmpty(librarySearchCriteria.getOptionalSearchProperties())
          && StringUtils.isNotBlank(librarySearchCriteria.getSearchField())) {
        appendAdditionalSearchCriteria(criteria, librarySearchCriteria);
      }
    }
    Criteria librarySetCriteria = new Criteria();
    if (filterByCurrentUser && StringUtils.isNotBlank(userId)) {
      librarySetCriteria =
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

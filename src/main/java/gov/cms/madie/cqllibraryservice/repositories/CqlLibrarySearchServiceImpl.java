package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.dto.FacetDTO;
import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.library.CqlLibrary;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;

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

  public CqlLibrarySearchServiceImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
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

    Aggregation pipeline = newAggregation(lookupOperation, matchOperation, facets);

    List<FacetDTO> results =
        mongoTemplate.aggregate(pipeline, CqlLibrary.class, FacetDTO.class).getMappedResults();

    return new PageImpl<>(
        results.get(0).getQueryResults(), pageable, results.get(0).getCount().size());
  }
}

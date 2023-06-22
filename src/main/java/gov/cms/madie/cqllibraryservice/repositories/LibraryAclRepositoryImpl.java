package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.library.CqlLibrary;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;

@Repository
public class LibraryAclRepositoryImpl implements LibraryAclRepository {
  private final MongoTemplate mongoTemplate;

  public LibraryAclRepositoryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  private LookupOperation getLookupOperation() {
    return LookupOperation.newLookup()
        .from("librarySet")
        .localField("librarySetId")
        .foreignField("librarySetId")
        .as("librarySet");
  }

  @Override
  public List<CqlLibrary> findAllMyLibraries(String userId) {

    Criteria libraryCriteria = Criteria.where("createdBy").is(userId);
    Criteria librarySetCriteria =
        new Criteria()
            .orOperator(
                Criteria.where("librarySet.owner").is(userId),
                Criteria.where("librarySet.acls.userId")
                    .is(userId)
                    .and("librarySet.acls.roles")
                    .in(RoleEnum.SHARED_WITH));
    MatchOperation matchOperation =
        match(new Criteria().andOperator(libraryCriteria, librarySetCriteria));

    Aggregation libraryAggregation = newAggregation(getLookupOperation(), matchOperation);

    List<CqlLibrary> results =
        mongoTemplate
            .aggregate(libraryAggregation, CqlLibrary.class, CqlLibrary.class)
            .getMappedResults();
    return results;
  }

  @Override
  public List<CqlLibrary> findAllByCqlLibraryNameAndDraftAndVersion(
      String cqlLibraryName, boolean draft, Version version) {
    Criteria libraryCriteria = Criteria.where("cqlLibraryName").is(cqlLibraryName);
    libraryCriteria.andOperator(
        Criteria.where("draft").is(draft), Criteria.where("version").is(version));

    MatchOperation matchOperation = match(new Criteria().andOperator(libraryCriteria));

    Aggregation librarySetAggregation = newAggregation(getLookupOperation(), matchOperation);
    List<CqlLibrary> results =
        mongoTemplate
            .aggregate(librarySetAggregation, CqlLibrary.class, CqlLibrary.class)
            .getMappedResults();
    return results;
  }

  @Override
  public List<CqlLibrary> findAllByCqlLibraryNameAndDraftAndVersionAndModel(
      String cqlLibraryName, boolean draft, Version version, String model) {

    Criteria libraryCriteria = Criteria.where("cqlLibraryName").is(cqlLibraryName);
    libraryCriteria.andOperator(
        Criteria.where("draft").is(draft),
        Criteria.where("version").is(version),
        Criteria.where("model").is(model));

    MatchOperation matchOperation = match(new Criteria().andOperator(libraryCriteria));

    Aggregation librarySetAggregation = newAggregation(getLookupOperation(), matchOperation);
    List<CqlLibrary> results =
        mongoTemplate
            .aggregate(librarySetAggregation, CqlLibrary.class, CqlLibrary.class)
            .getMappedResults();
    return results;
  }
}

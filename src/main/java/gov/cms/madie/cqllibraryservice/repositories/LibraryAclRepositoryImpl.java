package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.library.CqlLibrary;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

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
  public List<LibraryListDTO> findAllLibrariesByUser(String userId) {
    Criteria librarySetCriteria =
        new Criteria()
            .orOperator(
                Criteria.where("librarySet.owner").is(userId),
                Criteria.where("librarySet.acls.userId")
                    .regex("^\\Q" + userId + "\\E$", "i")
                    .and("librarySet.acls.roles")
                    .in(RoleEnum.SHARED_WITH));
    MatchOperation matchOperation = match(librarySetCriteria);
    Aggregation libraryAggregation =
        newAggregation(getLookupOperation(), matchOperation, project(LibraryListDTO.class));

    List<LibraryListDTO> results =
        mongoTemplate
            .aggregate(libraryAggregation, CqlLibrary.class, LibraryListDTO.class)
            .getMappedResults();
    return results;
  }
}

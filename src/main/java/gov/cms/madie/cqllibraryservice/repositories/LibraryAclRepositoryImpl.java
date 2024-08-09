package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.library.CqlLibrary;

import io.micrometer.common.util.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.aggregation.UnwindOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.regex.Pattern;

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
    Criteria librarySetCriteria = new Criteria();
    // if user is provided, fetch libraries belongs/shared to user
    if (StringUtils.isNotBlank(userId)) {
      librarySetCriteria =
          new Criteria()
              .orOperator(
                  Criteria.where("librarySet.owner").is(userId),
                  Criteria.where("librarySet.acls.userId")
                      .regex("^\\Q" + userId + "\\E$", "i")
                      .and("librarySet.acls.roles")
                      .in(RoleEnum.SHARED_WITH));
    }
    MatchOperation matchOperation = match(librarySetCriteria);
    UnwindOperation unwindOperation = unwind("librarySet");
    Aggregation libraryAggregation =
        newAggregation(
            getLookupOperation(), matchOperation, project(LibraryListDTO.class), unwindOperation);

    return mongoTemplate
        .aggregate(libraryAggregation, CqlLibrary.class, LibraryListDTO.class)
        .getMappedResults();
  }

  @Override
  public List<LibraryUsage> findLibraryUsageByLibraryName(String name) {
    LookupOperation lookupOperation = getLookupOperation();
    MatchOperation matchOperation =
        match(
            new Criteria()
                .andOperator(
                    Criteria.where("includedLibraries.name").is(name),
                    Criteria.where("active").is(true)));
    ProjectionOperation projectionOperation =
        project("version")
            .and("cqlLibraryName")
            .as("name")
            .and("librarySet.owner")
            .as("owner")
            .andExclude("_id");
    UnwindOperation unwindOperation = unwind("owner");
    Aggregation aggregation =
        newAggregation(matchOperation, lookupOperation, projectionOperation, unwindOperation);
    return mongoTemplate
        .aggregate(aggregation, CqlLibrary.class, LibraryUsage.class)
        .getMappedResults();
  }

  @Override
  public List<LibraryListDTO> findLibrariesByNameAndModelOrderByNameAscAndVersionDsc(
      String name, String model) {
    LookupOperation lookupOperation = getLookupOperation();
    MatchOperation matchOperation =
        match(
            new Criteria()
                .andOperator(
                    Criteria.where("cqlLibraryName")
                        .regex(Pattern.compile(name, Pattern.CASE_INSENSITIVE)),
                    Criteria.where("active").is(true),
                    Criteria.where("draft").is(false),
                    Criteria.where("model").is(model)));

    ProjectionOperation projectionOperation = project("cqlLibraryName", "version", "librarySet");

    UnwindOperation unwindOperation = unwind("librarySet");

    Sort sort =
        Sort.by(Sort.Direction.ASC, "cqlLibraryName").and(Sort.by(Sort.Direction.DESC, "version"));
    SortOperation sortOperation = new SortOperation(sort);

    Aggregation aggregation =
        newAggregation(
            matchOperation, lookupOperation, projectionOperation, unwindOperation, sortOperation);
    return mongoTemplate
        .aggregate(aggregation, CqlLibrary.class, LibraryListDTO.class)
        .getMappedResults();
  }
}

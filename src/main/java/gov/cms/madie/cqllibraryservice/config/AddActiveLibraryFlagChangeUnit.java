package gov.cms.madie.cqllibraryservice.config;

import gov.cms.madie.models.library.CqlLibrary;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@ChangeUnit(id = "add_active_library_flag", order = "1", author = "madie_dev")
public class AddActiveLibraryFlagChangeUnit {

  @Execution
  public void addActiveLibraryFlag(MongoTemplate mongoTemplate) {
    log.info("Running changelog to add library.active flag");
    Query query = new Query();
    Update update = new Update().set("active", true);
    BulkOperations bulkOperations =
        mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, CqlLibrary.class);
    bulkOperations.updateMulti(query, update);
    bulkOperations.execute();
    log.info("Running changelog to add library.active flag is complete");
  }

  @RollbackExecution
  public void rollbackExecution() {
    log.info("Running changelog to add library.active flag ran into error");
  }
}

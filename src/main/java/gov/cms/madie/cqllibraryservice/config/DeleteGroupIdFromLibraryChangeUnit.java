package gov.cms.madie.cqllibraryservice.config;

import gov.cms.madie.models.library.CqlLibrary;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@ChangeUnit(id = "delete_group_id_from_library", order = "1", author = "madie_dev")
public class DeleteGroupIdFromLibraryChangeUnit {

  @Execution
  public void deleteGroupIdFromLibrary(MongoOperations mongoOperations) {
    Query query = new Query();
    Update update = new Update().rename("groupId", "librarySetId");

    BulkOperations bulkOperations =
        mongoOperations.bulkOps(BulkOperations.BulkMode.UNORDERED, CqlLibrary.class);
    bulkOperations.updateMulti(query, update);
    bulkOperations.execute();
  }

  @RollbackExecution
  public void rollbackExecution() {
    log.debug("Entering rollbackExecution()");
  }
}

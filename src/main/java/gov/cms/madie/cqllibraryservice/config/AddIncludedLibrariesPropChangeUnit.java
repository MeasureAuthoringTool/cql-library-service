package gov.cms.madie.cqllibraryservice.config;

import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.cqllibraryservice.utils.LibraryUtils;
import gov.cms.madie.models.common.IncludedLibrary;
import gov.cms.madie.models.library.CqlLibrary;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

@Slf4j
@ChangeUnit(id = "add_included_libraries_prop", order = "2", author = "madie_dev")
public class AddIncludedLibrariesPropChangeUnit {

  @Execution
  public void addIncludedLibrariesProp(CqlLibraryRepository repository) {
    log.info("Running changelog to update included libraries");
    List<CqlLibrary> libraries = repository.findAll();
    if (CollectionUtils.isNotEmpty(libraries)) {
      List<CqlLibrary> updatedLibraries =
          libraries.stream()
              .map(
                  library -> {
                    List<IncludedLibrary> includedLibraries =
                        LibraryUtils.getIncludedLibraries(library.getCql());
                    return library.toBuilder().includedLibraries(includedLibraries).build();
                  })
              .toList();
      repository.saveAll(updatedLibraries);
    }
    log.info("Running changelog to update included libraries is complete");
  }

  @RollbackExecution
  public void rollbackExecution(MongoTemplate mongoTemplate) {
    log.info("Rolling back included libraries update changelog");
    Query query = new Query();
    Update update = new Update().unset("includedLibraries");
    BulkOperations bulkOperations =
        mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, CqlLibrary.class);
    bulkOperations.updateMulti(query, update);
    bulkOperations.execute();
    log.info("Rollback included libraries update changelog complete");
  }
}

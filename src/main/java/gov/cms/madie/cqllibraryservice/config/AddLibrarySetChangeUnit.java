package gov.cms.madie.cqllibraryservice.config;

import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;

import java.util.List;

import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(id = "add_library_set", order = "2", author = "madie_dev")
public class AddLibrarySetChangeUnit {

  @Execution
  public void addLibrarySetValues(
      LibrarySetRepository librarySetRepository, CqlLibraryRepository libraryRepository) {

    // group by librarySetId and createdBy
    List<CqlLibrary> cqlLibraries = libraryRepository.findByCqlLibrarySetId();
    cqlLibraries.forEach(
        cqlLibrary -> {
          if (!librarySetRepository.existsByLibrarySetId(cqlLibrary.getLibrarySetId())) {
            // create LibrarySet
            LibrarySet librarySet =
                LibrarySet.builder()
                    .librarySetId(cqlLibrary.getLibrarySetId())
                    .owner(cqlLibrary.getCreatedBy())
                    .build();

            librarySetRepository.save(librarySet);
          }
        });
  }

  @RollbackExecution
  public void rollbackExecution(LibrarySetRepository librarySetRepository) {
    librarySetRepository.deleteAll();
  }
}

package gov.cms.madie.cqllibraryservice.config;

import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;

import java.util.List;

import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(id = "add_library_set", order = "1", author = "madie_dev")
public class AddLibrarySetChangeUnit {

  @Execution
  public void addLibrarySetValues(
      LibrarySetRepository librarySetRepository, CqlLibraryRepository libraryRepository) {

    updateAllLibraries(libraryRepository);

    // group by groupId and createdBy
    List<CqlLibrary> cqlLibraries = libraryRepository.findCqlLibraryGroup();
    cqlLibraries.forEach(
        cqlLibrary -> {
          if (!librarySetRepository.existsByLibrarySetId(cqlLibrary.getGroupId())) {
            // create LibrarySet
            LibrarySet librarySet =
                LibrarySet.builder()
                    .librarySetId(cqlLibrary.getGroupId())
                    .owner(cqlLibrary.getCreatedBy())
                    .build();

            librarySetRepository.save(librarySet);
          }
        });
  }

  protected void updateAllLibraries(CqlLibraryRepository libraryRepository) {
    // update all libraries librarySetId with groupId
    List<CqlLibrary> allLibraries = libraryRepository.findAll();
    allLibraries.forEach(
        cqlLibrary -> {
          CqlLibrary updatedLibrary =
              CqlLibrary.builder()
                  .id(cqlLibrary.getId())
                  .cqlLibraryName(cqlLibrary.getCqlLibraryName())
                  .model(cqlLibrary.getModel())
                  .version(cqlLibrary.getVersion())
                  .draft(cqlLibrary.isDraft())
                  .groupId(cqlLibrary.getGroupId())
                  .cqlErrors(cqlLibrary.isCqlErrors())
                  .cql(cqlLibrary.getCql())
                  .elmJson(cqlLibrary.getElmJson())
                  .elmXml(cqlLibrary.getElmXml())
                  .createdAt(cqlLibrary.getCreatedAt())
                  .createdBy(cqlLibrary.getCreatedBy())
                  .lastModifiedAt(cqlLibrary.getLastModifiedAt())
                  .lastModifiedBy(cqlLibrary.getLastModifiedBy())
                  .publisher(cqlLibrary.getPublisher())
                  .description(cqlLibrary.getDescription())
                  .experimental(cqlLibrary.isExperimental())
                  .programUseContext(cqlLibrary.getProgramUseContext())
                  .build();

          updatedLibrary.setLibrarySetId(cqlLibrary.getGroupId());
          libraryRepository.save(updatedLibrary);
        });
  }

  @RollbackExecution
  public void rollbackExecution(LibrarySetRepository librarySetRepository) {
    librarySetRepository.deleteAll();
  }
}

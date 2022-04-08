package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.models.CqlLibrary;
import gov.cms.madie.cqllibraryservice.respositories.CqlLibraryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CqlLibraryService {

  private CqlLibraryRepository repository;

  @Autowired
  public CqlLibraryService(CqlLibraryRepository repository) {
    this.repository = repository;
  }

  /**
   * Returns false if there is already a draft for any version of this CQL Library group.
   * @param cqlLibrary CQL Library to check
   * @return false if there is already a draft for any version of this CQL Library group, true otherwise.
   */
  public boolean isDraftable(CqlLibrary cqlLibrary) {
    if (cqlLibrary == null)
      return true;
    return repository.existsByGroupIdAndDraft(cqlLibrary.getGroupId(), true);
  }

}

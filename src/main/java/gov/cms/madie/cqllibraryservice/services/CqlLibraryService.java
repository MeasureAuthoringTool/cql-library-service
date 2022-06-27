package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.exceptions.DuplicateKeyException;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import io.micrometer.core.instrument.util.StringUtils;
import java.util.Objects;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class CqlLibraryService {

  private CqlLibraryRepository cqlLibraryRepository;

  public void checkDuplicateCqlLibraryName(String cqlLibraryName) {
    if (StringUtils.isNotEmpty(cqlLibraryName)
        && cqlLibraryRepository.existsByCqlLibraryName(cqlLibraryName)) {
      throw new DuplicateKeyException("cqlLibraryName", "Library name must be unique.");
    }
  }

  public boolean isCqlLibraryNameChanged(CqlLibrary cqlLibrary, CqlLibrary persistedCqlLibrary) {
    return !Objects.equals(persistedCqlLibrary.getCqlLibraryName(), cqlLibrary.getCqlLibraryName());
  }
}

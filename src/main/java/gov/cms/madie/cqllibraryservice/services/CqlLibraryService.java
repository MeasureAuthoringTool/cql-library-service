package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.exceptions.DuplicateKeyException;
import gov.cms.madie.cqllibraryservice.exceptions.GeneralConflictException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import io.micrometer.core.instrument.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

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

  public CqlLibrary getVersionedCqlLibrary(String name, String version, Optional<String> model) {
    List<CqlLibrary> libs =
        model.isPresent()
            ? cqlLibraryRepository.findAllByCqlLibraryNameAndDraftAndVersionAndModel(
                name, false, Version.parse(version), model.get())
            : cqlLibraryRepository.findAllByCqlLibraryNameAndDraftAndVersion(
                name, false, Version.parse(version));
    if (CollectionUtils.isEmpty(libs)) {
      throw new ResourceNotFoundException("Library", "name", name);
    } else if (libs.size() > 1) {
      throw new GeneralConflictException(
          "Multiple versioned libraries were found. "
              + "Please provide additional filters "
              + "to narrow down the results to a single library.");
    } else {
      return libs.get(0);
    }
  }
}

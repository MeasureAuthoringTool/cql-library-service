package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.exceptions.*;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.models.library.LibrarySet;
import gov.cms.madie.models.measure.ElmJson;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@AllArgsConstructor
public class CqlLibraryService {

  private final ElmTranslatorClient elmTranslatorClient;
  private CqlLibraryRepository cqlLibraryRepository;
  private LibrarySetService librarySetService;

  public void checkDuplicateCqlLibraryName(String cqlLibraryName) {
    if (StringUtils.isNotEmpty(cqlLibraryName)
        && cqlLibraryRepository.existsByCqlLibraryName(cqlLibraryName)) {
      throw new DuplicateKeyException("cqlLibraryName", "Library name must be unique.");
    }
  }

  public boolean isCqlLibraryNameChanged(CqlLibrary cqlLibrary, CqlLibrary persistedCqlLibrary) {
    return !Objects.equals(persistedCqlLibrary.getCqlLibraryName(), cqlLibrary.getCqlLibraryName());
  }

  public CqlLibrary getVersionedCqlLibrary(
      String name, String version, Optional<String> model, final String accessToken) {
    List<CqlLibrary> libs =
        model.isPresent()
            ? cqlLibraryRepository.findAllByCqlLibraryNameAndDraftAndVersionAndModel(
                name, false, Version.parse(version), model.get())
            : cqlLibraryRepository.findAllByCqlLibraryNameAndDraftAndVersion(
                name, false, Version.parse(version));
    if (CollectionUtils.isEmpty(libs)) {
      log.error("Could not find Library resource with name: [{}] Version: [{}]", name, version);
      throw new ResourceNotFoundException("Library", "name", name);
    } else if (libs.size() > 1) {
      log.error("Multiple versioned libraries were found for [{}] Version: [{}]", name, version);
      throw new GeneralConflictException(
          "Multiple versioned libraries were found. "
              + "Please provide additional filters "
              + "to narrow down the results to a single library.");
    } else {
      CqlLibrary cqlLibrary = libs.get(0);
      if (StringUtils.isNotBlank(accessToken)) {
        try {
          final ElmJson elmJson = elmTranslatorClient.getElmJson(cqlLibrary.getCql(), accessToken);
          if (elmTranslatorClient.hasErrors(elmJson)) {
            throw new CqlElmTranslationErrorException(cqlLibrary.getCqlLibraryName());
          }
          cqlLibrary.setElmJson(elmJson.getJson());
          cqlLibrary.setElmXml(elmJson.getXml());
        } catch (CqlElmTranslationServiceException | CqlElmTranslationErrorException e) {
          throw e;
        }
      }
      return libs.get(0);
    }
  }

  public CqlLibrary findCqlLibraryById(String id) {
    Optional<CqlLibrary> optionalLibrary = cqlLibraryRepository.findById(id);
    if (optionalLibrary.isPresent()) {
      CqlLibrary cqlLibrary = optionalLibrary.get();
      LibrarySet librarySet = librarySetService.findByLibrarySetId(cqlLibrary.getLibrarySetId());
      cqlLibrary.setLibrarySet(librarySet);
      return cqlLibrary;
    }
    log.error("CqlLibrary with library ID [{}] was not found", id);
    throw new ResourceNotFoundException("CQL Library", id);
  }

  public boolean changeOwnership(String id, String userid) {
    boolean result = false;
    Optional<CqlLibrary> persistedCqlLibrary = cqlLibraryRepository.findById(id);
    if (persistedCqlLibrary.isPresent()) {
      CqlLibrary cqlLibrary = persistedCqlLibrary.get();
      librarySetService.updateOwnership(cqlLibrary.getLibrarySetId(), userid);
      result = true;
    }
    return result;
  }

  public boolean grantAccess(String cqlLibraryId, String userid) {
    boolean result = false;
    Optional<CqlLibrary> persistedCqlLibrary = cqlLibraryRepository.findById(cqlLibraryId);
    if (persistedCqlLibrary.isPresent()) {
      CqlLibrary cqlLibrary = persistedCqlLibrary.get();
      librarySetService.updateLibrarySetAcls(
          cqlLibrary.getLibrarySetId(), userid, RoleEnum.SHARED_WITH);
      result = true;
    }
    return result;
  }

  public CqlLibrary deleteDraftLibrary(final String id, final String userId) {
    CqlLibrary cqlLibrary = findCqlLibraryById(id);
    if (!userId.equalsIgnoreCase(cqlLibrary.getLibrarySet().getOwner())) {
      throw new PermissionDeniedException("CQL Library", cqlLibrary.getId(), userId);
    }

    if (cqlLibrary.isDraft()) {
      cqlLibraryRepository.delete(cqlLibrary);
    } else {
      throw new GeneralConflictException(
          String.format(
              "Could not update resource %s with id: %s. Resource is not a Draft.",
              "CQL Library", id));
    }
    return cqlLibrary;
  }
}

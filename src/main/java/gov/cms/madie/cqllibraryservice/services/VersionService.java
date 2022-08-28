package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.exceptions.*;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryActionLogRepository;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.measure.ElmJson;
import gov.cms.madie.models.library.Version;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@RequiredArgsConstructor
@Service
public class VersionService {

  private final CqlLibraryService cqlLibraryService;
  private final ActionLogService actionLogService;
  private final CqlLibraryRepository cqlLibraryRepository;
  private final CqlLibraryActionLogRepository cqlLibraryHistoryRepository;
  private final RestTemplate hapiFhirRestTemplate;

  private final ElmTranslatorClient elmTranslatorClient;

  @Value("${madie.fhir.service.baseUrl}")
  private String madieFhirService;

  @Value("${madie.fhir.service.hapi-fhir.libraries.uri}")
  private String librariesUri;

  public CqlLibrary createVersion(String id, boolean isMajor, String username, String accessToken) {
    CqlLibrary cqlLibrary =
        cqlLibraryRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("CQL Library", id));

    validateCqlLibrary(cqlLibrary, username);

    cqlLibrary.setDraft(false);
    cqlLibrary.setLastModifiedAt(Instant.now());
    cqlLibrary.setLastModifiedBy(username);

    Version next = getNextVersion(cqlLibrary, isMajor);
    cqlLibrary.setVersion(next);

    try {
      final ElmJson elmJson = elmTranslatorClient.getElmJson(cqlLibrary.getCql(), accessToken);
      if (elmTranslatorClient.hasErrors(elmJson)) {
        throw new CqlElmTranslationErrorException(cqlLibrary.getCqlLibraryName());
      }
      cqlLibrary.setElmJson(elmJson.getJson());
      cqlLibrary.setElmXml(elmJson.getXml());
      persistHapiFhirCqlLibrary(cqlLibrary, accessToken);
    } catch (CqlElmTranslationServiceException | CqlElmTranslationErrorException e) {
      throw e;
    } catch (Exception e) {
      log.error(
          "User [{}] cannot create a version for CQL Library with id [{}]"
              + "as there was an issue calling the Hapi Fhir service",
          username,
          cqlLibrary.getId(),
          e);
      throw new PersistHapiFhirCqlLibraryException("CQL Library", cqlLibrary.getId(), username);
    }

    var savedCqlLibrary = cqlLibraryRepository.save(cqlLibrary);

    actionLogService.logAction(
        cqlLibrary.getGroupId(),
        isMajor ? ActionType.VERSIONED_MAJOR : ActionType.VERSIONED_MINOR,
        username);

    log.info(
        "User [{}] successfully versioned cql library with ID [{}]",
        username,
        savedCqlLibrary.getId());

    return savedCqlLibrary;
  }

  private void validateCqlLibrary(CqlLibrary cqlLibrary, String username) {
    if (!Objects.equals(cqlLibrary.getCreatedBy(), username)) {
      log.error(
          "User [{}] doest not have permission to create a version of CQL Library with id [{}]",
          username,
          cqlLibrary.getId());
      throw new PermissionDeniedException("CQL Library", cqlLibrary.getId(), username);
    }

    if (!cqlLibrary.isDraft()) {
      log.error(
          "User [{}] attempted to version CQL Library with id [{}] which is not in a draft state",
          username,
          cqlLibrary.getId());
      throw new BadRequestObjectException("CQL Library", cqlLibrary.getId(), username);
    }

    if (cqlLibrary.isCqlErrors()) {
      log.error(
          "User [{}] cannot create a version for CQL Library with id [{}] "
              + "as the Cql has errors in it",
          username,
          cqlLibrary.getId());

      throw new ResourceCannotBeVersionedException(
          "CQL Library", cqlLibrary.getId(), username, "the Cql has errors in it");
    }

    if (cqlLibrary.getCql().length() == 0) {
      log.error(
          "User [{}] cannot create a version for CQL Library with id [{}] "
              + "as there is no associated Cql with this library",
          username,
          cqlLibrary.getId());
      throw new ResourceCannotBeVersionedException(
          "CQL Library",
          cqlLibrary.getId(),
          username,
          "there is no associated Cql with this library");
    }
  }

  public CqlLibrary createDraft(String id, String cqlLibraryName, String cql, String username) {
    CqlLibrary cqlLibrary =
        cqlLibraryRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("CQL Library", id));

    if (!Objects.equals(cqlLibraryName, cqlLibrary.getCqlLibraryName())) {
      cqlLibraryService.checkDuplicateCqlLibraryName(cqlLibraryName);
    }

    if (!Objects.equals(cqlLibrary.getCreatedBy(), username)) {
      log.info(
          "User [{}] doest not have permission to create a draft of CQL Library with id [{}]",
          username,
          cqlLibrary.getId());
      throw new PermissionDeniedException("CQL Library", cqlLibrary.getId(), username);
    }

    if (!isDraftable(cqlLibrary)) {
      throw new ResourceNotDraftableException("CQL Library");
    }

    CqlLibrary clonedCqlLibrary = cqlLibrary.toBuilder().build(); // creates a shallow copy
    // Clear ID so that the unique GUID from MongoDB will be applied
    clonedCqlLibrary.setId(null);
    clonedCqlLibrary.setCqlLibraryName(cqlLibraryName);
    clonedCqlLibrary.setCql(cql);
    clonedCqlLibrary.setDraft(true);
    var now = Instant.now();
    clonedCqlLibrary.setCreatedAt(now);
    clonedCqlLibrary.setCreatedBy(username);
    clonedCqlLibrary.setLastModifiedAt(now);
    clonedCqlLibrary.setLastModifiedBy(username);

    var savedCqlLibrary = cqlLibraryRepository.save(clonedCqlLibrary);

    log.info(
        "User [{}] successfully created a draft cql library with ID [{}]",
        username,
        savedCqlLibrary.getId());
    return savedCqlLibrary;
  }

  public Version getNextVersion(CqlLibrary cqlLibrary, boolean isMajor) {
    // get the max major/minor version and increment it
    try {
      if (isMajor) {
        Version version =
            cqlLibraryRepository
                .findMaxVersionByGroupId(cqlLibrary.getGroupId())
                .orElse(new Version());
        return version.toBuilder().major(version.getMajor() + 1).minor(0).build();
      } else {
        Version version =
            cqlLibraryRepository
                .findMaxMinorVersionByGroupIdAndVersionMajor(
                    cqlLibrary.getGroupId(), cqlLibrary.getVersion().getMajor())
                .orElse(new Version());
        return version.toBuilder().minor(version.getMinor() + 1).build();
      }
    } catch (RuntimeException ex) {
      log.error("VersionController::updateVersion Exception while updating version number", ex);
      throw new InternalServerErrorException("Unable to update version number", ex);
    }
  }

  /**
   * Returns false if there is already a draft for any version of this CQL Library group.
   *
   * @param cqlLibrary CQL Library to check
   * @return false if there is already a draft for any version of this CQL Library group, true
   *     otherwise.
   */
  public boolean isDraftable(CqlLibrary cqlLibrary) {
    if (cqlLibrary == null) {
      return true;
    }
    return !cqlLibraryRepository.existsByGroupIdAndDraft(cqlLibrary.getGroupId(), true);
  }

  private ResponseEntity<String> persistHapiFhirCqlLibrary(
      CqlLibrary cqlLibrary, String accessToken) {
    URI uri = buildMadieFhirServiceUri();
    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

    HttpEntity<CqlLibrary> request = new HttpEntity<>(cqlLibrary, headers);

    return hapiFhirRestTemplate.exchange(uri, HttpMethod.POST, request, String.class);
  }

  private URI buildMadieFhirServiceUri() {

    return UriComponentsBuilder.fromHttpUrl(madieFhirService + librariesUri + "/create")
        .build()
        .encode()
        .toUri();
  }
}

package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;

@Slf4j
@Service
@RequiredArgsConstructor
public class LibrarySetService {
  private final LibrarySetRepository librarySetRepository;
  private final CqlLibraryRepository cqlLibraryRepository;
  private final ActionLogService actionLogService;
  private final MongoTemplate mongoTemplate;

  public void createLibrarySet(
      final String harpId, final String libraryId, final String savedLibrarySetId) {

    boolean isLibrarySetPresent = librarySetRepository.existsByLibrarySetId(savedLibrarySetId);
    if (!isLibrarySetPresent) {
      LibrarySet librarySet =
          LibrarySet.builder().owner(harpId).librarySetId(savedLibrarySetId).build();
      LibrarySet savedLibrarySet = librarySetRepository.save(librarySet);
      log.info(
          "Library set [{}] is successfully created for the library [{}]",
          savedLibrarySet.getId(),
          libraryId);
      actionLogService.logAction(savedLibrarySet.getId(), ActionType.CREATED, harpId);
    }
  }

  public LibrarySet updateLibrarySetAcls(
      String librarySetId, AclOperation aclOperation, String userName) {
    Optional<LibrarySet> optionalLibrarySet = librarySetRepository.findByLibrarySetId(librarySetId);
    if (optionalLibrarySet.isPresent()) {
      Map<String, ActionType> actionLogDetails = new HashMap<>();
      LibrarySet librarySet = optionalLibrarySet.get();
      if (AclOperation.AclAction.GRANT == aclOperation.getAction()) {
        if (CollectionUtils.isEmpty(librarySet.getAcls())) {
          // if no acl present, add it
          librarySet.setAcls(aclOperation.getAcls());

          aclOperation
              .getAcls()
              .forEach(
                  aclSpecification -> {
                    String userId = aclSpecification.getUserId();

                    aclSpecification
                        .getRoles()
                        .forEach(
                            roleEnum -> {
                              if (roleEnum == RoleEnum.SHARED_WITH) {
                                actionLogDetails.put(userId, ActionType.SHARED);
                              }
                            });
                  });
        } else {
          // update acl
          aclOperation
              .getAcls()
              .forEach(
                  acl -> {
                    // check if acl already present for the user
                    AclSpecification aclSpecification =
                        findAclSpecificationByUserId(librarySet, acl.getUserId());
                    // if acl does not present, add it
                    if (aclSpecification == null) {
                      librarySet.getAcls().add(acl);

                      acl.getRoles()
                          .forEach(
                              roleEnum -> {
                                if (roleEnum == RoleEnum.SHARED_WITH) {
                                  actionLogDetails.put(acl.getUserId(), ActionType.SHARED);
                                }
                              });
                    } else {
                      acl.getRoles()
                          .forEach(
                              roleEnum -> {
                                if (!aclSpecification.getRoles().contains(roleEnum)) {
                                  aclSpecification.getRoles().add(roleEnum);

                                  if (roleEnum == RoleEnum.SHARED_WITH) {
                                    actionLogDetails.put(acl.getUserId(), ActionType.SHARED);
                                  }
                                }
                              });
                    }
                  });
        }
      } else if (AclOperation.AclAction.REVOKE == aclOperation.getAction()) {
        aclOperation
            .getAcls()
            .forEach(
                acl -> {
                  // check if acl already present for the user
                  AclSpecification aclSpecification =
                      findAclSpecificationByUserId(librarySet, acl.getUserId());
                  if (aclSpecification != null) {
                    // remove roles from ACL
                    aclSpecification.getRoles().removeAll(acl.getRoles());
                    // after removing the roles if there is no role left, remove acl
                    if (aclSpecification.getRoles().isEmpty()) {
                      librarySet.getAcls().remove(aclSpecification);
                    }
                  }
                });
      }

      LibrarySet updatedLibrarySet = librarySetRepository.save(librarySet);
      log.info("ACL updated for Library set [{}]", updatedLibrarySet.getId());

      actionLogDetails.forEach(
          (userId, actionType) -> {
            actionLogService.logShareAccessControlAction(
                librarySetId, actionType, userName, userId);
          });
      return updatedLibrarySet;
    } else {
      String error =
          String.format(
              "Library with set id `%s` can not be shared. Library set may not exists.",
              librarySetId);
      log.error(error);
      throw new ResourceNotFoundException("LibrarySet", "id", librarySetId);
    }
  }

  public LibrarySet findByLibrarySetId(final String librarySetId) {
    return librarySetRepository.findByLibrarySetId(librarySetId).orElse(null);
  }

  public List<String> getAllOwners(final List<String> librarySetIds) {
    Set<String> uniqueOwners = new HashSet<>();
    for (String librarySetId : librarySetIds) {
      Optional<LibrarySet> optionalLibrarySet =
          librarySetRepository.findByLibrarySetId(librarySetId);
      if (optionalLibrarySet.isPresent()) {
        LibrarySet librarySet = optionalLibrarySet.get();
        uniqueOwners.add(librarySet.getOwner());
      } else {
        log.warn("LibrarySet with id [{}] not found", librarySetId);
      }
    }
    return new ArrayList<>(uniqueOwners);
  }

  private AclSpecification findAclSpecificationByUserId(LibrarySet librarySet, String userId) {
    if (CollectionUtils.isEmpty(librarySet.getAcls())) {
      return null;
    }
    return librarySet.getAcls().stream()
        .filter(existingAcl -> Objects.equals(existingAcl.getUserId(), userId))
        .findFirst()
        .orElse(null);
  }

  public LibrarySet updateOwnership(String librarySetId, String userId) {
    Optional<LibrarySet> optionalLibrarySet = librarySetRepository.findByLibrarySetId(librarySetId);
    if (optionalLibrarySet.isPresent()) {
      LibrarySet librarySet = optionalLibrarySet.get();
      librarySet.setOwner(userId);
      LibrarySet updatedLibrarySet = librarySetRepository.save(librarySet);
      log.info("Owner changed in Library set [{}]", updatedLibrarySet.getId());
      return updatedLibrarySet;
    } else {
      String error =
          String.format(
              "Library with set id `%s` can not change ownership `%s`, Library set may not exist.",
              librarySetId, userId);
      log.error(error);
      throw new ResourceNotFoundException("LibrarySet", "id", librarySetId);
    }
  }

  private LookupOperation getLookupOperation() {
    return LookupOperation.newLookup()
        .from("librarySet")
        .localField("librarySetId")
        .foreignField("librarySetId")
        .as("librarySet");
  }

  public List<LibraryListDTO> getLibrariesByLibrarySetId(String librarySetId) {
    Criteria libraryCriteria =
        Criteria.where("active").is(true).and("librarySetId").is(librarySetId);

    MatchOperation matchOperation = match(libraryCriteria);
    UnwindOperation unwindOperation = unwind("librarySet");
    Aggregation libraryAggregation =
        newAggregation(
            getLookupOperation(), matchOperation, project(LibraryListDTO.class), unwindOperation);
    return mongoTemplate
        .aggregate(libraryAggregation, CqlLibrary.class, LibraryListDTO.class)
        .getMappedResults();
  }

  public List<CqlLibrary> getRecentLibrariesByLibrarySetId(List<String> librarySetIds) {
    List<CqlLibrary> mostRecentLibraries = new ArrayList<CqlLibrary>();
    for (String librarySetId : librarySetIds) {
      List<LibraryListDTO> libraries = getLibrariesByLibrarySetId(librarySetId);
      if (libraries != null && !libraries.isEmpty()) {
        LibraryListDTO library = libraries.get(libraries.size() - 1);
        CqlLibrary recentLibrary = cqlLibraryRepository.findById(library.getId()).orElse(null);
        mostRecentLibraries.add(recentLibrary);
      }
    }
    return mostRecentLibraries;
  }
}

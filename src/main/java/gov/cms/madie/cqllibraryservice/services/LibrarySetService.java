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
      actionLogService.logAction(
          savedLibrarySet.getLibrarySetId(), ActionType.CREATED, harpId, "librarySetActionLog");
    }
  }

  public LibrarySet updateLibrarySetAcls(
      String librarySetId, AclOperation aclOperation, String performedBy) {
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
                    acl.getRoles()
                        .forEach(
                            roleEnum -> {
                              if (aclSpecification.getRoles().contains(roleEnum)) {
                                aclSpecification.getRoles().remove(roleEnum);
                                if (roleEnum == RoleEnum.SHARED_WITH) {
                                  actionLogDetails.put(acl.getUserId(), ActionType.UNSHARED);
                                }
                              }
                            });
                    // after removing the roles if there is no role left, remove acl
                    if (aclSpecification.getRoles().isEmpty()) {
                      librarySet.getAcls().remove(aclSpecification);
                    }
                  }
                });
      }
      changeLibrarySetAlcsToLowerCase(librarySet);
      LibrarySet updatedLibrarySet = librarySetRepository.save(librarySet);
      log.info("ACL updated for Library set [{}]", updatedLibrarySet.getId());
      actionLogDetails.forEach(
          (userId, actionType) -> {
            actionLogService.logShareAccessControlAction(
                librarySet.getLibrarySetId(),
                actionType,
                performedBy,
                userId,
                String.format(
                    actionType == ActionType.UNSHARED ? "Unshared with - %s" : "Shared with - %s",
                    userId));
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
    AclSpecification aclSpecification =
        librarySet.getAcls().stream()
            .filter(existingAcl -> existingAcl.getUserId().equalsIgnoreCase(userId))
            .findFirst()
            .orElse(null);
    return aclSpecification;
  }

  public LibrarySet updateOwnership(
      String librarySetId,
      String userId,
      boolean retainShareAccess,
      String conductedBy,
      boolean isAdmin) {
    Optional<LibrarySet> optionalLibrarySet = librarySetRepository.findByLibrarySetId(librarySetId);

    if (optionalLibrarySet.isEmpty()) {
      log.error(
          ("Library with set id [%s] cannot change ownership to user [%s]. Library set may not "
                  + "exist.")
              .formatted(librarySetId, userId));
      throw new ResourceNotFoundException("LibrarySet", "id", librarySetId);
    }

    LibrarySet librarySet = optionalLibrarySet.get();
    String originalOwner = librarySet.getOwner();

    librarySet.setOwner(userId);

    boolean previouslyShared = false;

    // Remove SHARED_WITH role from new owner if it exists
    if (!CollectionUtils.isEmpty(librarySet.getAcls())) {
      // Find the ACL for the user
      AclSpecification userAcl =
          librarySet.getAcls().stream()
              .filter(acl -> acl.getUserId().equals(userId) && acl.getRoles() != null)
              .findFirst()
              .orElse(null);

      if (userAcl != null) {
        // Remove SHARED_WITH role
        previouslyShared = userAcl.getRoles().remove(RoleEnum.SHARED_WITH);

        // Remove ACL entirely if no roles remain
        if (userAcl.getRoles().isEmpty()) {
          librarySet.getAcls().remove(userAcl);
        }
      }
    }

    // Retain SHARED access for original owner if requested
    if (retainShareAccess) {
      List<AclSpecification> acls =
          !CollectionUtils.isEmpty(librarySet.getAcls()) ? librarySet.getAcls() : new ArrayList<>();
      boolean hasUserAlreadyBeenSharedWith =
          acls.stream().anyMatch(acl -> originalOwner.equalsIgnoreCase(acl.getUserId()));
      if (!hasUserAlreadyBeenSharedWith) {
        acls.add(
            AclSpecification.builder()
                .userId(originalOwner.toLowerCase())
                .roles(Set.of(RoleEnum.SHARED_WITH))
                .build());
      }
      librarySet.setAcls(acls);
    }
    changeLibrarySetAlcsToLowerCase(librarySet);
    LibrarySet updatedLibrarySet = librarySetRepository.save(librarySet);

    log.info(
        "Library set [{}] ownership transferred from original owner [{}] "
            + "to new owner [{}] by user [{}]",
        updatedLibrarySet.getId(),
        originalOwner,
        userId,
        conductedBy);
    String adminSuffix = isAdmin ? " by MADiE Admin" : "";
    actionLogService.logAction(
        updatedLibrarySet.getLibrarySetId(),
        ActionType.OWNERSHIP_TRANSFER,
        conductedBy,
        "librarySetActionLog",
        String.format("Transferred from %s to %s%s", originalOwner, userId, adminSuffix));

    if (retainShareAccess) {
      actionLogService.logShareAccessControlAction(
          updatedLibrarySet.getLibrarySetId(),
          ActionType.SHARED,
          conductedBy,
          originalOwner,
          String.format("Shared with - %s%s", originalOwner, adminSuffix));

      log.info(
          "Retained SHARED role for user [{}] on library set [{}] after ownership transfer",
          originalOwner,
          updatedLibrarySet.getLibrarySetId());
    }

    if (previouslyShared) {
      actionLogService.logShareAccessControlAction(
          updatedLibrarySet.getLibrarySetId(),
          ActionType.UNSHARED,
          conductedBy,
          userId,
          String.format(
              "%s now has owner permissions instead of share permissions%s", userId, adminSuffix));

      log.info(
          "Removed SHARED role for user [{}] on library set [{}] after ownership transfer",
          userId,
          updatedLibrarySet.getLibrarySetId());
    }

    return updatedLibrarySet;
  }

  private LookupOperation getLookupOperation() {
    return LookupOperation.newLookup()
        .from("librarySet")
        .localField("librarySetId")
        .foreignField("librarySetId")
        .as("librarySet");
  }

  private List<LibraryListDTO> getLibrariesByLibrarySetId(String librarySetId) {
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
    List<CqlLibrary> mostRecentLibraries = new ArrayList<>();
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

  private void changeLibrarySetAlcsToLowerCase(LibrarySet librarySet) {
    String ownerLower = librarySet.getOwner().toLowerCase();
    librarySet.setOwner(ownerLower);
    if (CollectionUtils.isNotEmpty(librarySet.getAcls())) {
      librarySet.getAcls().stream().forEach(acl -> acl.setUserId(acl.getUserId().toLowerCase()));
    }
  }
}

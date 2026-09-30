package gov.cms.madie.cqllibraryservice.services;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.dto.UserLibrariesDTO;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

/**
 * Assembles owned and shared libraries for many users in a single pass, for the Full User Export.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserLibraryExportService {

  private final MongoTemplate mongoTemplate;
  private final UserServiceClient userServiceClient;

  /**
   * Returns, for each requested HARP id, all library versions the user owns or is shared on.
   *
   * @param harpIds users to include; when null/empty, every user that owns/shares a library is
   *     returned
   * @return map of lower-cased HARP id -> owned/shared library lists
   */
  public Map<String, UserLibrariesDTO> getLibrariesForUsers(List<String> harpIds) {
    Set<String> targets =
        CollectionUtils.isEmpty(harpIds)
            ? null
            : harpIds.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

    List<LibraryListDTO> allLibraries = findAllLibraryVersions();

    List<String> ownerIds =
        allLibraries.stream()
            .map(
                library ->
                    library.getLibrarySet() == null ? null : library.getLibrarySet().getOwner())
            .filter(StringUtils::isNotBlank)
            .map(String::toLowerCase)
            .distinct()
            .collect(Collectors.toList());
    Map<String, UserDetailsDto> ownerDetails =
        ownerIds.isEmpty() ? Map.of() : userServiceClient.getBulkUserDetails(ownerIds);

    Map<String, UserLibrariesDTO> byUser = new HashMap<>();
    for (LibraryListDTO library : allLibraries) {
      LibrarySet librarySet = library.getLibrarySet();
      if (librarySet == null) {
        continue;
      }
      String owner =
          StringUtils.isBlank(librarySet.getOwner()) ? null : librarySet.getOwner().toLowerCase();
      if (owner != null) {
        library.setOwnerDisplayName(resolveDisplayName(ownerDetails, owner));
        if (targets == null || targets.contains(owner)) {
          bucketFor(byUser, owner).getOwnedLibraries().add(library);
        }
      }
      if (CollectionUtils.isNotEmpty(librarySet.getAcls())) {
        for (AclSpecification acl : librarySet.getAcls()) {
          if (acl.getRoles() != null
              && acl.getRoles().contains(RoleEnum.SHARED_WITH)
              && StringUtils.isNotBlank(acl.getUserId())) {
            String sharedUser = acl.getUserId().toLowerCase();
            if (targets == null || targets.contains(sharedUser)) {
              bucketFor(byUser, sharedUser).getSharedLibraries().add(library);
            }
          }
        }
      }
    }
    log.info(
        "Bulk export assembled libraries for {} user(s) from {} library versions",
        byUser.size(),
        allLibraries.size());
    return byUser;
  }

  private UserLibrariesDTO bucketFor(Map<String, UserLibrariesDTO> byUser, String harpId) {
    return byUser.computeIfAbsent(harpId, key -> new UserLibrariesDTO());
  }

  private String resolveDisplayName(Map<String, UserDetailsDto> details, String harpId) {
    UserDetailsDto userDetails = details.get(harpId);
    if (userDetails != null) {
      String fullName = LibraryUserDetailsHelper.getFullName(userDetails);
      if (StringUtils.isNotBlank(fullName)) {
        return fullName;
      }
    }
    return StringUtils.isNotBlank(harpId) ? harpId : "-";
  }

  /**
   * Aggregation: keep active libraries, join the librarySet, then return all library versions
   * sorted by librarySetId (to group same libraries together), then by draft status and version
   * (DESC).
   */
  private List<LibraryListDTO> findAllLibraryVersions() {
    LookupOperation lookup = lookup("librarySet", "librarySetId", "librarySetId", "librarySet");
    Aggregation aggregation =
        newAggregation(
            match(Criteria.where("active").is(true)),
            project().andExclude("cql", "elmJson", "elmXml"),
            lookup,
            unwind("librarySet"),
            sort(
                Sort.by(
                    Sort.Order.asc("librarySetId"),
                    Sort.Order.desc("draft"),
                    Sort.Order.desc("version"))));
    return mongoTemplate
        .aggregate(aggregation, CqlLibrary.class, LibraryListDTO.class)
        .getMappedResults();
  }
}

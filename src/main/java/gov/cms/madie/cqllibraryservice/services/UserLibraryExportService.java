package gov.cms.madie.cqllibraryservice.services;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.lookup;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.replaceRoot;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.unwind;

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
   * Returns, for each requested HARP id, the latest library per family the user owns or is shared
   * on.
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

    List<LibraryListDTO> latestPerFamily = findLatestLibraryPerFamily();

    List<String> ownerIds =
        latestPerFamily.stream()
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
    for (LibraryListDTO library : latestPerFamily) {
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
        "Bulk export assembled libraries for {} user(s) from {} library families",
        byUser.size(),
        latestPerFamily.size());
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
   * One aggregation: keep active libraries, join the librarySet, then pick the latest library per
   * family (draft > version, DESC) - matching the selection the UI/search uses.
   */
  private List<LibraryListDTO> findLatestLibraryPerFamily() {
    LookupOperation lookup = lookup("librarySet", "librarySetId", "librarySetId", "librarySet");
    Aggregation aggregation =
        newAggregation(
            match(Criteria.where("active").is(true)),
            project().andExclude("cql", "elmJson", "elmXml"),
            lookup,
            unwind("librarySet"),
            sort(Sort.by(Sort.Direction.DESC, "draft", "version")),
            group("librarySetId").first("$$ROOT").as("selectedDoc"),
            replaceRoot("selectedDoc"));
    return mongoTemplate
        .aggregate(aggregation, CqlLibrary.class, LibraryListDTO.class)
        .getMappedResults();
  }
}

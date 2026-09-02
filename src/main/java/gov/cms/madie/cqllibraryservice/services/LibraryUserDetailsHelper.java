package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.models.dto.UserDetailsDto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

final class LibraryUserDetailsHelper {

  private LibraryUserDetailsHelper() {}

  static void enrichWithUserDetails(
      List<LibraryListDTO> libraries, UserServiceClient userServiceClient) {
    if (CollectionUtils.isEmpty(libraries)) {
      return;
    }

    List<String> ownerIds =
        libraries.stream()
            .map(lib -> lib.getLibrarySet() != null ? lib.getLibrarySet().getOwner() : null)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());

    List<String> reviewerIds =
        libraries.stream()
            .map(LibraryListDTO::getReviewers)
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());

    Set<String> distinctUserIds = new LinkedHashSet<>(ownerIds);
    distinctUserIds.addAll(reviewerIds);

    if (distinctUserIds.isEmpty()) {
      return;
    }

    Map<String, UserDetailsDto> userDetailsMap =
        userServiceClient.getBulkUserDetails(new ArrayList<>(distinctUserIds));
    final Map<String, UserDetailsDto> userDetailsById =
        userDetailsMap != null ? userDetailsMap : Collections.emptyMap();

    libraries.forEach(
        library -> {
          if (library.getLibrarySet() != null && library.getLibrarySet().getOwner() != null) {
            String ownerId = library.getLibrarySet().getOwner();
            UserDetailsDto userDetails = userDetailsById.get(ownerId);
            library.setOwnerDisplayName(resolveOwnerDisplayName(userDetails, ownerId));
          }

          if (library.getReviewers() != null) {
            library.setReviewers(
                library.getReviewers().stream()
                    .map(
                        reviewerId ->
                            resolveReviewerDisplayName(userDetailsById.get(reviewerId), reviewerId))
                    .collect(Collectors.toList()));
          }
        });
  }

  static String resolveOwnerDisplayName(UserDetailsDto userDetails, String ownerId) {
    if (userDetails == null) {
      return "-";
    }
    String firstName = userDetails.getFirstName();
    String lastName = userDetails.getLastName();

    String displayName = "";

    if (StringUtils.isNotBlank(firstName) && StringUtils.isNotBlank(lastName)) {
      displayName = firstName + " " + lastName;
    } else if (StringUtils.isNotBlank(firstName)) {
      displayName = firstName;
    } else if (StringUtils.isNotBlank(lastName)) {
      displayName = lastName;
    }

    return StringUtils.isNotBlank(displayName)
        ? displayName
        : StringUtils.isNotBlank(ownerId) ? ownerId : "-";
  }

  static String getFullName(UserDetailsDto userDetails) {
    String firstName = userDetails.getFirstName() != null ? userDetails.getFirstName() : "";
    String lastName = userDetails.getLastName() != null ? userDetails.getLastName() : "";
    return (firstName + " " + lastName).trim();
  }

  private static String resolveReviewerDisplayName(UserDetailsDto userDetails, String reviewerId) {
    if (StringUtils.isBlank(reviewerId)) {
      return "-";
    }
    if (userDetails == null) {
      return reviewerId;
    }
    String firstName = userDetails.getFirstName();
    String lastName = userDetails.getLastName();
    if (StringUtils.isNotBlank(firstName) && StringUtils.isNotBlank(lastName)) {
      return firstName + " " + lastName;
    }
    return reviewerId;
  }
}

package gov.cms.madie.cqllibraryservice.utils;

import gov.cms.madie.models.dto.UserDetailsDto;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class UserDisplayNameUtils {

  private UserDisplayNameUtils() {}

  public static String getFullName(UserDetailsDto userDetails) {
    if (userDetails == null) {
      return "";
    }
    String firstName = userDetails.getFirstName();
    String lastName = userDetails.getLastName();

    if (StringUtils.isNotBlank(firstName) && StringUtils.isNotBlank(lastName)) {
      return firstName + " " + lastName;
    } else if (StringUtils.isNotBlank(firstName)) {
      return firstName;
    } else if (StringUtils.isNotBlank(lastName)) {
      return lastName;
    }
    return "";
  }

  public static String toDisplayName(String harpId, Map<String, UserDetailsDto> userDetailsMap) {
    if (StringUtils.isBlank(harpId)) {
      return harpId;
    }
    String fullName = getFullName(userDetailsMap == null ? null : userDetailsMap.get(harpId));
    return StringUtils.isNotBlank(fullName) ? fullName : harpId;
  }

  public static Map<String, String> toDisplayNamesByHarpId(
      List<String> harpIds, Map<String, UserDetailsDto> userDetailsMap) {
    if (CollectionUtils.isEmpty(harpIds)) {
      return Map.of();
    }
    return harpIds.stream()
        .filter(StringUtils::isNotBlank)
        .distinct()
        .collect(
            Collectors.toMap(harpId -> harpId, harpId -> toDisplayName(harpId, userDetailsMap)));
  }
}

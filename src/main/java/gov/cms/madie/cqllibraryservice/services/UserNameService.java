package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.utils.UserDisplayNameUtils;
import gov.cms.madie.models.dto.UserDetailsDto;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class UserNameService {

  private final UserServiceClient userServiceClient;

  public String getDisplayName(String harpId) {
    if (StringUtils.isBlank(harpId)) {
      return harpId;
    }
    return UserDisplayNameUtils.toDisplayName(
        harpId, userServiceClient.getBulkUserDetails(List.of(harpId)));
  }

  public String getCommentAuthorDisplayName(String harpId) {
    if (StringUtils.isBlank(harpId)) {
      return harpId;
    }
    Map<String, UserDetailsDto> users = userServiceClient.getBulkUserDetails(List.of(harpId));
    UserDetailsDto user = users == null ? null : users.get(harpId);
    if (user == null
        || StringUtils.isBlank(user.getFirstName())
        || StringUtils.isBlank(user.getLastName())) {
      return harpId;
    }
    return user.getFirstName().trim() + " " + user.getLastName().trim();
  }
}

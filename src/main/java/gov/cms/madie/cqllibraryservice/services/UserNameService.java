package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.utils.UserDisplayNameUtils;
import java.util.List;
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
}

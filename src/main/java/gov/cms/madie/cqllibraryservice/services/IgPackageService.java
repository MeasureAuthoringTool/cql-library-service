package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.IgPackageInstallRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IgPackageService {

  /**
   * Initiates the IG package processing workflow.
   *
   * @param request the IG package installation request containing packageId and packageVersion
   * @param username the admin user initiating the installation
   */
  public void installIgPackage(IgPackageInstallRequest request, String username) {
    log.info(
        "User [{}] initiated IG package installation for packageId [{}], version [{}]",
        username,
        request.getPackageId(),
        request.getPackageVersion());
    // Package processing workflow will be implemented in a future story.
  }
}

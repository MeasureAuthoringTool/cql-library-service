package gov.cms.madie.cqllibraryservice.services;

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
   * @param packageId the identifier of the IG package to install
   * @param packageVersion the version of the IG package to install
   * @param username the admin user initiating the installation
   */
  public void installIgPackage(String packageId, String packageVersion, String username) {
    log.info(
        "User [{}] initiated IG package installation for packageId [{}], version [{}]",
        username,
        packageId,
        packageVersion);
    // Package processing workflow will be implemented in a future story.
  }
}

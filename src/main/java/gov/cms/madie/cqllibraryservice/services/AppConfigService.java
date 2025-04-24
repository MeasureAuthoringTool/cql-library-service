package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.config.AppConfigServiceConfig;
import gov.cms.madie.cqllibraryservice.dto.MadieFeatureFlag;
import gov.cms.madie.cqllibraryservice.dto.ServiceConfig;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class AppConfigService {
  private final AppConfigServiceConfig appConfigServiceConfig;
  private final RestTemplate appConfigRestTemplate;
  private Map<String, Boolean> featureFlags;

  @Autowired
  public AppConfigService(
      RestTemplate appConfigRestTemplate, AppConfigServiceConfig appConfigServiceConfig) {
    this.appConfigRestTemplate = appConfigRestTemplate;
    this.appConfigServiceConfig = appConfigServiceConfig;
  }

  @PostConstruct
  @Scheduled(cron = "0 */5 * * * *")
  public void refreshAppConfig() {
    try {
      ServiceConfig serviceConfig =
          appConfigRestTemplate.getForObject(
              appConfigServiceConfig.getServiceConfigJsonUrl(), ServiceConfig.class);
      log.info("Initializing cql-library-service with serviceConfig: {}", serviceConfig);
      featureFlags = serviceConfig.getFeatures();
    } catch (Exception ex) {
      log.error("An error occurred while initializing feature flags from serviceConfig.json!", ex);
    }
  }

  public boolean isFlagEnabled(MadieFeatureFlag flag) {
    if (featureFlags == null) {
      log.warn("Feature flags are not initialized. Returning false for flag: {}", flag);
      return false;
    }
    return featureFlags.getOrDefault(flag.toString(), false);
  }
}

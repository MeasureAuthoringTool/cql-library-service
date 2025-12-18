package gov.cms.madie.cqllibraryservice.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Getter
@Configuration
public class AppConfigServiceConfig {
  @Value("${madie.service-config.json-url}")
  private String serviceConfigJsonUrl;

  @Bean
  public RestTemplate genericRestTemplate() {
    return new RestTemplate();
  }
}

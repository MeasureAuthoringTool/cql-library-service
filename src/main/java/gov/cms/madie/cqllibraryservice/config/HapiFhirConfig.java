package gov.cms.madie.cqllibraryservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class HapiFhirConfig {
  @Bean
  public RestTemplate hapiFhirRestTemplate() {
    return new RestTemplate();
  }
}

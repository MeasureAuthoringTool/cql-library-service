package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.config.EnvironmentConfig;
import gov.cms.madie.cqllibraryservice.dto.NotificationDto;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class NotificationServiceClient {

  private EnvironmentConfig environmentConfig;
  private RestTemplate genericRestTemplate;

  public void sendNotifications(List<NotificationDto> notifications) {
    try {
      URI uri = URI.create(environmentConfig.getNotificationServiceBaseUrl() + "/notifications");
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<List<NotificationDto>> request = new HttpEntity<>(notifications, headers);
      genericRestTemplate.exchange(uri, HttpMethod.POST, request, Void.class);
      log.info("Successfully sent {} notifications to notification-service", notifications.size());
    } catch (Exception ex) {
      log.error("An error occurred while sending notifications to notification-service", ex);
      // Don't throw - notifications are best-effort and should not block versioning
    }
  }
}

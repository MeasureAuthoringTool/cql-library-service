package gov.cms.madie.cqllibraryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class SharedUser {
  private String userId;
  private Instant performedAt;
}

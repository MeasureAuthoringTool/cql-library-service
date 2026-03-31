package gov.cms.madie.cqllibraryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibraryAccessReportDTO {
  private String id;
  private String libraryName;
  private String libraryModel;
  private String owner;
  private List<SharedWithUser> sharedWith;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SharedWithUser {
    private String userId;
    private String dateShared;
  }
}

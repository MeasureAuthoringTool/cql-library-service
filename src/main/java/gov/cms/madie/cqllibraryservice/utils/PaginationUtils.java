package gov.cms.madie.cqllibraryservice.utils;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PaginationUtils {

  public static Pageable createPageable(int page, int limit, String sortInfo) {
    if (sortInfo != null && !sortInfo.trim().isEmpty()) {
      String[] sortParts = sortInfo.split(",");
      if (sortParts.length == 2) {
        String sortBy = sortParts[0];
        boolean desc = Boolean.parseBoolean(sortParts[1]);
        return PageRequest.of(
            page, limit, Sort.by(desc ? Sort.Order.desc(sortBy) : Sort.Order.asc(sortBy)));
      }
    }
    return PageRequest.of(page, limit, Sort.by(Sort.Order.desc("lastModifiedAt")));
  }
}

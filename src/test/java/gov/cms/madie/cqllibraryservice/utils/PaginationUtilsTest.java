package gov.cms.madie.cqllibraryservice.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PaginationUtilsTest {

  @Test
  void createsAscendingPageableFromSortInfo() {
    Pageable pageable = PaginationUtils.createPageable(2, 10, "cqlLibraryName,false");

    assertEquals(2, pageable.getPageNumber());
    assertEquals(10, pageable.getPageSize());
    assertEquals(
        Sort.Order.asc("cqlLibraryName"), pageable.getSort().getOrderFor("cqlLibraryName"));
  }

  @Test
  void createsDescendingPageableFromSortInfo() {
    Pageable pageable = PaginationUtils.createPageable(0, 25, "lastModifiedAt,true");

    assertEquals(
        Sort.Order.desc("lastModifiedAt"), pageable.getSort().getOrderFor("lastModifiedAt"));
  }

  @Test
  void defaultsToLastModifiedDescendingForMissingSortInfo() {
    Pageable pageable = PaginationUtils.createPageable(0, 10, null);

    assertEquals(
        Sort.Order.desc("lastModifiedAt"), pageable.getSort().getOrderFor("lastModifiedAt"));
  }

  @Test
  void defaultsToLastModifiedDescendingForMalformedSortInfo() {
    Pageable pageable = PaginationUtils.createPageable(0, 10, "name,false,unexpected");

    assertEquals(
        Sort.Order.desc("lastModifiedAt"), pageable.getSort().getOrderFor("lastModifiedAt"));
  }
}

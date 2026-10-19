package gov.cms.madie.cqllibraryservice.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gov.cms.madie.models.dto.UserDetailsDto;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UserDisplayNameUtilsTest {

  @Test
  void returnsEmptyMapWhenHarpIdsIsNullOrEmpty() {
    Map<String, UserDetailsDto> userDetailsMap =
        Map.of("harp-1", UserDetailsDto.builder().firstName("Jane").lastName("Doe").build());

    assertEquals(Map.of(), UserDisplayNameUtils.toDisplayNamesByHarpId(null, userDetailsMap));
    assertEquals(Map.of(), UserDisplayNameUtils.toDisplayNamesByHarpId(List.of(), userDetailsMap));
  }

  @Test
  void mapsHarpIdsToDisplayNamesSkippingBlanksAndDuplicates() {
    Map<String, UserDetailsDto> userDetailsMap =
        Map.of("harp-1", UserDetailsDto.builder().firstName("Jane").lastName("Doe").build());

    List<String> harpIds = Arrays.asList("harp-1", "harp-2", "harp-1", "   ", "", null);

    Map<String, String> displayNames =
        UserDisplayNameUtils.toDisplayNamesByHarpId(harpIds, userDetailsMap);

    assertEquals(Map.of("harp-1", "Jane Doe", "harp-2", "harp-2"), displayNames);
  }
}

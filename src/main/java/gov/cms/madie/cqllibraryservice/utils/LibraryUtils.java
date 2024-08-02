package gov.cms.madie.cqllibraryservice.utils;

import gov.cms.madie.models.common.IncludedLibrary;
import gov.cms.mat.cql.CqlTextParser;
import gov.cms.mat.cql.elements.IncludeProperties;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class LibraryUtils {
  public static List<IncludedLibrary> getIncludedLibraries(String cql) {
    if (StringUtils.isBlank(cql)) {
      return List.of();
    }
    CqlTextParser cqlTextParser = new CqlTextParser(cql);
    List<IncludeProperties> includeProperties = cqlTextParser.getIncludes();

    return includeProperties.stream()
        .map(
            include ->
                IncludedLibrary.builder()
                    .name(include.getName())
                    .version(include.getVersion())
                    .build())
        .toList();
  }
}

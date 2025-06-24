package gov.cms.madie.cqllibraryservice.utils;

import gov.cms.madie.cqllibraryservice.dto.LibrarySearchCriteria;
import gov.cms.madie.models.common.Version;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isNumeric;

public class SearchUtils {

    public static void appendAdditionalSearchCriteria(
            Criteria criteria, LibrarySearchCriteria libraryNameCriteria) {
        // Build the orOperator for the remaining properties
        String searchField = libraryNameCriteria.getSearchField();
        List<Criteria> orConditions = new ArrayList<>();

        for (String property : libraryNameCriteria.getOptionalSearchProperties()) {
            // this needs to run whenever we have multiple, however we need to force a search even if
            // the searchField split is less than 3 if the version is the only category that is applied
            switch (property) {
                case "version":
                    String[] versionParts = searchField.split("\\.");
                    if (versionParts.length == 3
                            && isNumeric(versionParts[0])
                            && isNumeric(versionParts[1])
                            && isNumeric(versionParts[2])) {
                        Criteria otherCriteria = Criteria.where("version").is(Version.parse(searchField));
                        orConditions.add(otherCriteria);
                    }
                    if (versionParts.length == 2
                            && isNumeric(versionParts[0])
                            && isNumeric(versionParts[1])) {
                        int major = Integer.parseInt(versionParts[0]);
                        int minor = Integer.parseInt(versionParts[1]);
                        Criteria otherCriteria =
                                Criteria.where("version.major").is(major).and("version.minor").is(minor);
                        Criteria additionalCriteria =
                                Criteria.where("version.minor").is(major).and("version.revisionNumber").is(minor);
                        orConditions.add(otherCriteria);
                        orConditions.add(additionalCriteria);
                    }
                    if (versionParts.length == 1) {
                        if (isNumeric(versionParts[0])) {
                            int anyMatch = Integer.parseInt(versionParts[0]);
                            Criteria majorMatch = Criteria.where("version.major").is(anyMatch);
                            Criteria minorMatch = Criteria.where("version.minor").is(anyMatch);
                            Criteria patchMatch = Criteria.where("version.revisionNumber").is(anyMatch);
                            orConditions.add(majorMatch);
                            orConditions.add(minorMatch);
                            orConditions.add(patchMatch);
                        } else {
                            if (libraryNameCriteria.getOptionalSearchProperties().size() == 1) {
                                Criteria noVersionMatch = Criteria.where("version.major").is(versionParts[0]);
                                orConditions.add(noVersionMatch);
                            }
                        }
                    }
                    //  if its a bad version that's a random string, and there are no other optional params
                    // provided, we need to force this criteria search
                    break;
                case "library":
                    orConditions.add(
                            Criteria.where("cqlLibraryName")
                                    .regex(".*" + Pattern.quote(searchField) + ".*", "i"));
                    break;
                case "model":
                    orConditions.add(
                            Criteria.where("model").regex(".*" + Pattern.quote(searchField) + ".*", "i"));
                    break;
                default:
                    if (!StringUtils.isBlank(property)) {
                        orConditions.add(
                                Criteria.where(property).regex(libraryNameCriteria.getSearchField(), "i"));
                    }
            }
        }
        Criteria allOrConditions = new Criteria();
        if (!orConditions.isEmpty()) {
            allOrConditions.orOperator(orConditions);
        }
        criteria.andOperator(allOrConditions);
    }
}

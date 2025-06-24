package gov.cms.madie.cqllibraryservice.utils;

import gov.cms.madie.cqllibraryservice.dto.LibrarySearchCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class SearchUtilsTest {
    @Test
    void testAppendVersionSearchCriteria_ThreePartVersion() {
        Criteria base = new Criteria();
        LibrarySearchCriteria input = new LibrarySearchCriteria("1.2.3", List.of("version"));

        SearchUtils.appendAdditionalSearchCriteria(base, input);

        String json = base.getCriteriaObject().toString();
        assertThat(json).contains("version=1.2.003");
    }

    @Test
    void testAppendVersionSearchCriteria_TwoPartVersion() {
        Criteria base = new Criteria();
        LibrarySearchCriteria input = new LibrarySearchCriteria("2.5", List.of("version"));

        SearchUtils.appendAdditionalSearchCriteria(base, input);

        String json = base.getCriteriaObject().toJson();
        assertThat(json).contains("version.major");
        assertThat(json).contains("version.minor");
        assertThat(json).contains("version.revisionNumber");
        assertThat(json).contains("2");
        assertThat(json).contains("5");
    }

    @Test
    void testAppendVersionSearchCriteria_SingleNumber() {
        Criteria base = new Criteria();
        LibrarySearchCriteria input = new LibrarySearchCriteria("4", List.of("version"));

        SearchUtils.appendAdditionalSearchCriteria(base, input);

        String json = base.getCriteriaObject().toJson();
        assertThat(json).contains("version.major");
        assertThat(json).contains("version.minor");
        assertThat(json).contains("version.revisionNumber");
    }

    @Test
    void testAppendLibrarySearchCriteria() {
        Criteria base = new Criteria();
        LibrarySearchCriteria input = new LibrarySearchCriteria("TestLib", List.of("library"));

        SearchUtils.appendAdditionalSearchCriteria(base, input);

        String json = base.getCriteriaObject().toJson();
        assertThat(json).contains("cqlLibraryName");
        assertThat(json).contains("TestLib");
    }

    @Test
    void testAppendModelSearchCriteria() {
        Criteria base = new Criteria();
        LibrarySearchCriteria input = new LibrarySearchCriteria("FHIR", List.of("model"));

        SearchUtils.appendAdditionalSearchCriteria(base, input);

        String json = base.getCriteriaObject().toJson();
        assertThat(json).contains("model");
        assertThat(json).contains("FHIR");
    }

    @Test
    void testAppendUnknownFieldCriteria() {
        Criteria base = new Criteria();
        LibrarySearchCriteria input = new LibrarySearchCriteria("someText", List.of("customField"));

        SearchUtils.appendAdditionalSearchCriteria(base, input);

        String json = base.getCriteriaObject().toJson();
        assertThat(json).contains("customField");
        assertThat(json).contains("someText");
    }
}

package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.common.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CqlLibraryVersionRepositoryImplTest {

  @Mock MongoTemplate mongoTemplate;

  @InjectMocks CqlLibraryVersionRepositoryImpl cqlLibraryVersionRepository;

  @Test
  void testFindMaxVersionByLibrarySetIdReturnsEmptyOptionalForNoResponse() {
    when(mongoTemplate.findOne(any(Query.class), any(Class.class))).thenReturn(null);
    Optional<Version> output =
        cqlLibraryVersionRepository.findMaxVersionByLibrarySetId("LIBRARY_SET_ID");
    assertThat(output.isEmpty(), is(true));
  }

  @Test
  void testFindMaxVersionByLibrarySetIdReturnsEmptyOptionalForNullVersionResponse() {
    when(mongoTemplate.findOne(any(Query.class), any(Class.class)))
        .thenReturn(CqlLibrary.builder().build());
    Optional<Version> output =
        cqlLibraryVersionRepository.findMaxVersionByLibrarySetId("LIBRARY_SET_ID");
    assertThat(output.isEmpty(), is(true));
  }

  @Test
  void testFindMaxVersionByLibrarySetIdReturnsVersion() {
    Version version = Version.parse("1.2.000");
    when(mongoTemplate.findOne(any(Query.class), any(Class.class)))
        .thenReturn(CqlLibrary.builder().version(version).build());
    Optional<Version> output =
        cqlLibraryVersionRepository.findMaxVersionByLibrarySetId("LIBRARY_SET_ID");
    assertThat(output.isEmpty(), is(false));
    assertThat(output.get(), is(equalTo(version)));
  }

  @Test
  void testFindMaxMinorVersionByLibrarySetIdAndVersionMajorReturnsEmptyOptionalForNoResponse() {
    when(mongoTemplate.findOne(any(Query.class), any(Class.class))).thenReturn(null);
    Optional<Version> output =
        cqlLibraryVersionRepository.findMaxMinorVersionByLibrarySetIdAndVersionMajor(
            "LIBRARY_SET_ID", 1);
    assertThat(output.isEmpty(), is(true));
  }

  @Test
  void
      testFindMaxMinorVersionByLibrarySetIdAndVersionMajorReturnsEmptyOptionalForNullVersionResponse() {
    when(mongoTemplate.findOne(any(Query.class), any(Class.class)))
        .thenReturn(CqlLibrary.builder().build());
    Optional<Version> output =
        cqlLibraryVersionRepository.findMaxMinorVersionByLibrarySetIdAndVersionMajor(
            "LIBRARY_SET_ID", 1);
    assertThat(output.isEmpty(), is(true));
  }

  @Test
  void testFindMaxMinorVersionByLibrarySetIdAndVersionMajorReturnsVersion() {
    Version version = Version.parse("1.2.000");
    when(mongoTemplate.findOne(any(Query.class), any(Class.class)))
        .thenReturn(CqlLibrary.builder().version(version).build());
    Optional<Version> output =
        cqlLibraryVersionRepository.findMaxMinorVersionByLibrarySetIdAndVersionMajor(
            "LIBRARY_SET_ID", 1);
    assertThat(output.isEmpty(), is(false));
    assertThat(output.get(), is(equalTo(version)));
  }
}

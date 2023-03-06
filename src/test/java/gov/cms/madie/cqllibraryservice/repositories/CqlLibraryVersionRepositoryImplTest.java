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
  void testFindMaxVersionByGroupIdReturnsEmptyOptionalForNoResponse() {
    when(mongoTemplate.findOne(any(Query.class), any(Class.class))).thenReturn(null);
    Optional<Version> output = cqlLibraryVersionRepository.findMaxVersionByGroupId("GROUP_ID");
    assertThat(output.isEmpty(), is(true));
  }

  @Test
  void testFindMaxVersionByGroupIdReturnsEmptyOptionalForNullVersionResponse() {
    when(mongoTemplate.findOne(any(Query.class), any(Class.class)))
        .thenReturn(CqlLibrary.builder().build());
    Optional<Version> output = cqlLibraryVersionRepository.findMaxVersionByGroupId("GROUP_ID");
    assertThat(output.isEmpty(), is(true));
  }

  @Test
  void testFindMaxVersionByGroupIdReturnsVersion() {
    Version version = Version.parse("1.2.000");
    when(mongoTemplate.findOne(any(Query.class), any(Class.class)))
        .thenReturn(CqlLibrary.builder().version(version).build());
    Optional<Version> output = cqlLibraryVersionRepository.findMaxVersionByGroupId("GROUP_ID");
    assertThat(output.isEmpty(), is(false));
    assertThat(output.get(), is(equalTo(version)));
  }

  @Test
  void testFindMaxMinorVersionByGroupIdAndVersionMajorReturnsEmptyOptionalForNoResponse() {
    when(mongoTemplate.findOne(any(Query.class), any(Class.class))).thenReturn(null);
    Optional<Version> output =
        cqlLibraryVersionRepository.findMaxMinorVersionByGroupIdAndVersionMajor("GROUP_ID", 1);
    assertThat(output.isEmpty(), is(true));
  }

  @Test
  void testFindMaxMinorVersionByGroupIdAndVersionMajorReturnsEmptyOptionalForNullVersionResponse() {
    when(mongoTemplate.findOne(any(Query.class), any(Class.class)))
        .thenReturn(CqlLibrary.builder().build());
    Optional<Version> output =
        cqlLibraryVersionRepository.findMaxMinorVersionByGroupIdAndVersionMajor("GROUP_ID", 1);
    assertThat(output.isEmpty(), is(true));
  }

  @Test
  void testFindMaxMinorVersionByGroupIdAndVersionMajorReturnsVersion() {
    Version version = Version.parse("1.2.000");
    when(mongoTemplate.findOne(any(Query.class), any(Class.class)))
        .thenReturn(CqlLibrary.builder().version(version).build());
    Optional<Version> output =
        cqlLibraryVersionRepository.findMaxMinorVersionByGroupIdAndVersionMajor("GROUP_ID", 1);
    assertThat(output.isEmpty(), is(false));
    assertThat(output.get(), is(equalTo(version)));
  }
}

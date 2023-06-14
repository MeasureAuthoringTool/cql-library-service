package gov.cms.madie.cqllibraryservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("test")
@TestPropertySource(properties = {"mongock.enabled=false"})
@SpringBootTest
class CqlLibraryServiceApplicationTests {

  @Test
  void contextLoads() {}
}

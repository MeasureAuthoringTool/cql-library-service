package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CqlLibraryServiceAclTest {
  @Mock private CqlLibraryRepository cqlLibraryRepository;
  @Mock private LibrarySetService librarySetService;
  @InjectMocks private CqlLibraryService cqlLibraryService;

  @Test
  public void testGrantAccess() {
    CqlLibrary cqlLibrary = CqlLibrary.builder().id("123").librarySetId("1-2-3").build();
    Optional<CqlLibrary> persistedCqlLibrary = Optional.of(cqlLibrary);
    when(cqlLibraryRepository.findById(anyString())).thenReturn(persistedCqlLibrary);
    when(librarySetService.updateLibrarySetAcls(anyString(), any(AclSpecification.class)))
        .thenReturn(new LibrarySet());

    boolean result = cqlLibraryService.grantAccess(cqlLibrary.getId(), "testuser");
    assertTrue(result);
  }

  @Test
  public void testGrantAccessNoCqlLibrary() {
    Optional<CqlLibrary> persistedCqlLibrary = Optional.empty();
    when(cqlLibraryRepository.findById(eq("123"))).thenReturn(persistedCqlLibrary);
    boolean result = cqlLibraryService.grantAccess("123", "akinsgre");

    assertFalse(result);
  }
}

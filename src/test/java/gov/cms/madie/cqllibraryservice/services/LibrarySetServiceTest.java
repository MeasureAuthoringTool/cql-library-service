package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.library.LibrarySet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LibrarySetServiceTest {
  @Mock private LibrarySetRepository librarySetRepository;
  @InjectMocks private LibrarySetService librarySetService;

  LibrarySet librarySet;

  @BeforeEach
  public void setUp() {
    librarySet = LibrarySet.builder().librarySetId("set-1").owner("user-1").id("1").build();
  }

  @Test
  void testFindByLibrarySetId() {
    when(librarySetRepository.findByLibrarySetId(anyString())).thenReturn(Optional.of(librarySet));
    LibrarySet set = librarySetService.findByLibrarySetId("1");
    Assertions.assertEquals(set.getId(), librarySet.getId());
    Assertions.assertEquals(set.getLibrarySetId(), librarySet.getLibrarySetId());
  }
}

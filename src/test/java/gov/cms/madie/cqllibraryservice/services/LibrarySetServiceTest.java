package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.library.LibrarySet;
import org.junit.jupiter.api.Assertions;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.library.LibrarySet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibrarySetServiceTest {

  @InjectMocks private LibrarySetService librarySetService;
  @Mock LibrarySetRepository librarySetRepository;
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
    librarySet = LibrarySet.builder().librarySetId("id-2").owner("user-1").build();
  }

  @Test
  public void testUpdateOwnership() {
    LibrarySet updatedLibrarySet = librarySet;
    updatedLibrarySet.setOwner("testUser");
    when(librarySetRepository.findByLibrarySetId(anyString())).thenReturn(Optional.of(librarySet));
    when(librarySetRepository.save(any(LibrarySet.class))).thenReturn(updatedLibrarySet);

    LibrarySet result = librarySetService.updateOwnership("1", "testUser");
    assertThat(result.getId(), is(equalTo(updatedLibrarySet.getId())));
    assertThat(result.getOwner(), is(equalTo(updatedLibrarySet.getOwner())));
  }

  @Test
  public void testUpdateOwnershipWhenMeasureSetNotFound() {
    when(librarySetRepository.findByLibrarySetId(anyString())).thenReturn(Optional.empty());

    Exception ex =
        assertThrows(
            ResourceNotFoundException.class,
            () -> librarySetService.updateOwnership("1", "testUser"));
    verify(librarySetRepository, times(1)).findByLibrarySetId(anyString());
    verify(librarySetRepository, times(0)).save(any(LibrarySet.class));
  }
}

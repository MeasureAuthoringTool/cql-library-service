package gov.cms.madie.cqllibraryservice.services;

import static org.mockito.ArgumentMatchers.anyString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.library.LibrarySet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class LibrarySetServiceTest {
    @Mock
    private LibrarySetRepository librarySetRepository;
    @Mock
    private ActionLogService actionLogService;
    @InjectMocks
    private LibrarySetService librarySetService;
    @Test
    void testCreateLibrarySet() {
        LibrarySet librarySet = LibrarySet.builder().owner("fakeId").librarySetId("librarySetId").id("test").build();
        when(librarySetRepository.existsByLibrarySetId("librarySetId")).thenReturn(false);

        when(librarySetRepository.save(any(LibrarySet.class))).thenReturn(librarySet);
        when(actionLogService.logAction(anyString(), any(), anyString())).thenReturn(true);
        librarySetService.createLibrarySet(
                "fakeId", "libraryId", "librarySetId"
        );
        verify(librarySetRepository, times(1)).existsByLibrarySetId("librarySetId");
        verify(librarySetRepository, times(1)).save(librarySet);

    }

}

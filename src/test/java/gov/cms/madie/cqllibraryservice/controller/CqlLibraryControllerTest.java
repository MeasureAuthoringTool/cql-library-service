package gov.cms.madie.cqllibraryservice.controller;

import gov.cms.madie.cqllibraryservice.models.CqlLibrary;
import gov.cms.madie.cqllibraryservice.respositories.CqlLibraryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.List;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CqlLibraryControllerTest {

    @Mock
    CqlLibraryRepository cqlLibraryRepository;

    @InjectMocks
    CqlLibraryController cqlLibraryController;

    private CqlLibrary cqlLibrary;

    @BeforeEach
    public void setUp() {
        cqlLibrary = new CqlLibrary();
        cqlLibrary.setId("testCqlLibraryId");
        cqlLibrary.setCqlLibraryName("testCqlLibraryName");
    }

    @Test
    void getCqlLibrariesWithoutCurrentUserFilter() {
        List<CqlLibrary> cqlLibraries = List.of(cqlLibrary);
        when(cqlLibraryRepository.findAll()).thenReturn(cqlLibraries);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("test.user");

        ResponseEntity<List<CqlLibrary>> response = cqlLibraryController.getCqlLibraries(principal, false);
        verify(cqlLibraryRepository, times(1)).findAll();
        verifyNoMoreInteractions(cqlLibraryRepository);
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get(0));
        assertEquals("testCqlLibraryId", response.getBody().get(0).getId());
    }

    @Test
    void getCqlLibrariesWithCurrentUserFilter() {
        List<CqlLibrary> cqlLibraries = List.of(cqlLibrary);
        when(cqlLibraryRepository.findAllByCreatedBy(anyString())).thenReturn(cqlLibraries);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("test.user");

        ResponseEntity<List<CqlLibrary>> response = cqlLibraryController.getCqlLibraries(principal, true);
        verify(cqlLibraryRepository, times(1)).findAllByCreatedBy(eq("test.user"));
        verifyNoMoreInteractions(cqlLibraryRepository);
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get(0));
        assertEquals("testCqlLibraryId", response.getBody().get(0).getId());
    }

    @Test
    void testSaveCqlLibrary() {
        ArgumentCaptor<CqlLibrary> saveCqlLibraryArgCaptor = ArgumentCaptor.forClass(CqlLibrary.class);
        doReturn(cqlLibrary).when(cqlLibraryRepository).save(ArgumentMatchers.any());

        CqlLibrary cqlLibrary = new CqlLibrary();
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("test.user");

        ResponseEntity<CqlLibrary> response = cqlLibraryController.createCqlLibrary(cqlLibrary, principal);
        assertNotNull(response.getBody());
        assertEquals("testCqlLibraryId", response.getBody().getId());

        verify(cqlLibraryRepository, times(1)).save(saveCqlLibraryArgCaptor.capture());
        CqlLibrary savedCqlLibrary = saveCqlLibraryArgCaptor.getValue();
        assertThat(savedCqlLibrary.getCreatedBy(), is(equalTo("test.user")));
        assertThat(savedCqlLibrary.getLastModifiedBy(), is(equalTo("test.user")));
        assertThat(savedCqlLibrary.getCreatedAt(), is(notNullValue()));
        assertThat(savedCqlLibrary.getLastModifiedAt(), is(notNullValue()));
    }
}
package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CqlLibraryServiceAclTest {
  @Mock private CqlLibraryRepository cqlLibraryRepository;
  @Mock private LibrarySetRepository librarySetRepository;
  @InjectMocks private LibrarySetService librarySetService;
  @InjectMocks private CqlLibraryService cqlLibraryService;
  LibrarySet librarySet;
  AclSpecification spec;

  @BeforeEach
  public void setUp() {
    librarySet = LibrarySet.builder().librarySetId("1-2-3").owner("user-1").id("1").build();

    spec = new AclSpecification();
    spec.setUserId("testUser");
    spec.setRoles(List.of(RoleEnum.SHARED_WITH));
  }

  @Test
  public void testGrantAccessNoCqlLibrary() {
    Optional<CqlLibrary> persistedCqlLibrary = Optional.empty();
    when(cqlLibraryRepository.findById(eq("123"))).thenReturn(persistedCqlLibrary);
    boolean result = cqlLibraryService.grantAccess("123", "testUser");

    assertFalse(result);
  }

  @Test
  public void testGrantAccessWhenAclNotExists() {
    when(librarySetRepository.findByLibrarySetId(anyString())).thenReturn(Optional.of(librarySet));
    LibrarySet updatedLibrarySet =
        LibrarySet.builder().librarySetId("1-2-3").owner("owner").acls(List.of(spec)).build();
    when(librarySetRepository.save(any(LibrarySet.class))).thenReturn(updatedLibrarySet);

    LibrarySet savedLibrarySet =
        librarySetService.updateLibrarySetAcls("1-2-3", "testUser", RoleEnum.SHARED_WITH);
    assertThat(savedLibrarySet.getId(), is(equalTo(updatedLibrarySet.getId())));
    assertThat(savedLibrarySet.getOwner(), is(equalTo(updatedLibrarySet.getOwner())));
    assertThat(savedLibrarySet.getAcls().get(0).getUserId(), is(equalTo("testUser")));
    assertThat(
        savedLibrarySet.getAcls().get(0).getRoles(), is(equalTo(List.of(RoleEnum.SHARED_WITH))));
  }

  @Test
  public void testGrantAccessWhenAclExists() {
    librarySet.setAcls(List.of(spec));
    when(librarySetRepository.findByLibrarySetId(anyString())).thenReturn(Optional.of(librarySet));
    LibrarySet updatedLibrarySet =
        LibrarySet.builder().librarySetId("1-2-3").owner("owner").acls(List.of(spec)).build();
    when(librarySetRepository.save(any(LibrarySet.class))).thenReturn(updatedLibrarySet);

    LibrarySet savedLibrarySet =
        librarySetService.updateLibrarySetAcls("1-2-3", "testUser", RoleEnum.SHARED_WITH);
    assertThat(savedLibrarySet.getId(), is(equalTo(updatedLibrarySet.getId())));
    assertThat(savedLibrarySet.getOwner(), is(equalTo(updatedLibrarySet.getOwner())));
    assertThat(savedLibrarySet.getAcls().get(0).getUserId(), is(equalTo("testUser")));
    assertThat(
        savedLibrarySet.getAcls().get(0).getRoles(), is(equalTo(List.of(RoleEnum.SHARED_WITH))));
  }
}

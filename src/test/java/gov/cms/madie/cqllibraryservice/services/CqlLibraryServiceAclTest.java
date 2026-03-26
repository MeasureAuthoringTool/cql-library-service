package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CqlLibraryServiceAclTest {
  @Mock private CqlLibraryRepository cqlLibraryRepository;
  @Mock private LibrarySetService librarySetService;
  @Mock private UserServiceClient userServiceClient;
  @InjectMocks private CqlLibraryService cqlLibraryService;

  @Test
  public void testUpdateAccessControlListWithNoLibrary() {
    CqlLibrary library = CqlLibrary.builder().id("123").librarySetId("1-2-3").build();
    AclSpecification aclSpecification = new AclSpecification();
    aclSpecification.setUserId("test");
    aclSpecification.setRoles(Set.of(RoleEnum.SHARED_WITH));

    AclOperation aclOperation =
        AclOperation.builder()
            .acls(List.of(aclSpecification))
            .action(AclOperation.AclAction.GRANT)
            .build();
    when(cqlLibraryRepository.findById(anyString())).thenReturn(Optional.empty());

    Exception ex =
        assertThrows(
            ResourceNotFoundException.class,
            () ->
                cqlLibraryService.updateAccessControlList(
                    library.getId(), aclOperation, "admin", true, "token"));
    assertEquals(ex.getMessage(), "Library does not exist: " + library.getId());
  }

  @Test
  public void testUpdateAccessControlList() {
    CqlLibrary library = CqlLibrary.builder().id("123").librarySetId("1-2-3").build();
    AclSpecification aclSpecification = new AclSpecification();
    aclSpecification.setUserId("test");
    aclSpecification.setRoles(Set.of(RoleEnum.SHARED_WITH));
    LibrarySet librarySet =
        LibrarySet.builder()
            .librarySetId(library.getLibrarySetId())
            .acls(List.of(aclSpecification))
            .build();
    AclOperation aclOperation =
        AclOperation.builder()
            .acls(List.of(aclSpecification))
            .action(AclOperation.AclAction.GRANT)
            .build();
    Optional<CqlLibrary> persistedLibrary = Optional.of(library);
    when(cqlLibraryRepository.findById(anyString())).thenReturn(persistedLibrary);
    when(userServiceClient.getUserDetails(anyString(), anyString()))
        .thenReturn(UserDetailsDto.builder().active(true).build());
    when(librarySetService.updateLibrarySetAcls(any(), any(), any(), any(Boolean.class)))
        .thenReturn(librarySet);

    List<AclSpecification> aclSpecifications =
        cqlLibraryService.updateAccessControlList(
            library.getId(), aclOperation, "nonAdmin", false, "token");
    assertThat(aclSpecifications.size(), is(equalTo(1)));
    assertThat(aclSpecifications.get(0).getUserId(), is(aclSpecification.getUserId()));
    assertThat(aclSpecifications.get(0).getRoles(), is(aclSpecification.getRoles()));
  }
}

package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.library.LibrarySet;
import org.junit.jupiter.api.Assertions;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LibrarySetServiceTest {

  @InjectMocks private LibrarySetService librarySetService;
  @Mock LibrarySetRepository librarySetRepository;
  LibrarySet librarySet;

  @BeforeEach
  public void setUp() {
    AclSpecification aclSpec = new AclSpecification();
    aclSpec.setUserId("john");
    aclSpec.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });

    librarySet =
        LibrarySet.builder()
            .librarySetId("1")
            .owner("user-1")
            .acls(
                new ArrayList<>() {
                  {
                    add(aclSpec);
                  }
                })
            .build();
  }

  @Test
  public void testGrantOperationNoLibrarySetFound() {
    String librarySetId = "1";
    AclSpecification aclSpec = new AclSpecification();
    aclSpec.setUserId("john");
    aclSpec.setRoles(Set.of(RoleEnum.SHARED_WITH));
    AclOperation aclOperation =
        AclOperation.builder().acls(List.of(aclSpec)).action(AclOperation.AclAction.GRANT).build();
    when(librarySetRepository.findByLibrarySetId(anyString())).thenReturn(Optional.empty());

    Exception ex =
        assertThrows(
            ResourceNotFoundException.class,
            () -> librarySetService.updateLibrarySetAcls(librarySetId, aclOperation, "username"));
    assertEquals(ex.getMessage(), "Could not find resource LibrarySet with id: " + librarySetId);
  }

  @Test
  public void testGrantOperationAsFirstNewAcl() {
    AclSpecification aclSpec = new AclSpecification();
    aclSpec.setUserId("john_1");
    aclSpec.setRoles(Set.of(RoleEnum.SHARED_WITH));
    AclOperation aclOperation =
        AclOperation.builder().acls(List.of(aclSpec)).action(AclOperation.AclAction.GRANT).build();
    LibrarySet updatedLibrarySet =
        LibrarySet.builder().librarySetId("1").owner("john_1").acls(List.of(aclSpec)).build();
    when(librarySetRepository.findByLibrarySetId(anyString())).thenReturn(Optional.of(librarySet));
    when(librarySetRepository.save(any(LibrarySet.class))).thenReturn(updatedLibrarySet);

    LibrarySet librarySet = librarySetService.updateLibrarySetAcls("1", aclOperation, "username");
    assertThat(librarySet.getId(), is(equalTo(updatedLibrarySet.getId())));
    assertThat(librarySet.getOwner(), is(equalTo(updatedLibrarySet.getOwner())));
    assertThat(librarySet.getAcls().size(), is(equalTo(1)));
  }

  @Test
  public void testGrantOperationAsFirstNewAclWithNoAclsInLibrarySet() {
    LibrarySet librarySetWithNoAcls =
        LibrarySet.builder().librarySetId("1").owner("user-1").build();
    AclSpecification aclSpec = new AclSpecification();
    aclSpec.setUserId("john_1");
    aclSpec.setRoles(Set.of(RoleEnum.SHARED_WITH));
    AclOperation aclOperation =
        AclOperation.builder().acls(List.of(aclSpec)).action(AclOperation.AclAction.GRANT).build();
    LibrarySet updatedLibrarySet =
        LibrarySet.builder().librarySetId("1").owner("john_1").acls(List.of(aclSpec)).build();
    when(librarySetRepository.findByLibrarySetId(anyString()))
        .thenReturn(Optional.of(librarySetWithNoAcls));
    when(librarySetRepository.save(any(LibrarySet.class))).thenReturn(updatedLibrarySet);

    LibrarySet librarySet = librarySetService.updateLibrarySetAcls("1", aclOperation, "username");
    assertThat(librarySet.getId(), is(equalTo(updatedLibrarySet.getId())));
    assertThat(librarySet.getOwner(), is(equalTo(updatedLibrarySet.getOwner())));
    assertThat(librarySet.getAcls().size(), is(equalTo(1)));
  }

  @Test
  public void testGrantOperationAsSecondNewAcl() {
    AclSpecification aclSpec1 = new AclSpecification();
    aclSpec1.setUserId("john");
    aclSpec1.setRoles(Set.of(RoleEnum.SHARED_WITH));
    AclSpecification aclSpec2 = new AclSpecification();
    aclSpec2.setUserId("jane");
    aclSpec2.setRoles(Set.of(RoleEnum.SHARED_WITH));
    AclOperation aclOperation =
        AclOperation.builder().acls(List.of(aclSpec2)).action(AclOperation.AclAction.GRANT).build();
    LibrarySet updatedLibrarySet =
        LibrarySet.builder()
            .librarySetId("1")
            .owner("john")
            .acls(List.of(aclSpec1, aclSpec2))
            .build();
    when(librarySetRepository.findByLibrarySetId(anyString())).thenReturn(Optional.of(librarySet));
    when(librarySetRepository.save(any(LibrarySet.class))).thenReturn(updatedLibrarySet);

    LibrarySet librarySet = librarySetService.updateLibrarySetAcls("1", aclOperation, "username");
    assertThat(librarySet.getId(), is(equalTo(updatedLibrarySet.getId())));
    assertThat(librarySet.getOwner(), is(equalTo(updatedLibrarySet.getOwner())));
    assertThat(librarySet.getAcls().size(), is(equalTo(2)));
  }

  @Test
  public void testGrantOperationUpdateAcl() {
    AclSpecification aclSpec1 = new AclSpecification();
    aclSpec1.setUserId("john");
    aclSpec1.setRoles(Set.of(RoleEnum.SHARED_WITH));
    AclSpecification aclSpec2 = new AclSpecification();
    aclSpec2.setUserId("john");
    aclSpec2.setRoles(Set.of(RoleEnum.SHARED_WITH));
    AclOperation aclOperation =
        AclOperation.builder().acls(List.of(aclSpec2)).action(AclOperation.AclAction.GRANT).build();
    LibrarySet updatedLibrarySet =
        LibrarySet.builder().librarySetId("1").owner("john").acls(List.of(aclSpec1)).build();
    when(librarySetRepository.findByLibrarySetId(anyString())).thenReturn(Optional.of(librarySet));
    when(librarySetRepository.save(any(LibrarySet.class))).thenReturn(updatedLibrarySet);

    LibrarySet librarySet = librarySetService.updateLibrarySetAcls("1", aclOperation, "username");
    assertThat(librarySet.getId(), is(equalTo(updatedLibrarySet.getId())));
    assertThat(librarySet.getOwner(), is(equalTo(updatedLibrarySet.getOwner())));
    assertThat(librarySet.getAcls().size(), is(equalTo(1)));
    assertThat(librarySet.getAcls().get(0).getUserId(), is(equalTo(aclSpec2.getUserId())));
  }

  @Test
  public void testRevokeOperation() {
    AclSpecification aclSpec = new AclSpecification();
    aclSpec.setUserId("john");
    aclSpec.setRoles(Set.of(RoleEnum.SHARED_WITH));
    AclOperation aclOperation =
        AclOperation.builder().acls(List.of(aclSpec)).action(AclOperation.AclAction.REVOKE).build();
    when(librarySetRepository.findByLibrarySetId(anyString())).thenReturn(Optional.of(librarySet));
    when(librarySetRepository.save(any(LibrarySet.class))).thenReturn(librarySet);

    LibrarySet updatedLibrarySet =
        librarySetService.updateLibrarySetAcls("1", aclOperation, "username");
    assertThat(updatedLibrarySet.getId(), is(equalTo(librarySet.getId())));
    assertThat(updatedLibrarySet.getOwner(), is(equalTo(librarySet.getOwner())));
    assertThat(updatedLibrarySet.getAcls().size(), is(equalTo(0)));
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

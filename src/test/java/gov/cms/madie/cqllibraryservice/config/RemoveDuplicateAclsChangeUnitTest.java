package gov.cms.madie.cqllibraryservice.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.library.LibrarySet;

@ExtendWith(MockitoExtension.class)
public class RemoveDuplicateAclsChangeUnitTest {
  @Mock private LibrarySetRepository librarySetRepository;
  @InjectMocks private RemoveDuplicateAclsChangeUnit changeUnit;

  private AclSpecification acl1 =
      AclSpecification.builder().userId("testUser1").roles(Set.of(RoleEnum.SHARED_WITH)).build();
  private AclSpecification acl2 =
      AclSpecification.builder().userId("testUser2").roles(Set.of(RoleEnum.SHARED_WITH)).build();
  private AclSpecification acl3 =
      AclSpecification.builder().userId("testuser1").roles(Set.of(RoleEnum.SHARED_WITH)).build();
  private LibrarySet librarySet = null;

  @BeforeEach
  public void setUp() {
    librarySet = LibrarySet.builder().acls(List.of(acl1, acl2, acl3)).build();
  }

  @Test
  void testRemoveDuplicateAcls() {
    when(librarySetRepository.findAll()).thenReturn(List.of(librarySet));

    changeUnit.removeDuplicateAcls(librarySetRepository);

    // After execution, librarySet should have only one ACL for testUser1 (case-insensitive)
    List<AclSpecification> updatedAcls = librarySet.getAcls();

    // Expected 2 ACLs after removing duplicates
    assertEquals(2, updatedAcls.size());
    // Expected only one ACL for testUser1
    assertEquals(
        1,
        updatedAcls.stream().filter(acl -> acl.getUserId().equalsIgnoreCase("testUser1")).count());
    // Expected one ACL for testUser2
    assertEquals(
        1,
        updatedAcls.stream().filter(acl -> acl.getUserId().equalsIgnoreCase("testUser2")).count());
  }

  @Test
  void testRemoveDuplicateAclsWhenNoDuplicates() {
    AclSpecification acl4 =
        AclSpecification.builder().userId("testUser3").roles(Set.of(RoleEnum.SHARED_WITH)).build();
    librarySet.setAcls(List.of(acl1, acl2, acl4));

    when(librarySetRepository.findAll()).thenReturn(List.of(librarySet));

    changeUnit.removeDuplicateAcls(librarySetRepository);

    List<AclSpecification> updatedAcls = librarySet.getAcls();
    // Expected 3 ACLs since there are no duplicates
    assertEquals(3, updatedAcls.size());
  }

  @Test
  void testRemoveDuplicateWhenNoLibrarySets() {
    when(librarySetRepository.findAll()).thenReturn(List.of());

    changeUnit.removeDuplicateAcls(librarySetRepository);

    // No library sets, so no ACLs should be modified
    // Just ensure that the method runs without exceptions
    assertDoesNotThrow(() -> librarySetRepository.findAll());
  }

  @Test
  void testRemoveDuplicateWhenNoAcls() {
    librarySet.setAcls(List.of());
    when(librarySetRepository.findAll()).thenReturn(List.of(librarySet));

    changeUnit.removeDuplicateAcls(librarySetRepository);

    List<AclSpecification> updatedAcls = librarySet.getAcls();
    assertEquals(0, updatedAcls.size());
  }

  @Test
  void testRemoveDuplicateWhenNoRoles() {
    AclSpecification acl4 = AclSpecification.builder().userId("testUser4").build();
    librarySet.setAcls(List.of(acl1, acl2, acl4));

    when(librarySetRepository.findAll()).thenReturn(List.of(librarySet));

    changeUnit.removeDuplicateAcls(librarySetRepository);

    List<AclSpecification> updatedAcls = librarySet.getAcls();
    assertEquals(2, updatedAcls.size());
  }

  @Test
  void testRemoveDuplicateWhenRolesDoNotContainSharedWith() {
    AclSpecification acl4 = AclSpecification.builder().userId("testUser4").roles(Set.of()).build();
    librarySet.setAcls(List.of(acl1, acl2, acl4));

    when(librarySetRepository.findAll()).thenReturn(List.of(librarySet));

    changeUnit.removeDuplicateAcls(librarySetRepository);

    List<AclSpecification> updatedAcls = librarySet.getAcls();
    assertEquals(2, updatedAcls.size());
  }

  @Test
  void testRollbackExecution() {
    ReflectionTestUtils.setField(changeUnit, "copyOfAllLibrarySets", List.of(librarySet));

    // Simulate rollback
    changeUnit.rollbackExecution(librarySetRepository);

    // After rollback, librarySet should have the original ACLs
    List<AclSpecification> rolledBackAcls = librarySet.getAcls();

    assertEquals(3, rolledBackAcls.size());
    assertEquals(
        2,
        rolledBackAcls.stream()
            .filter(acl -> acl.getUserId().equalsIgnoreCase("testUser1"))
            .count());
    assertEquals(
        1,
        rolledBackAcls.stream()
            .filter(acl -> acl.getUserId().equalsIgnoreCase("testUser2"))
            .count());
  }

  @Test
  void testRollbackExecutionWhenNoLibrarySets() {
    when(librarySetRepository.findAll()).thenReturn(List.of());

    changeUnit.rollbackExecution(librarySetRepository);

    // No library sets, so no ACLs should be modified
    // Just ensure that the method runs without exceptions
    assertDoesNotThrow(() -> librarySetRepository.findAll());
  }
}

package gov.cms.madie.cqllibraryservice.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import gov.cms.madie.cqllibraryservice.exceptions.InvalidIdException;
import gov.cms.madie.cqllibraryservice.exceptions.PermissionDeniedException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.exceptions.UnauthorizedException;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.access.UserStatus;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.dto.UserRolesDto;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CqlLibraryServiceAclTest {
  @Mock private LibrarySetService librarySetService;
  @Mock private UserServiceClient userServiceClient;
  @InjectMocks private CqlLibraryAccessControlService accessControlService;

  @Test
  void testCheckAccessPermissionsThrowsForNonOwnerWithoutShareRole() {
    CqlLibrary library =
        CqlLibrary.builder()
            .id("123")
            .librarySet(
                LibrarySet.builder()
                    .owner("owner")
                    .acls(
                        List.of(
                            AclSpecification.builder()
                                .userId("otherUser")
                                .roles(Set.of(RoleEnum.SHARED_WITH))
                                .build()))
                    .build())
            .build();

    assertThrows(
        PermissionDeniedException.class,
        () -> accessControlService.checkAccessPermissions(library, "testUser"));
  }

  @Test
  void testCheckAccessPermissionsAllowsSharedUser() {
    CqlLibrary library =
        CqlLibrary.builder()
            .id("123")
            .librarySet(
                LibrarySet.builder()
                    .owner("owner")
                    .acls(
                        List.of(
                            AclSpecification.builder()
                                .userId("testUser")
                                .roles(Set.of(RoleEnum.SHARED_WITH))
                                .build()))
                    .build())
            .build();

    assertDoesNotThrow(() -> accessControlService.checkAccessPermissions(library, "testUser"));
  }

  @Test
  void testCheckOwnershipThrowsForNonOwner() {
    CqlLibrary library =
        CqlLibrary.builder()
            .id("123")
            .librarySet(LibrarySet.builder().owner("owner").build())
            .build();

    assertThrows(
        PermissionDeniedException.class,
        () -> accessControlService.checkOwnership(library, "nonOwner"));
  }

  @Test
  void testVerifyLibrarySetAuthorizationThrowsWhenLibrarySetHasNoAcls() {
    LibrarySet librarySet = LibrarySet.builder().owner("test").build();

    assertThrows(
        UnauthorizedException.class,
        () ->
            accessControlService.verifyLibrarySetAuthorization(
                "testUser", "test", "targetId", null, librarySet));
  }

  @Test
  void testVerifyLibrarySetAuthorizationThrowsWhenAclDoesNotMatchUser() {
    LibrarySet librarySet =
        LibrarySet.builder()
            .librarySetId("librarySetId1")
            .owner("testUser")
            .acls(
                List.of(
                    AclSpecification.builder()
                        .userId("testUser1")
                        .roles(Set.of(RoleEnum.SHARED_WITH))
                        .build()))
            .build();

    assertThrows(
        UnauthorizedException.class,
        () ->
            accessControlService.verifyLibrarySetAuthorization(
                "testUser2", "test", "targetId", null, librarySet));
  }

  @Test
  void testVerifyLibrarySetAuthorizationAllowsMatchingAclRole() {
    String username = "testUser";
    RoleEnum allowedRole = RoleEnum.SHARED_WITH;
    LibrarySet librarySet =
        LibrarySet.builder()
            .owner("otherUser")
            .acls(
                List.of(
                    AclSpecification.builder().userId(username).roles(Set.of(allowedRole)).build()))
            .build();

    assertDoesNotThrow(
        () ->
            accessControlService.verifyLibrarySetAuthorization(
                username, "CqlLibrary", "targetId", List.of(allowedRole), librarySet));
  }

  @Test
  void testVerifyLibrarySetAuthorizationThrowsWhenAclRoleDoesNotMatch() {
    LibrarySet librarySet =
        LibrarySet.builder()
            .owner("otherUser")
            .acls(
                List.of(
                    AclSpecification.builder()
                        .userId("anotherUser")
                        .roles(Set.of(RoleEnum.SHARED_WITH))
                        .build()))
            .build();

    assertThrows(
        UnauthorizedException.class,
        () ->
            accessControlService.verifyLibrarySetAuthorization(
                "testUser", "CqlLibrary", "targetId", List.of(RoleEnum.SHARED_WITH), librarySet));
  }

  @Test
  void testVerifyAuthorizationThrowsWhenLibrarySetNotFound() {
    when(librarySetService.findByLibrarySetId(anyString())).thenReturn(null);

    CqlLibrary library = CqlLibrary.builder().id("Lib1").librarySetId("LibSetId1").build();

    assertThrows(
        ResourceNotFoundException.class,
        () -> accessControlService.verifyAuthorization("testUser", library, null, false));
  }

  @Test
  void testHasAdminRoleReturnsTrueForMadieAdmin() {
    when(userServiceClient.getUserRoles("adminUser", "token"))
        .thenReturn(
            UserRolesDto.builder().harpId("adminUser").roles(List.of("MADiE-Admin")).build());

    assertTrue(accessControlService.hasAdminRole("adminUser", "token"));
  }

  @Test
  void testHasAdminRoleReturnsFalseWhenRolesMissing() {
    when(userServiceClient.getUserRoles("user", "token"))
        .thenReturn(UserRolesDto.builder().harpId("user").roles(List.of("MADiE-User")).build());

    assertFalse(accessControlService.hasAdminRole("user", "token"));
  }

  @Test
  void testValidateHarpIdThrowsWhenUserNotFound() {
    when(userServiceClient.getUserDetails("unknownUser", "token")).thenReturn(null);

    assertThrows(
        InvalidIdException.class,
        () -> accessControlService.validateHarpId("unknownUser", "token"));
  }

  @Test
  void testValidateHarpIdThrowsWhenUserIsInactive() {
    when(userServiceClient.getUserDetails("inactiveUser", "token"))
        .thenReturn(
            UserDetailsDto.builder()
                .harpId("inactiveUser")
                .userStatus(UserStatus.DEACTIVATED)
                .build());

    assertThrows(
        InvalidIdException.class,
        () -> accessControlService.validateHarpId("inactiveUser", "token"));
  }

  @Test
  void testValidateHarpIdAllowsActiveUser() {
    when(userServiceClient.getUserDetails("activeUser", "token"))
        .thenReturn(
            UserDetailsDto.builder().harpId("activeUser").userStatus(UserStatus.ACTIVE).build());

    assertDoesNotThrow(() -> accessControlService.validateHarpId("activeUser", "token"));
  }
}

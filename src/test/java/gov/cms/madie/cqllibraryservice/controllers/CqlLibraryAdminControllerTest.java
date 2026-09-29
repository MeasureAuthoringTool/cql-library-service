package gov.cms.madie.cqllibraryservice.controllers;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import java.util.Set;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.dto.LibrarySearchCriteria;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import gov.cms.madie.cqllibraryservice.dto.IgPackageInstallRequest;
import gov.cms.madie.cqllibraryservice.services.AdminService;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryLockService;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryService;
import gov.cms.madie.cqllibraryservice.services.IgPackageService;
import gov.cms.madie.cqllibraryservice.exceptions.GeneralConflictException;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.library.CqlLibrary;

@ExtendWith(MockitoExtension.class)
public class CqlLibraryAdminControllerTest {

  @InjectMocks private CqlLibraryAdminController controller;
  @Mock private CqlLibraryLockService cqlLibraryLockService;
  @Mock private CqlLibraryService cqlLibraryService;
  @Mock private AdminService adminService;
  @Mock private IgPackageService igPackageService;
  @Mock Principal principal;

  @Test
  public void testUnlockAllByUser() {
    String msg1 = "Delete library locks for harpId: test.user";
    String msg2 = "Deleted library lock for Id: cqlLibrayId";
    when(cqlLibraryLockService.unlockByUser(anyString())).thenReturn(List.of(msg1, msg2));

    ResponseEntity<List<String>> response = controller.unlockAllByUser("test.user", principal);
    assertNotNull(response);
    assertEquals(2, response.getBody().size());
    assertTrue(response.getBody().get(0).contains(msg1));
    assertTrue(response.getBody().get(1).contains(msg2));
  }

  @Test
  void testDeleteLibraryAlongWithVersions() {
    String libraryName = "Helper";
    doNothing()
        .when(cqlLibraryService)
        .deleteLibraryAlongWithVersions(anyString(), anyString(), anyString());
    ResponseEntity<String> response =
        controller.deleteLibraryAlongWithVersions(libraryName, "token", "harpId");
    assertThat(
        response.getBody(),
        is(equalTo("The library and all its associated versions have been removed successfully.")));
  }

  @Test
  public void testUpdateAccessControl() {
    AclSpecification aclSpecification = new AclSpecification();
    aclSpecification.setUserId("user_1");
    aclSpecification.setRoles(Set.of(RoleEnum.SHARED_WITH));

    AclOperation aclOperation =
        AclOperation.builder()
            .acls(List.of(aclSpecification))
            .action(AclOperation.AclAction.GRANT)
            .build();

    List<AclSpecification> aclSpecifications = List.of(aclSpecification);

    when(cqlLibraryService.updateAccessControlList(
            anyString(), any(), anyString(), any(Boolean.class), anyString()))
        .thenReturn(aclSpecifications);

    ResponseEntity<List<AclSpecification>> output =
        controller.updateAccessControl("1", aclOperation, "token");

    verify(cqlLibraryService, times(1))
        .updateAccessControlList(anyString(), any(), anyString(), any(Boolean.class), anyString());
    assertThat(output.getBody(), equalTo(aclSpecifications));
  }

  @Test
  void testSearchLibrariesForUserUsesProfileHarpId() {
    LibrarySearchCriteria criteria = LibrarySearchCriteria.builder().searchField("helper").build();
    Page<LibraryListDTO> libraries =
        new PageImpl<>(List.of(LibraryListDTO.builder().id("library-1").build()));
    when(cqlLibraryService.getLibrariesByCriteria(
            eq(criteria), eq(OwnershipType.OWNED), any(Pageable.class), eq("profile_user")))
        .thenReturn(libraries);

    ResponseEntity<Page<LibraryListDTO>> response =
        controller.searchLibrariesForUser(
            "PROFILE_USER", OwnershipType.OWNED, criteria, 10, 0, "lastModifiedAt,false");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(libraries, response.getBody());
    verify(cqlLibraryService)
        .getLibrariesByCriteria(
            eq(criteria), eq(OwnershipType.OWNED), any(Pageable.class), eq("profile_user"));
  }

  @Test
  void testInstallIgPackage() {
    when(principal.getName()).thenReturn("admin.user");
    IgPackageInstallRequest request =
        IgPackageInstallRequest.builder()
            .packageId("hl7.fhir.us.qicore")
            .packageVersion("7.0.2")
            .build();
    doNothing().when(igPackageService).installIgPackage(anyString(), anyString(), anyString());

    ResponseEntity<String> response = controller.installIgPackage(principal, request);

    assertNotNull(response);
    assertEquals(HttpStatus.ACCEPTED.value(), response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().contains("hl7.fhir.us.qicore"));
    assertTrue(response.getBody().contains("7.0.2"));
    verify(igPackageService, times(1)).installIgPackage(anyString(), anyString(), anyString());
  }

  @Test
  void testCorrectLibraryVersionDelegatesToAdminService() {
    when(principal.getName()).thenReturn("Admin.User");
    CqlLibrary reverted =
        CqlLibrary.builder().id("lib-1").version(Version.parse("1.0.000")).draft(true).build();
    when(adminService.correctLibraryVersion(
            "lib-1", "1.0.001", "1.0.000", "owner.harp", "admin.user"))
        .thenReturn(reverted);

    ResponseEntity<CqlLibrary> response =
        controller.correctLibraryVersion("owner.harp", principal, "lib-1", "1.0.001", "1.0.000");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(reverted, response.getBody());
    verify(adminService, times(1))
        .correctLibraryVersion("lib-1", "1.0.001", "1.0.000", "owner.harp", "admin.user");
  }

  @Test
  void testCorrectLibraryVersionPropagatesServiceFailures() {
    when(principal.getName()).thenReturn("admin.user");
    when(adminService.correctLibraryVersion(
            anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenThrow(
            new GeneralConflictException(
                "New version # must be lower than the intended final version number"));

    GeneralConflictException exception =
        assertThrows(
            GeneralConflictException.class,
            () ->
                controller.correctLibraryVersion(
                    "owner.harp", principal, "lib-1", "1.0.001", "1.0.002"));
    assertEquals(
        "New version # must be lower than the intended final version number",
        exception.getMessage());
  }
}

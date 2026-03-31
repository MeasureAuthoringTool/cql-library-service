package gov.cms.madie.cqllibraryservice.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import gov.cms.madie.cqllibraryservice.dto.LibraryAccessReportDTO;
import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetActionLogRepository;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.LibrarySetActionLog;
import gov.cms.madie.models.library.LibrarySet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

  @Mock private CqlLibraryRepository cqlLibraryRepository;
  @Mock private LibrarySetActionLogRepository librarySetActionLogRepository;
  @Mock private ExcelClient excelClient;

  @InjectMocks private AdminService adminService;

  private LibraryListDTO testLibraryDTO;
  private LibrarySet testLibrarySet;
  private LibrarySetActionLog testActionLog;

  @BeforeEach
  void setUp() {
    AclSpecification acl1 = new AclSpecification();
    acl1.setUserId("user1");
    Set<RoleEnum> roles = new HashSet<>();
    roles.add(RoleEnum.SHARED_WITH);
    acl1.setRoles(roles);

    AclSpecification acl2 = new AclSpecification();
    acl2.setUserId("user2");
    acl2.setRoles(roles);

    testLibrarySet =
        LibrarySet.builder()
            .librarySetId("lib-set-123")
            .owner("testOwner")
            .acls(List.of(acl1, acl2))
            .build();

    testLibraryDTO =
        LibraryListDTO.builder()
            .id("lib-123")
            .cqlLibraryName("TestLibrary")
            .model("QI-Core v4.1.1")
            .librarySetId("lib-set-123")
            .librarySet(testLibrarySet)
            .build();

    testActionLog =
        LibrarySetActionLog.builder()
            .targetId("lib-set-123")
            .actions(
                List.of(
                    AccessControlAction.builder()
                        .actionType(ActionType.SHARED)
                        .sharedWith("user1")
                        .performedAt(Instant.parse("2025-06-15T10:30:00Z"))
                        .performedBy("testOwner")
                        .build(),
                    AccessControlAction.builder()
                        .actionType(ActionType.SHARED)
                        .sharedWith("user2")
                        .performedAt(Instant.parse("2025-07-20T14:45:00Z"))
                        .performedBy("testOwner")
                        .build()))
            .build();
  }

  @Test
  void exportSharedWithLibrariesReturnsExcelBytes() {
    byte[] expectedBytes = "excel content".getBytes();
    when(cqlLibraryRepository.findLibrariesForAccessReport(List.of("lib-123")))
        .thenReturn(List.of(testLibraryDTO));
    when(librarySetActionLogRepository.findByTargetId("lib-set-123"))
        .thenReturn(Optional.of(testActionLog));
    when(excelClient.getSharedAccessReportForLibraries(anyList(), eq("token")))
        .thenReturn(expectedBytes);

    byte[] result = adminService.exportSharedWithLibraries(List.of("lib-123"), "testUser", "token");

    assertNotNull(result);
    assertArrayEquals(expectedBytes, result);
    verify(excelClient).getSharedAccessReportForLibraries(anyList(), eq("token"));
  }

  @Test
  void exportSharedWithLibrariesThrowsExceptionForEmptyList() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> adminService.exportSharedWithLibraries(List.of(), "testUser", "token"));

    assertEquals(
        "Please provide at least one library id to export the shared access report.",
        exception.getMessage());
  }

  @Test
  void exportSharedWithLibrariesThrowsExceptionForNullList() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> adminService.exportSharedWithLibraries(null, "testUser", "token"));

    assertEquals(
        "Please provide at least one library id to export the shared access report.",
        exception.getMessage());
  }

  @Test
  void getLibrariesWithAccessReportReturnsEmptyListWhenNoLibrariesFound() {
    when(cqlLibraryRepository.findLibrariesForAccessReport(List.of("non-existent")))
        .thenReturn(Collections.emptyList());

    List<LibraryAccessReportDTO> result =
        adminService.getLibrariesWithAccessReport(List.of("non-existent"));

    assertTrue(result.isEmpty());
    verifyNoInteractions(librarySetActionLogRepository);
  }

  @Test
  void getLibrariesWithAccessReportMapsLibraryFieldsCorrectly() {
    when(cqlLibraryRepository.findLibrariesForAccessReport(List.of("lib-123")))
        .thenReturn(List.of(testLibraryDTO));
    when(librarySetActionLogRepository.findByTargetId("lib-set-123")).thenReturn(Optional.empty());

    List<LibraryAccessReportDTO> result =
        adminService.getLibrariesWithAccessReport(List.of("lib-123"));

    assertEquals(1, result.size());
    LibraryAccessReportDTO dto = result.get(0);
    assertEquals("lib-123", dto.getId());
    assertEquals("TestLibrary", dto.getLibraryName());
    assertEquals("QI-Core v4.1.1", dto.getLibraryModel());
    assertEquals("testOwner", dto.getOwner());
  }

  @Test
  void getLibrariesWithAccessReportIncludesSharedUsersWithDates() {
    when(cqlLibraryRepository.findLibrariesForAccessReport(List.of("lib-123")))
        .thenReturn(List.of(testLibraryDTO));
    when(librarySetActionLogRepository.findByTargetId("lib-set-123"))
        .thenReturn(Optional.of(testActionLog));

    List<LibraryAccessReportDTO> result =
        adminService.getLibrariesWithAccessReport(List.of("lib-123"));

    assertEquals(1, result.size());
    List<LibraryAccessReportDTO.SharedWithUser> sharedWith = result.get(0).getSharedWith();
    assertEquals(2, sharedWith.size());

    LibraryAccessReportDTO.SharedWithUser user1 =
        sharedWith.stream().filter(u -> u.getUserId().equals("user1")).findFirst().orElseThrow();
    assertEquals("2025-06-15", user1.getDateShared());

    LibraryAccessReportDTO.SharedWithUser user2 =
        sharedWith.stream().filter(u -> u.getUserId().equals("user2")).findFirst().orElseThrow();
    assertEquals("2025-07-20", user2.getDateShared());
  }

  @Test
  void getLibrariesWithAccessReportHandlesNullDateSharedWhenNoActionLog() {
    when(cqlLibraryRepository.findLibrariesForAccessReport(List.of("lib-123")))
        .thenReturn(List.of(testLibraryDTO));
    when(librarySetActionLogRepository.findByTargetId("lib-set-123")).thenReturn(Optional.empty());

    List<LibraryAccessReportDTO> result =
        adminService.getLibrariesWithAccessReport(List.of("lib-123"));

    List<LibraryAccessReportDTO.SharedWithUser> sharedWith = result.get(0).getSharedWith();
    assertEquals(2, sharedWith.size());
    assertNull(sharedWith.get(0).getDateShared());
    assertNull(sharedWith.get(1).getDateShared());
  }

  @Test
  void getLibrariesWithAccessReportHandlesLibraryWithNoAcls() {
    LibrarySet librarySetNoAcls =
        LibrarySet.builder().librarySetId("lib-set-456").owner("owner").acls(null).build();
    LibraryListDTO libraryNoAcls =
        LibraryListDTO.builder()
            .id("lib-456")
            .cqlLibraryName("NoAclsLibrary")
            .librarySetId("lib-set-456")
            .librarySet(librarySetNoAcls)
            .build();

    when(cqlLibraryRepository.findLibrariesForAccessReport(List.of("lib-456")))
        .thenReturn(List.of(libraryNoAcls));

    List<LibraryAccessReportDTO> result =
        adminService.getLibrariesWithAccessReport(List.of("lib-456"));

    assertEquals(1, result.size());
    assertTrue(result.get(0).getSharedWith().isEmpty());
  }

  @Test
  void getLibrariesWithAccessReportHandlesLibraryWithNullLibrarySet() {
    LibraryListDTO libraryNoSet =
        LibraryListDTO.builder()
            .id("lib-789")
            .cqlLibraryName("NoSetLibrary")
            .librarySetId(null)
            .librarySet(null)
            .build();

    when(cqlLibraryRepository.findLibrariesForAccessReport(List.of("lib-789")))
        .thenReturn(List.of(libraryNoSet));

    List<LibraryAccessReportDTO> result =
        adminService.getLibrariesWithAccessReport(List.of("lib-789"));

    assertEquals(1, result.size());
    assertNull(result.get(0).getOwner());
    assertTrue(result.get(0).getSharedWith().isEmpty());
  }

  @Test
  void getLibrariesWithAccessReportUsesLatestShareDateForMultipleShares() {
    AclSpecification acl = new AclSpecification();
    acl.setUserId("resharedUser");
    acl.setRoles(Set.of(RoleEnum.SHARED_WITH));

    LibrarySet librarySet =
        LibrarySet.builder()
            .librarySetId("lib-set-multi")
            .owner("owner")
            .acls(List.of(acl))
            .build();
    LibraryListDTO library =
        LibraryListDTO.builder()
            .id("lib-multi")
            .librarySetId("lib-set-multi")
            .librarySet(librarySet)
            .build();

    LibrarySetActionLog actionLogWithMultipleShares =
        LibrarySetActionLog.builder()
            .targetId("lib-set-multi")
            .actions(
                List.of(
                    AccessControlAction.builder()
                        .actionType(ActionType.SHARED)
                        .sharedWith("resharedUser")
                        .performedAt(Instant.parse("2025-01-01T10:00:00Z"))
                        .build(),
                    AccessControlAction.builder()
                        .actionType(ActionType.SHARED)
                        .sharedWith("resharedUser")
                        .performedAt(Instant.parse("2025-03-15T10:00:00Z"))
                        .build()))
            .build();

    when(cqlLibraryRepository.findLibrariesForAccessReport(List.of("lib-multi")))
        .thenReturn(List.of(library));
    when(librarySetActionLogRepository.findByTargetId("lib-set-multi"))
        .thenReturn(Optional.of(actionLogWithMultipleShares));

    List<LibraryAccessReportDTO> result =
        adminService.getLibrariesWithAccessReport(List.of("lib-multi"));

    assertEquals("2025-03-15", result.get(0).getSharedWith().get(0).getDateShared());
  }

  @Test
  void getLibrariesWithAccessReportIgnoresNonSharedActionTypes() {
    AclSpecification acl = new AclSpecification();
    acl.setUserId("user1");
    acl.setRoles(Set.of(RoleEnum.SHARED_WITH));

    LibrarySet librarySet =
        LibrarySet.builder()
            .librarySetId("lib-set-mixed")
            .owner("owner")
            .acls(List.of(acl))
            .build();
    LibraryListDTO library =
        LibraryListDTO.builder()
            .id("lib-mixed")
            .librarySetId("lib-set-mixed")
            .librarySet(librarySet)
            .build();

    LibrarySetActionLog actionLogWithMixedTypes =
        LibrarySetActionLog.builder()
            .targetId("lib-set-mixed")
            .actions(
                List.of(
                    AccessControlAction.builder()
                        .actionType(ActionType.CREATED)
                        .performedAt(Instant.parse("2025-01-01T10:00:00Z"))
                        .build(),
                    AccessControlAction.builder()
                        .actionType(ActionType.UNSHARED)
                        .sharedWith("user1")
                        .performedAt(Instant.parse("2025-02-01T10:00:00Z"))
                        .build()))
            .build();

    when(cqlLibraryRepository.findLibrariesForAccessReport(List.of("lib-mixed")))
        .thenReturn(List.of(library));
    when(librarySetActionLogRepository.findByTargetId("lib-set-mixed"))
        .thenReturn(Optional.of(actionLogWithMixedTypes));

    List<LibraryAccessReportDTO> result =
        adminService.getLibrariesWithAccessReport(List.of("lib-mixed"));

    assertNull(result.get(0).getSharedWith().get(0).getDateShared());
  }

  @Test
  void getLibrariesWithAccessReportHandlesCaseInsensitiveUserIdMatching() {
    AclSpecification acl = new AclSpecification();
    acl.setUserId("UserName");
    acl.setRoles(Set.of(RoleEnum.SHARED_WITH));

    LibrarySet librarySet =
        LibrarySet.builder().librarySetId("lib-set-case").owner("owner").acls(List.of(acl)).build();
    LibraryListDTO library =
        LibraryListDTO.builder()
            .id("lib-case")
            .librarySetId("lib-set-case")
            .librarySet(librarySet)
            .build();

    LibrarySetActionLog actionLog =
        LibrarySetActionLog.builder()
            .targetId("lib-set-case")
            .actions(
                List.of(
                    AccessControlAction.builder()
                        .actionType(ActionType.SHARED)
                        .sharedWith("username")
                        .performedAt(Instant.parse("2025-05-01T10:00:00Z"))
                        .build()))
            .build();

    when(cqlLibraryRepository.findLibrariesForAccessReport(List.of("lib-case")))
        .thenReturn(List.of(library));
    when(librarySetActionLogRepository.findByTargetId("lib-set-case"))
        .thenReturn(Optional.of(actionLog));

    List<LibraryAccessReportDTO> result =
        adminService.getLibrariesWithAccessReport(List.of("lib-case"));

    assertEquals("2025-05-01", result.get(0).getSharedWith().get(0).getDateShared());
  }

  @Test
  void getLibrariesWithAccessReportHandlesMultipleLibrariesWithSameLibrarySet() {
    LibraryListDTO library1 =
        LibraryListDTO.builder()
            .id("lib-v1")
            .cqlLibraryName("Library")
            .librarySetId("lib-set-123")
            .librarySet(testLibrarySet)
            .build();
    LibraryListDTO library2 =
        LibraryListDTO.builder()
            .id("lib-v2")
            .cqlLibraryName("Library")
            .librarySetId("lib-set-123")
            .librarySet(testLibrarySet)
            .build();

    when(cqlLibraryRepository.findLibrariesForAccessReport(List.of("lib-v1", "lib-v2")))
        .thenReturn(List.of(library1, library2));
    when(librarySetActionLogRepository.findByTargetId("lib-set-123"))
        .thenReturn(Optional.of(testActionLog));

    List<LibraryAccessReportDTO> result =
        adminService.getLibrariesWithAccessReport(List.of("lib-v1", "lib-v2"));

    assertEquals(2, result.size());
    verify(librarySetActionLogRepository, times(1)).findByTargetId("lib-set-123");
  }
}

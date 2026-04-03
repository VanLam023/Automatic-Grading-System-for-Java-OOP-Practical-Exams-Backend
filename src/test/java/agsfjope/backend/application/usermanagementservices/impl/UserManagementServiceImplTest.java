package agsfjope.backend.application.usermanagementservices.impl;

import agsfjope.backend.application.dtos.requests.user.CreateUserRequest;
import agsfjope.backend.application.dtos.requests.user.UpdateUserRequest;
import agsfjope.backend.application.dtos.responses.user.CreateUserResponse;
import agsfjope.backend.application.dtos.responses.user.ImportStudentResponse;
import agsfjope.backend.application.dtos.responses.user.UserDetailResponse;
import agsfjope.backend.application.ports.out.EmailService;
import agsfjope.backend.core.entities.PasswordResetToken;
import agsfjope.backend.core.entities.Role;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.repositories.auth.PasswordResetTokenRepository;
import agsfjope.backend.core.repositories.auth.RoleRepository;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.infrastructure.excel.ExcelStudentParser;
import agsfjope.backend.testutils.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho UserManagementServiceImpl.
 * Phân loại: [N] Normal, [B] Boundary, [A] Abnormal.
 * Pattern: AAA (Arrange - Act - Assert).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserManagementServiceImpl Tests")
class UserManagementServiceImplTest {

    @Mock private ExcelStudentParser excelStudentParser;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private MultipartFile mockFile;

    @InjectMocks
    private UserManagementServiceImpl service;

    // ─── Shared fixtures ──────────────────────────────────────────────────────

    private User activeStudent;
    private Role studentRole;
    private Role sysAdminRole;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        activeStudent = TestDataFactory.createActiveStudent(); // lamtvse173173
        studentRole = TestDataFactory.createStudentRole();

        sysAdminRole = new Role();
        sysAdminRole.setRoleId(99);
        sysAdminRole.setName("SYSTEM_ADMIN");

        testUserId = activeStudent.getUserId();
    }

    // =========================================================================
    // importStudentsFromExcel()
    // =========================================================================

    @Test
    @DisplayName("[N] importStudentsFromExcel - Import thành công 1 student mới từ Excel (email/MSSV chưa tồn tại)")
    void importStudentsFromExcel_OneValidRow_SavesUserAndReturnsSuccess() {
        // Arrange
        agsfjope.backend.application.dtos.requests.user.ImportStudentRequest parsedRow =
                new agsfjope.backend.application.dtos.requests.user.ImportStudentRequest();
        parsedRow.setEmail("newstudent@fpt.edu.vn");
        parsedRow.setFullName("New Student");
        parsedRow.setMssv("SE999999");

        when(excelStudentParser.parse(eq(mockFile), anyList()))
                .thenReturn(List.of(parsedRow));
        when(roleRepository.findByName("STUDENT")).thenReturn(Optional.of(studentRole));
        when(userRepository.findByEmailIn(any())).thenReturn(List.of());
        when(userRepository.findByMssvIn(any())).thenReturn(List.of());
        when(userRepository.findByUsernameIn(any())).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
        when(userRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        ImportStudentResponse response = service.importStudentsFromExcel(mockFile);

        // Assert
        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getSkippedCount()).isEqualTo(0);
        verify(userRepository).saveAll(anyList());
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    @DisplayName("[A] importStudentsFromExcel - Bỏ qua row trùng email 'newstudent@fpt.edu.vn' đã tồn tại trong DB")
    void importStudentsFromExcel_DuplicateEmail_SkipsRow() {
        // Arrange
        agsfjope.backend.application.dtos.requests.user.ImportStudentRequest parsedRow =
                new agsfjope.backend.application.dtos.requests.user.ImportStudentRequest();
        parsedRow.setEmail("lamtvse173173@fpt.edu.vn");  // đã tồn tại trong DB
        parsedRow.setFullName("Lam Tran Van");
        parsedRow.setMssv("SE173173");

        User existingUser = TestDataFactory.createActiveStudent();

        when(excelStudentParser.parse(eq(mockFile), anyList()))
                .thenReturn(List.of(parsedRow));
        when(roleRepository.findByName("STUDENT")).thenReturn(Optional.of(studentRole));
        when(userRepository.findByEmailIn(any())).thenReturn(List.of(existingUser));
        when(userRepository.findByMssvIn(any())).thenReturn(List.of());
        when(userRepository.findByUsernameIn(any())).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");

        // Act
        ImportStudentResponse response = service.importStudentsFromExcel(mockFile);

        // Assert
        assertThat(response.getSuccessCount()).isEqualTo(0);
        assertThat(response.getSkippedCount()).isEqualTo(1);
        assertThat(response.getSkippedDetails().get(0).getReason())
                .contains("Email đã tồn tại trong hệ thống");
        verify(userRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("[B] importStudentsFromExcel - File Excel rỗng (không có row hợp lệ nào), trả về successCount=0")
    void importStudentsFromExcel_EmptyFile_ReturnsZeroSuccess() {
        // Arrange — parser trả về list rỗng (file không có data rows)
        when(excelStudentParser.parse(eq(mockFile), anyList()))
                .thenReturn(List.of());

        // Act
        ImportStudentResponse response = service.importStudentsFromExcel(mockFile);

        // Assert
        assertThat(response.getSuccessCount()).isEqualTo(0);
        verify(roleRepository, never()).findByName(any());
        verify(userRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("[A] importStudentsFromExcel - Bỏ qua row trùng MSSV 'SE173173' đã tồn tại trong DB")
    void importStudentsFromExcel_DuplicateMssv_SkipsRow() {
        // Arrange
        agsfjope.backend.application.dtos.requests.user.ImportStudentRequest parsedRow =
                new agsfjope.backend.application.dtos.requests.user.ImportStudentRequest();
        parsedRow.setEmail("another@fpt.edu.vn");
        parsedRow.setFullName("Another Student");
        parsedRow.setMssv("SE173173"); // MSSV đã tồn tại

        // Tạo 1 user giả có MSSV = "SE173173" (uppercase như DB lưu)
        User existingUserWithSameMssv = TestDataFactory.createActiveStudent();
        existingUserWithSameMssv.setMssv("SE173173"); // đảm bảo uppercase khớp với Set check

        when(excelStudentParser.parse(eq(mockFile), anyList()))
                .thenReturn(List.of(parsedRow));
        when(roleRepository.findByName("STUDENT")).thenReturn(Optional.of(studentRole));
        when(userRepository.findByEmailIn(any())).thenReturn(List.of());
        when(userRepository.findByMssvIn(any())).thenReturn(List.of(existingUserWithSameMssv));
        when(userRepository.findByUsernameIn(any())).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");

        // Act
        ImportStudentResponse response = service.importStudentsFromExcel(mockFile);

        // Assert
        assertThat(response.getSuccessCount()).isEqualTo(0);
        assertThat(response.getSkippedDetails().get(0).getReason())
                .contains("MSSV đã tồn tại trong hệ thống");
    }


    // =========================================================================
    // createUser()
    // =========================================================================

    @Test
    @DisplayName("[N] createUser - Tạo user STUDENT 'lamtvse173173@fpt.edu.vn' thành công với MSSV 'SE173173'")
    void createUser_ValidStudentRequest_SavesAndReturnsResponse() {
        // Arrange
        CreateUserRequest request = CreateUserRequest.builder()
                .roleName("STUDENT")
                .email("lamtvse173173@fpt.edu.vn")
                .fullName("Lam Tran Van")
                .mssv("SE173173")
                .build();

        when(userRepository.findByEmail("lamtvse173173@fpt.edu.vn")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("lamtvse173173")).thenReturn(Optional.empty());
        when(userRepository.findByMssv("SE173173")).thenReturn(Optional.empty());
        when(roleRepository.findByName("STUDENT")).thenReturn(Optional.of(studentRole));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        CreateUserResponse response = service.createUser(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("lamtvse173173");
        assertThat(response.getEmail()).isEqualTo("lamtvse173173@fpt.edu.vn");
        assertThat(response.getRoleName()).isEqualTo("STUDENT");
        assertThat(response.getMssv()).isEqualTo("SE173173");
        assertThat(response.isActive()).isFalse();
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("[A] createUser - Throw IllegalArgumentException khi roleName='ADMIN' không hợp lệ")
    void createUser_InvalidRole_ThrowIllegalArgumentException() {
        // Arrange
        CreateUserRequest request = CreateUserRequest.builder()
                .roleName("ADMIN") // không thuộc whitelist
                .email("test@fpt.edu.vn")
                .fullName("Test User")
                .build();

        // Act & Assert
        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role 'ADMIN' không hợp lệ");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] createUser - Throw IllegalArgumentException khi STUDENT không có MSSV")
    void createUser_StudentWithoutMssv_ThrowIllegalArgumentException() {
        // Arrange
        CreateUserRequest request = CreateUserRequest.builder()
                .roleName("STUDENT")
                .email("lamtvse173173@fpt.edu.vn")
                .fullName("Lam Tran Van")
                .mssv(null) // thiếu MSSV
                .build();

        // Act & Assert
        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MSSV là bắt buộc khi tạo tài khoản STUDENT");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] createUser - Throw IllegalArgumentException khi STUDENT dùng email không phải @fpt.edu.vn")
    void createUser_StudentWithNonFptEmail_ThrowIllegalArgumentException() {
        // Arrange
        CreateUserRequest request = CreateUserRequest.builder()
                .roleName("STUDENT")
                .email("lamtvse173173@gmail.com") // sai domain
                .fullName("Lam Tran Van")
                .mssv("SE173173")
                .build();

        // Act & Assert
        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sinh viên bắt buộc phải dùng email FPT");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] createUser - Throw IllegalArgumentException khi email 'lamtvse173173@fpt.edu.vn' đã tồn tại trong DB")
    void createUser_DuplicateEmail_ThrowIllegalArgumentException() {
        // Arrange
        CreateUserRequest request = CreateUserRequest.builder()
                .roleName("STUDENT")
                .email("lamtvse173173@fpt.edu.vn")
                .fullName("Lam Tran Van")
                .mssv("SE173173")
                .build();

        when(userRepository.findByEmail("lamtvse173173@fpt.edu.vn"))
                .thenReturn(Optional.of(activeStudent));

        // Act & Assert
        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email 'lamtvse173173@fpt.edu.vn' đã được sử dụng");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("[N] createUser - Tạo LECTURER thành công (không cần MSSV)")
    void createUser_ValidLecturerRequest_SavesSuccessfully() {
        // Arrange
        Role lecturerRole = TestDataFactory.createLecturerRole();
        CreateUserRequest request = CreateUserRequest.builder()
                .roleName("LECTURER")
                .email("lecturer01@fe.edu.vn")
                .fullName("Lecturer01 Name")
                .build();

        when(userRepository.findByEmail("lecturer01@fe.edu.vn")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("lecturer01")).thenReturn(Optional.empty());
        when(roleRepository.findByName("LECTURER")).thenReturn(Optional.of(lecturerRole));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        CreateUserResponse response = service.createUser(request);

        // Assert
        assertThat(response.getRoleName()).isEqualTo("LECTURER");
        assertThat(response.getMssv()).isNull(); // LECTURER không có MSSV
    }

    // =========================================================================
    // deleteUser()
    // =========================================================================

    @Test
    @DisplayName("[N] deleteUser - Soft-delete user 'lamtvse173173' thành công, deletedAt được set")
    void deleteUser_ExistingActiveUser_SoftDeletesSuccessfully() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(activeStudent));
        when(userRepository.save(any(User.class))).thenReturn(activeStudent);

        // Act
        service.deleteUser(testUserId);

        // Assert
        assertThat(activeStudent.getDeletedAt()).isNotNull();
        assertThat(activeStudent.getIsActive()).isFalse();
        assertThat(activeStudent.getIsLocked()).isTrue();
        verify(userRepository).save(activeStudent);
    }

    @Test
    @DisplayName("[A] deleteUser - Throw IllegalArgumentException khi user không tồn tại trong DB")
    void deleteUser_UserNotFound_ThrowIllegalArgumentException() {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.deleteUser(unknownId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy user với ID:");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] deleteUser - Throw IllegalArgumentException khi cố xoá tài khoản SYSTEM_ADMIN")
    void deleteUser_SystemAdminUser_ThrowIllegalArgumentException() {
        // Arrange
        User adminUser = TestDataFactory.createActiveStudent();
        adminUser.setRole(sysAdminRole);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(adminUser));

        // Act & Assert
        assertThatThrownBy(() -> service.deleteUser(testUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không thể xoá tài khoản SYSTEM_ADMIN");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] deleteUser - Throw IllegalArgumentException khi user đã bị xoá trước đó")
    void deleteUser_AlreadyDeletedUser_ThrowIllegalArgumentException() {
        // Arrange
        User deletedUser = TestDataFactory.createActiveStudent();
        deletedUser.setDeletedAt(OffsetDateTime.now().minusDays(1));
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(deletedUser));

        // Act & Assert
        assertThatThrownBy(() -> service.deleteUser(testUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đã bị xoá trước đó");

        verify(userRepository, never()).save(any());
    }

    // =========================================================================
    // activateUser()
    // =========================================================================

    @Test
    @DisplayName("[N] activateUser - Kích hoạt user 'lamtvse173173' chưa active thành công")
    void activateUser_InactiveUser_ActivatesSuccessfully() {
        // Arrange
        User inactiveUser = TestDataFactory.createInactiveStudent();
        UUID userId = inactiveUser.getUserId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(inactiveUser));
        when(userRepository.save(any(User.class))).thenReturn(inactiveUser);

        // Act
        service.activateUser(userId);

        // Assert
        assertThat(inactiveUser.getIsActive()).isTrue();
        assertThat(inactiveUser.getIsLocked()).isFalse();
        assertThat(inactiveUser.getEmailVerifiedAt()).isNotNull();
        verify(userRepository).save(inactiveUser);
    }

    @Test
    @DisplayName("[A] activateUser - Throw IllegalArgumentException khi user 'lamtvse173173' đã active rồi")
    void activateUser_AlreadyActiveUser_ThrowIllegalArgumentException() {
        // Arrange — activeStudent đã có isActive=true
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(activeStudent));

        // Act & Assert
        assertThatThrownBy(() -> service.activateUser(testUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đã được kích hoạt rồi");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] activateUser - Throw IllegalArgumentException khi user đã bị xoá (deletedAt != null)")
    void activateUser_DeletedUser_ThrowIllegalArgumentException() {
        // Arrange
        User deletedUser = TestDataFactory.createInactiveStudent();
        deletedUser.setDeletedAt(OffsetDateTime.now().minusDays(2));
        when(userRepository.findById(deletedUser.getUserId())).thenReturn(Optional.of(deletedUser));

        // Act & Assert
        assertThatThrownBy(() -> service.activateUser(deletedUser.getUserId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đã bị xoá, không thể kích hoạt");

        verify(userRepository, never()).save(any());
    }

    // =========================================================================
    // getAllUsers()
    // =========================================================================

    @Test
    @DisplayName("[N] getAllUsers - Trả về Page<UserDetailResponse> chứa user 'lamtvse173173' (không phải SYSTEM_ADMIN)")
    void getAllUsers_ReturnsPageOfUsers() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> userPage = new PageImpl<>(List.of(activeStudent));
        when(userRepository.findAllByDeletedAtIsNullAndRoleNameNot("SYSTEM_ADMIN", pageable))
                .thenReturn(userPage);

        // Act
        Page<UserDetailResponse> result = service.getAllUsers(pageable);

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("lamtvse173173");
    }

    @Test
    @DisplayName("[B] getAllUsers - Trả về Page rỗng khi không có user nào (Boundary)")
    void getAllUsers_EmptyDB_ReturnsEmptyPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> emptyPage = new PageImpl<>(List.of());
        when(userRepository.findAllByDeletedAtIsNullAndRoleNameNot("SYSTEM_ADMIN", pageable))
                .thenReturn(emptyPage);

        // Act
        Page<UserDetailResponse> result = service.getAllUsers(pageable);

        // Assert
        assertThat(result.getContent()).isEmpty();
    }

    // =========================================================================
    // searchUsers()
    // =========================================================================

    @Test
    @DisplayName("[N] searchUsers - Tìm kiếm theo keyword='lamtv' trả về user 'lamtvse173173'")
    void searchUsers_WithKeyword_ReturnsMatchingUsers() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> userPage = new PageImpl<>(List.of(activeStudent));
        when(userRepository.searchUsers("lamtv", "", pageable)).thenReturn(userPage);

        // Act
        Page<UserDetailResponse> result = service.searchUsers("lamtv", null, pageable);

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("lamtvse173173");
    }

    @Test
    @DisplayName("[B] searchUsers - Tìm kiếm với keyword=null và roleName=null → normalize thành empty string, gọi searchUsers('', '')")
    void searchUsers_NullKeywordAndRole_NormalizesToEmptyStrings() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.searchUsers("", "", pageable))
                .thenReturn(new PageImpl<>(List.of()));

        // Act
        Page<UserDetailResponse> result = service.searchUsers(null, null, pageable);

        // Assert
        assertThat(result.getContent()).isEmpty();
        verify(userRepository).searchUsers("", "", pageable);
    }

    // =========================================================================
    // getUserById()
    // =========================================================================

    @Test
    @DisplayName("[N] getUserById - Lấy thông tin user 'lamtvse173173' theo UUID thành công")
    void getUserById_ExistingNonDeletedUser_ReturnsResponse() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(activeStudent));

        // Act
        UserDetailResponse response = service.getUserById(testUserId);

        // Assert
        assertThat(response.getUsername()).isEqualTo("lamtvse173173");
        assertThat(response.getEmail()).isEqualTo("lamtvse173173@fpt.edu.vn");
    }

    @Test
    @DisplayName("[A] getUserById - Throw IllegalArgumentException khi user không tồn tại trong DB")
    void getUserById_UserNotFound_ThrowIllegalArgumentException() {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.getUserById(unknownId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy user với ID:");
    }

    @Test
    @DisplayName("[A] getUserById - Throw IllegalArgumentException khi user là SYSTEM_ADMIN (ẩn khỏi admin UI)")
    void getUserById_SystemAdminUser_ThrowIllegalArgumentException() {
        // Arrange
        User adminUser = TestDataFactory.createActiveStudent();
        adminUser.setRole(sysAdminRole);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(adminUser));

        // Act & Assert
        assertThatThrownBy(() -> service.getUserById(testUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy user với ID:");
    }

    @Test
    @DisplayName("[A] getUserById - Throw IllegalArgumentException khi user đã bị soft-deleted")
    void getUserById_DeletedUser_ThrowIllegalArgumentException() {
        // Arrange
        User deletedUser = TestDataFactory.createActiveStudent();
        deletedUser.setDeletedAt(OffsetDateTime.now().minusDays(1));
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(deletedUser));

        // Act & Assert
        assertThatThrownBy(() -> service.getUserById(testUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đã bị xoá");
    }

    // =========================================================================
    // updateUser()
    // =========================================================================

    @Test
    @DisplayName("[N] updateUser - Cập nhật fullName của 'lamtvse173173' thành công")
    void updateUser_UpdateFullName_SavesAndReturnsResponse() {
        // Arrange
        UpdateUserRequest request = new UpdateUserRequest();
        request.setFullName("Lam Tran Van Updated");

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(activeStudent));
        when(userRepository.save(any(User.class))).thenReturn(activeStudent);

        // Act
        UserDetailResponse response = service.updateUser(testUserId, request);

        // Assert
        assertThat(response.getFullName()).isEqualTo("Lam Tran Van Updated");
        verify(userRepository).save(activeStudent);
    }

    @Test
    @DisplayName("[A] updateUser - Throw IllegalArgumentException khi cố sửa tài khoản SYSTEM_ADMIN")
    void updateUser_SystemAdminUser_ThrowIllegalArgumentException() {
        // Arrange
        User adminUser = TestDataFactory.createActiveStudent();
        adminUser.setRole(sysAdminRole);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(adminUser));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setFullName("New Name");

        // Act & Assert
        assertThatThrownBy(() -> service.updateUser(testUserId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không thể chỉnh sửa tài khoản SYSTEM_ADMIN");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] updateUser - Throw IllegalArgumentException khi cố sửa user đã bị soft-deleted")
    void updateUser_DeletedUser_ThrowIllegalArgumentException() {
        // Arrange
        User deletedUser = TestDataFactory.createActiveStudent();
        deletedUser.setDeletedAt(OffsetDateTime.now().minusDays(3));
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(deletedUser));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setFullName("New Name");

        // Act & Assert
        assertThatThrownBy(() -> service.updateUser(testUserId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đã bị xoá, không thể chỉnh sửa");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] updateUser - Throw IllegalArgumentException khi email mới 'other@fpt.edu.vn' đã tồn tại trong DB")
    void updateUser_DuplicateEmail_ThrowIllegalArgumentException() {
        // Arrange
        User otherUser = TestDataFactory.createActiveStudent();

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(activeStudent));
        when(userRepository.findByEmail("other@fpt.edu.vn")).thenReturn(Optional.of(otherUser));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("other@fpt.edu.vn");

        // Act & Assert
        assertThatThrownBy(() -> service.updateUser(testUserId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email 'other@fpt.edu.vn' đã được sử dụng");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("[A] updateUser - Throw IllegalArgumentException khi roleName='SUPERUSER' không hợp lệ")
    void updateUser_InvalidRole_ThrowIllegalArgumentException() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(activeStudent));

        UpdateUserRequest request = new UpdateUserRequest();
        request.setRoleName("SUPERUSER"); // không nằm trong whitelist

        // Act & Assert
        assertThatThrownBy(() -> service.updateUser(testUserId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role 'SUPERUSER' không hợp lệ");

        verify(userRepository, never()).save(any());
    }
}

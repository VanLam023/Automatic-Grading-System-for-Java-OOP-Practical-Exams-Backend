package agsfjope.backend.application.usermanagementservices.impl;

import agsfjope.backend.application.dtos.requests.user.ImportStudentRequest;
import agsfjope.backend.application.dtos.responses.user.ImportStudentResponse;
import agsfjope.backend.application.dtos.responses.user.UserDetailResponse;
import agsfjope.backend.application.ports.out.EmailService;
import agsfjope.backend.application.usermanagementservices.UserManagementService;
import agsfjope.backend.core.entities.Role;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.repositories.auth.RoleRepository;
import agsfjope.backend.core.repositories.auth.UserRepository;
import agsfjope.backend.infrastructure.excel.ExcelStudentParser;
import agsfjope.backend.infrastructure.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of {@link UserManagementService}.
 * Orchestrates the full import flow: parse Excel → validate duplicates →
 * batch-create accounts → send credential emails asynchronously.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {

    private static final String DEFAULT_PASSWORD = "Abc@123";
    private static final String ROLE_STUDENT = "STUDENT";

    private final ExcelStudentParser excelStudentParser;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Bulk-imports student accounts from an uploaded Excel (.xlsx) file.
     *
     * <p>
     * Processing steps:
     * <ol>
     * <li>Parse the file via {@link ExcelStudentParser} — format/empty rows are
     * skipped here.</li>
     * <li>Load the Student Role from DB (throws if not found — seed data
     * issue).</li>
     * <li>Batch-fetch existing emails and MSSVs to detect duplicates without N+1
     * queries.</li>
     * <li>For each parsed row: skip if duplicate, otherwise build and save new
     * User.</li>
     * <li>Persist all valid users via {@code saveAll()} in a single
     * transaction.</li>
     * <li>Send credential emails asynchronously — emails don't block the response
     * to Admin.</li>
     * </ol>
     * </p>
     *
     * @param file the .xlsx multipart file uploaded by Admin
     * @return summary containing success/skip counts and skipped row details
     */
    @Override
    @Transactional
    public ImportStudentResponse importStudentsFromExcel(MultipartFile file) {
        // Accumulate skipped rows across all validation stages
        List<ImportStudentResponse.SkippedRow> skippedDetails = new ArrayList<>();

        // ── Step 1: Parse Excel file → raw parsed rows ─────────────────────
        // ExcelStudentParser handles format-level validation (empty rows, email
        // format).
        // It appends its own skipped entries into skippedDetails.
        List<ImportStudentRequest> parsedRows = excelStudentParser.parse(file, skippedDetails);
        int totalRows = parsedRows.size() + skippedDetails.size(); // total data rows (excl. header)

        if (parsedRows.isEmpty()) {
            log.info("[UserManagement] No valid rows found in Excel file.");
            return buildResponse(totalRows, 0, skippedDetails);
        }

        // ── Step 2: Load STUDENT role from DB ──────────────────────────────
        Role studentRole = roleRepository.findByName(ROLE_STUDENT)
                .orElseThrow(() -> new RuntimeException(
                        "Role 'STUDENT' not found in DB. Check seed data."));

        // ── Step 3: Batch-check duplicates to avoid N+1 DB queries ─────────
        // Collect all emails and MSSVs from parsed rows for a single IN query each
        List<String> emails = parsedRows.stream()
                .map(r -> r.getEmail().toLowerCase())
                .toList();
        List<String> mssvs = parsedRows.stream()
                .map(ImportStudentRequest::getMssv)
                .toList();

        // Set of existing emails and MSSVs from the database (case-insensitive)
        Set<String> existingEmails = userRepository.findByEmailIn(emails).stream()
                .map(u -> u.getEmail().toLowerCase())
                .collect(Collectors.toSet());
        Set<String> existingMssvs = userRepository.findByMssvIn(mssvs).stream()
                .map(User::getMssv)
                .collect(Collectors.toSet());

        // Also track usernames we're about to insert in this same batch
        // to prevent duplicates within the file itself
        Set<String> pendingUsernames = new java.util.HashSet<>();

        // ── Step 4: Build valid User entities, skip duplicates ──────────────
        String hashedPassword = passwordEncoder.encode(DEFAULT_PASSWORD);
        List<User> newUsers = new ArrayList<>();

        for (int i = 0; i < parsedRows.size(); i++) {
            ImportStudentRequest row = parsedRows.get(i);

            String email = row.getEmail().toLowerCase();
            String mssv = row.getMssv().toUpperCase();
            // Username = everything before the '@' symbol in the email
            String username = email.substring(0, email.indexOf('@'));

            // Check duplicate email in DB
            if (existingEmails.contains(email)) {
                skippedDetails.add(buildSkipped(i, row, "Email đã tồn tại trong hệ thống"));
                continue;
            }

            // Check duplicate MSSV in DB
            if (existingMssvs.contains(mssv)) {
                skippedDetails.add(buildSkipped(i, row, "MSSV đã tồn tại trong hệ thống"));
                continue;
            }

            // Check duplicate username in DB (derived from email, so usually same as email
            // check,
            // but guard against edge cases where two different emails share the same
            // prefix)
            boolean usernameExistsInDb = userRepository.findByUsername(username).isPresent();
            if (usernameExistsInDb || pendingUsernames.contains(username)) {
                skippedDetails.add(buildSkipped(i, row, "Username '" + username + "' đã tồn tại"));
                continue;
            }

            // All checks passed — build the User entity
            // isActive = false: student must change password on first login to activate
            // emailVerifiedAt = null: will be set when student changes password
            User newUser = User.builder()
                    .role(studentRole)
                    .username(username)
                    .email(email)
                    .passwordHash(hashedPassword)
                    .fullName(row.getFullName())
                    .mssv(mssv)
                    .isActive(false)
                    .isLocked(false)
                    .loginFailCount(0)
                    .build();

            newUsers.add(newUser);
            pendingUsernames.add(username);
            // Add email to the seen set so within-batch duplicates are also caught
            existingEmails.add(email);
            existingMssvs.add(mssv);
        }

        // ── Step 5: Batch save all new users in one transaction ─────────────
        if (!newUsers.isEmpty()) {
            userRepository.saveAll(newUsers);
            log.info("[UserManagement] Saved {} new student accounts.", newUsers.size());
        }

        // ── Step 6: Send credential + activation emails asynchronously ──────────────
        // Generate a fresh activation JWT for each user (24-hour expiry, signed with
        // email).
        // sendCredentialEmailAsync is @Async — runs on a background thread, Admin gets
        // response immediately.
        for (User user : newUsers) {
            // generateActivationToken(email) creates a JWT identical to the
            // self-registration flow
            String activationToken = jwtTokenProvider.generateActivationToken(user.getEmail());
            String activationLink = "http://localhost:5173/verify-account?token=" + activationToken;
            sendCredentialEmailAsync(user.getEmail(), user.getUsername(), activationLink);
        }

        return buildResponse(totalRows, newUsers.size(), skippedDetails);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends the credential + activation email on a separate async thread so the
     * Admin's
     * HTTP request is not blocked by SMTP operations.
     * Spring's @Async requires {@code @EnableAsync} in a configuration class
     * (AsyncConfig).
     *
     * @param email          recipient email
     * @param username       the generated username
     * @param activationLink the verify-account URL (JWT signed, 24h expiry)
     */
    @Async
    protected void sendCredentialEmailAsync(String email, String username, String activationLink) {
        try {
            emailService.sendAccountCredentialsEmail(email, username, DEFAULT_PASSWORD, activationLink);
        } catch (Exception e) {
            // Email failure must NOT roll back the DB transaction.
            // Log the error; Admin can manually resend or inform students.
            log.error("[UserManagement] Failed to send credential email to {}: {}", email, e.getMessage());
        }
    }

    /**
     * Builds a SkippedRow entry for a parsed row that failed duplicate checks.
     * The row number is approximated as (i + 2) because row 1 is the header.
     *
     * @param index  0-based index within parsedRows list
     * @param row    the parsed row data
     * @param reason human-readable skip reason
     * @return a SkippedRow DTO
     */
    private ImportStudentResponse.SkippedRow buildSkipped(int index,
            ImportStudentRequest row,
            String reason) {
        return ImportStudentResponse.SkippedRow.builder()
                .rowNumber(index + 2) // row 1 = header, data starts at row 2
                .email(row.getEmail())
                .reason(reason)
                .build();
    }

    /**
     * Assembles the final ImportStudentResponse from collected data.
     *
     * @param totalRows    total data rows read from Excel (excluding header)
     * @param successCount number of accounts successfully created
     * @param skipped      list of all skipped rows with reasons
     * @return the response DTO
     */
    private ImportStudentResponse buildResponse(int totalRows,
            int successCount,
            List<ImportStudentResponse.SkippedRow> skipped) {
        return ImportStudentResponse.builder()
                .totalRows(totalRows)
                .successCount(successCount)
                .skippedCount(skipped.size())
                .skippedDetails(skipped)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createUser — Admin manually creates ONE account
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public agsfjope.backend.application.dtos.responses.user.CreateUserResponse createUser(
            agsfjope.backend.application.dtos.requests.user.CreateUserRequest request) {

        // ── 1. Validate role (strict whitelist) ────────────────────────────
        String roleName = request.getRoleName() == null ? "" : request.getRoleName().toUpperCase().trim();
        java.util.Set<String> allowedRoles = java.util.Set.of("STUDENT", "EXAM_STAFF", "LECTURER");
        if (!allowedRoles.contains(roleName)) {
            throw new IllegalArgumentException(
                    "Role '" + request.getRoleName()
                            + "' không hợp lệ. Giá trị được phép: STUDENT, EXAM_STAFF, LECTURER.");
        }

        // ── 2. Normalize fields ─────────────────────────────────────────────
        String email = request.getEmail().trim().toLowerCase();
        // Normalize fullName: collapse consecutive spaces → single space
        String fullName = request.getFullName().strip().replaceAll("\\s{2,}", " ");
        String mssv = (request.getMssv() != null && !request.getMssv().isBlank())
                ? request.getMssv().trim().toUpperCase()
                : null;

        // Username always derived from email alias (part before '@')
        String username = email.substring(0, email.indexOf('@'));

        // ── 3. Business validation ───────────────────────────────────────────
        // For STUDENT with MSSV: email must belong to FPT student domain
        if ("STUDENT".equals(roleName) && mssv != null && !email.endsWith("@fpt.edu.vn")) {
            throw new IllegalArgumentException(
                    "Sinh viên có MSSV bắt buộc phải dùng email FPT (@fpt.edu.vn). Nhận được: " + email);
        }

        // ── 4. Duplicate checks ──────────────────────────────────────────────
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email '" + email + "' đã được sử dụng.");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username '" + username + "' đã tồn tại.");
        }
        if (mssv != null && userRepository.findByMssv(mssv).isPresent()) {
            throw new IllegalArgumentException("MSSV '" + mssv + "' đã tồn tại trong hệ thống.");
        }

        // ── 5. Resolve Role from DB ──────────────────────────────────────────
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Role '" + roleName + "' không tìm thấy trong database."));

        // ── 6. Save User ─────────────────────────────────────────────────────
        User newUser = User.builder()
                .role(role)
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                .fullName(fullName)
                .mssv(mssv)
                .isActive(false)
                .isLocked(false)
                .loginFailCount(0)
                .build();
        userRepository.save(newUser);
        log.info("[UserManagement] Created account '{}' with role '{}'.", username, roleName);

        // ── 7. Send credential + activation email (async) ───────────────────
        String activationToken = jwtTokenProvider.generateActivationToken(email);
        String activationLink = "http://localhost:5173/verify-account?token=" + activationToken;
        sendCredentialEmailAsync(email, username, activationLink);

        return agsfjope.backend.application.dtos.responses.user.CreateUserResponse.builder()
                .username(username)
                .email(email)
                .fullName(fullName)
                .roleName(roleName)
                .mssv(mssv)
                .active(false)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deleteUser — Soft delete (set deletedAt, do NOT remove from DB)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteUser(java.util.UUID userId) {
        // ── 1. Find user ────────────────────────────────────────────────────
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy user với ID: " + userId));

        // ── 2. Guard: cannot delete SYSTEM_ADMIN ────────────────────────────
        if ("SYSTEM_ADMIN".equals(user.getRole().getName())) {
            throw new IllegalArgumentException(
                    "Không thể xoá tài khoản SYSTEM_ADMIN.");
        }

        // ── 3. Guard: already deleted ───────────────────────────────────────
        if (user.getDeletedAt() != null) {
            throw new IllegalArgumentException(
                    "Tài khoản '" + user.getUsername() + "' đã bị xoá trước đó.");
        }

        // ── 4. Soft delete: stamp deletedAt, disable account ────────────────
        user.setDeletedAt(java.time.OffsetDateTime.now());
        user.setIsActive(false); // prevent login
        user.setIsLocked(true); // additional safety lock
        userRepository.save(user);

        log.info("[UserManagement] Soft-deleted user '{}' (ID: {}).", user.getUsername(), userId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // activateUser — Admin manually activates an account by UUID
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void activateUser(java.util.UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy user với ID: " + userId));

        if (Boolean.TRUE.equals(user.getIsActive())) {
            throw new IllegalArgumentException(
                    "Tài khoản '" + user.getUsername() + "' đã được kích hoạt rồi.");
        }
        if (user.getDeletedAt() != null) {
            throw new IllegalArgumentException(
                    "Tài khoản '" + user.getUsername() + "' đã bị xoá, không thể kích hoạt.");
        }

        user.setIsActive(true);
        user.setIsLocked(false);
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(java.time.OffsetDateTime.now());
        }
        userRepository.save(user);

        log.info("[UserManagement] Admin activated account '{}' (ID: {}).", user.getUsername(), userId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getAllUsers — paginated list of all non-deleted users
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<UserDetailResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAllByDeletedAtIsNull(pageable)
                .map(this::mapToUserDetailResponse);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // searchUsers — filter by keyword and/or roleName
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<UserDetailResponse> searchUsers(String keyword, String roleName, Pageable pageable) {
        // Normalize: pass null to JPQL to skip the filter clause
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        String rn = (roleName != null && !roleName.isBlank()) ? roleName.trim().toUpperCase() : null;
        return userRepository.searchUsers(kw, rn, pageable)
                .map(this::mapToUserDetailResponse);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getUserById — detail view of a single user
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UserDetailResponse getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy user với ID: " + userId));

        if (user.getDeletedAt() != null) {
            throw new IllegalArgumentException(
                    "Tài khoản '" + user.getUsername() + "' đã bị xoá.");
        }

        return mapToUserDetailResponse(user);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private mapper: User entity → UserDetailResponse DTO
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Maps a {@link User} entity to a {@link UserDetailResponse} DTO.
     * Centralised here to avoid duplication across getAllUsers / searchUsers / getUserById.
     *
     * @param user the source entity (must have role eagerly loaded)
     * @return the mapped response DTO
     */
    private UserDetailResponse mapToUserDetailResponse(User user) {
        return UserDetailResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roleName(user.getRole().getName())
                .mssv(user.getMssv())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .isActive(user.getIsActive())
                .isLocked(user.getIsLocked())
                .loginFailCount(user.getLoginFailCount())
                .lastLoginAt(user.getLastLoginAt())
                .emailVerifiedAt(user.getEmailVerifiedAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .deletedAt(user.getDeletedAt())
                .build();
    }
}

package agsfjope.backend.configuration.database;

import agsfjope.backend.core.entities.Role;
import agsfjope.backend.core.entities.User;
import agsfjope.backend.core.repositories.auth.RoleRepository;
import agsfjope.backend.core.repositories.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Automatically runs on application startup.
 * Checks if default users exist, and if not, creates them with BCrypt hashed passwords.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("Checking database for default user accounts...");

        // Ensure roles exist first (these should be seeded via schema.sql)
        Role adminRole = getRoleOrThrow("SYSTEM_ADMIN");
        Role staffRole = getRoleOrThrow("EXAM_STAFF");
        Role lecturerRole = getRoleOrThrow("LECTURER");
        Role studentRole = getRoleOrThrow("STUDENT");

        // The default password for all seed accounts
        String defaultPassword = "Password@123";
        String encodedPassword = passwordEncoder.encode(defaultPassword);

        // 1. System Admin
        createUserIfNotFound(
                "admin",
                "admin@oopexam.fpt.edu.vn",
                "System Administrator",
                null,
                adminRole,
                encodedPassword
        );

        // 2. Exam Staff
        createUserIfNotFound(
                "staff",
                "staff@oopexam.fpt.edu.vn",
                "Examination Staff",
                null,
                staffRole,
                encodedPassword
        );

        // 3. Lecturer
        createUserIfNotFound(
                "lecturer",
                "lecturer@oopexam.fpt.edu.vn",
                "Lecturer",
                null,
                lecturerRole,
                encodedPassword
        );

        // 4. Student
        createUserIfNotFound(
                "student",
                "student@oopexam.fpt.edu.vn",
                "Nguyen Van A (Student)",
                "SE140001",
                studentRole,
                encodedPassword
        );

        log.info("Database seeding complete.");
    }

    private Role getRoleOrThrow(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role '" + roleName + "' not found. Make sure docker/oop_exam_schema_full.sql has run."));
    }

    private void createUserIfNotFound(String username, String email, String fullName, String mssv, Role role, String encodedPassword) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .fullName(fullName)
                .mssv(mssv)
                .passwordHash(encodedPassword)
                .role(role)
                .isActive(true)      // Important: Account must be active to login
                .isLocked(false)     // Account must not be locked
                .loginFailCount(0)
                .build();

        userRepository.save(user);
        log.info("Created default user: {} with role: {}", username, role.getName());
    }
}

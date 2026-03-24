package agsfjope.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the OOP Exam Grading System (Backend).
 */
@SpringBootApplication
@EnableScheduling
public class AgsfjopeApplication {

    public static void main(String[] args) {
        // Set múi giờ Việt Nam cho toàn bộ JVM
        // PostgreSQL TIMESTAMPTZ sẽ tự convert UTC <-> +07:00
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SpringApplication.run(AgsfjopeApplication.class, args);
    }

}

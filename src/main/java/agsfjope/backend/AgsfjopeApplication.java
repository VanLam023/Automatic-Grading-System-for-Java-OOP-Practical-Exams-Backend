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
        SpringApplication.run(AgsfjopeApplication.class, args);
    }

}

package agsfjope.backend.core.repositories;

import agsfjope.backend.core.entities.Exam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExamRepository extends JpaRepository<Exam, UUID> {
    boolean existsByNameAndSemester(String name, String semester);
}

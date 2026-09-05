package ch.gotthard.domain.repository;

import ch.gotthard.domain.model.RiskAssessment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, UUID> {}

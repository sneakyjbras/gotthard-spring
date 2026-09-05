package ch.gotthard.domain.repository;

import ch.gotthard.domain.model.AiAnalysisCitation;
import ch.gotthard.domain.model.AiAnalysisCitationId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAnalysisCitationRepository extends JpaRepository<AiAnalysisCitation, AiAnalysisCitationId> {}

package ch.gotthard.domain.repository;

import ch.gotthard.domain.model.RiskRule;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskRuleRepository extends JpaRepository<RiskRule, UUID> {}

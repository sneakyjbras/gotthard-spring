package ch.gotthard.domain.repository;

import ch.gotthard.domain.model.Operator;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorRepository extends JpaRepository<Operator, UUID> {}

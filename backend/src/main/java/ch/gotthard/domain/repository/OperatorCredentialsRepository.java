package ch.gotthard.domain.repository;

import ch.gotthard.domain.model.OperatorCredentials;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorCredentialsRepository extends JpaRepository<OperatorCredentials, UUID> {}

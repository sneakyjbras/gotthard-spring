package ch.gotthard.domain.repository;

import ch.gotthard.domain.model.PolicyChunk;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyChunkRepository extends JpaRepository<PolicyChunk, UUID> {}

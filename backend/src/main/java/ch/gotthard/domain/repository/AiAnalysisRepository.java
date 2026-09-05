package ch.gotthard.domain.repository;

import ch.gotthard.domain.model.AiAnalysis;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, UUID> {

    /** A customer's analysis history, newest first — mirrors {@code idx_analysis_customer_time}. */
    @Query("select a from AiAnalysis a where a.customer.customerId = :customerId order by a.createdAt desc")
    List<AiAnalysis> findByCustomerId(@Param("customerId") UUID customerId);
}

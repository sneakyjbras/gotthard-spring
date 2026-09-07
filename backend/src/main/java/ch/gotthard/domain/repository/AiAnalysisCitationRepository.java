package ch.gotthard.domain.repository;

import ch.gotthard.domain.model.AiAnalysisCitation;
import ch.gotthard.domain.model.AiAnalysisCitationId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiAnalysisCitationRepository extends JpaRepository<AiAnalysisCitation, AiAnalysisCitationId> {

    /**
     * What the model was shown, best match first — the {@code rank} the citation was written with.
     *
     * <p>The chunk is fetched in the same query rather than left lazy: every caller wants the text,
     * and a citation whose body arrives one select at a time is a citation list that costs a query
     * per clause.
     */
    @Query(
            """
            select c from AiAnalysisCitation c
            join fetch c.chunk
            where c.analysis.analysisId = :analysisId
            order by c.rank asc""")
    List<AiAnalysisCitation> findByAnalysisId(@Param("analysisId") UUID analysisId);
}

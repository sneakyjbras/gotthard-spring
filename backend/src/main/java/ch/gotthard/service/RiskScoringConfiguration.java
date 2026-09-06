package ch.gotthard.service;

import ch.gotthard.core.risk.RiskScorer;
import ch.gotthard.core.risk.rules.NearThresholdStructuringRule;
import ch.gotthard.core.risk.rules.StandardRules;
import ch.gotthard.domain.query.NearThresholdBand;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Where the pure core is handed to Spring.
 *
 * <p>{@link RiskScorer} and its rules have no annotations and no idea a container exists — that is
 * the point of them. Something still has to construct them, and doing it here keeps the wiring in
 * one visible place rather than hidden inside whichever use case happened to need it first.
 */
@Configuration(proxyBeanMethods = false)
public class RiskScoringConfiguration {

    @Bean
    RiskScorer riskScorer() {
        return new RiskScorer(StandardRules.all());
    }

    /**
     * The band the window query counts as near-threshold, anchored to the figure the structuring
     * rule actually compares against. Stating it once means the query and the rule cannot drift into
     * disagreeing about where the reporting line sits.
     */
    @Bean
    NearThresholdBand nearThresholdBand() {
        return NearThresholdBand.below(NearThresholdStructuringRule.MIN_AGGREGATE_VOLUME);
    }
}

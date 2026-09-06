package ch.gotthard.service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The wallet watch list.
 *
 * <p>Stands in for what a compliance function actually subscribes to — sanctions designations,
 * mixer and ransomware attributions, addresses named in an investigation. Those arrive from a feed
 * and change weekly, so this is configuration rather than a constant in code or a table nobody
 * defined: {@code gotthard.risk.flagged-wallets}, comma separated.
 *
 * <p>Empty by default, and empty means the proximity rule is inert rather than wrong. An empty watch
 * list is a true statement about a system nobody has configured a feed into; inventing addresses so
 * that a rule has something to fire on would not be.
 */
@Component
public class FlaggedWallets {

    private final Set<String> addresses;

    public FlaggedWallets(@Value("${gotthard.risk.flagged-wallets:}") final String configured) {
        this.addresses = parse(configured);
    }

    /** The watch list. Empty when no feed is configured. */
    public Set<String> addresses() {
        return addresses;
    }

    private static Set<String> parse(final String configured) {
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(address -> !address.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}

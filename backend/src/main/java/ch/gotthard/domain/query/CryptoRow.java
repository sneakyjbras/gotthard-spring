package ch.gotthard.domain.query;

import java.math.BigDecimal;

/**
 * What the wallet ledger says about a crypto transfer. Empty on a card or payment row.
 *
 * <p>The wallet windows are partitioned by wallet address, not by customer: a wallet's history is a
 * fact about the chain, and the money that arrived at it may well have come from somebody else's
 * transaction.
 *
 * @param destinationIsExchange the receiving wallet belongs to a known exchange
 * @param sinceInboundFundingSeconds gap between funds last arriving at the sending wallet and this
 *     transfer leaving it, null when no inbound funding was ever observed
 * @param outboundVolume24h what left the sending wallet over the last twenty-four hours
 * @param sendingWallet the address this transfer left, so the wallet graph can be walked from it
 */
public record CryptoRow(
        boolean destinationIsExchange,
        Long sinceInboundFundingSeconds,
        BigDecimal outboundVolume24h,
        String sendingWallet) {}

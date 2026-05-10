package net.enelson.sopparty.api;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Backend view of party state synchronized from the Velocity authority.
 * Other plugins obtain it via {@link SopPartyServices#get()}.
 */
public interface SopPartyApi {

    /**
     * UUIDs to move or queue together (includes {@code player}). If the player is not in a party on this
     * backend, returns a one-element list containing only that player.
     */
    List<UUID> getMemberUuids(Player player);

    Optional<UUID> getPartyId(Player player);

    Optional<UUID> getLeaderUuid(Player player);

    /** {@code true} if this backend has a synced party for the player (may be size 1). */
    boolean isInParty(Player player);

    boolean isLeader(Player player);

    /**
     * Match / queue reservation key synced from the proxy for this player's party ({@link Optional#empty()} if unset).
     * Game plugins coordinate the string format.
     */
    Optional<String> getPartyReservationGameKey(Player player);

    /**
     * Fire-and-forget: sends a reservation update to Velocity. Empty or blank {@code reservationKeyOrBlank} clears.
     * Only the party leader succeeds; Velocity sends feedback messages to leaders and failed actors.
     */
    boolean sendReservationRequest(Player player, String reservationKeyOrBlank);
}

package net.enelson.sopparty.bukkit;

import net.enelson.sopparty.protocol.PartyProtocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player view of party membership derived from proxy snapshots.
 */
final class PartyMemberCache {

    private final Map<UUID, String> reservationsByPartyId = new ConcurrentHashMap<UUID, String>();

    static final class PartyView {
        final UUID partyId;
        final UUID leaderId;
        final List<UUID> members;

        PartyView(UUID partyId, UUID leaderId, List<UUID> members) {
            this.partyId = partyId;
            this.leaderId = leaderId;
            this.members = members;
        }
    }

    private final Map<UUID, PartyView> viewByPlayer = new ConcurrentHashMap<UUID, PartyView>();

    void applySnapshot(UUID partyId, UUID leaderId, List<UUID> members) {
        for (Iterator<Map.Entry<UUID, PartyView>> it = viewByPlayer.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, PartyView> e = it.next();
            PartyView existing = e.getValue();
            if (existing != null && partyId.equals(existing.partyId) && !members.contains(e.getKey())) {
                it.remove();
            }
        }
        List<UUID> copy = Collections.unmodifiableList(new ArrayList<UUID>(members));
        PartyView view = new PartyView(partyId, leaderId, copy);
        for (UUID m : members) {
            viewByPlayer.put(m, view);
        }
    }

    void clearPlayer(UUID playerId) {
        viewByPlayer.remove(playerId);
    }

    PartyView viewOf(UUID playerId) {
        return viewByPlayer.get(playerId);
    }

    List<UUID> getMemberUuids(UUID playerId) {
        PartyView v = viewByPlayer.get(playerId);
        if (v == null || v.members.isEmpty()) {
            return Collections.singletonList(playerId);
        }
        return new ArrayList<UUID>(v.members);
    }

    Optional<UUID> partyIdOf(UUID playerId) {
        PartyView v = viewByPlayer.get(playerId);
        return v != null ? Optional.of(v.partyId) : Optional.empty();
    }

    Optional<UUID> leaderOf(UUID playerId) {
        PartyView v = viewByPlayer.get(playerId);
        return v != null ? Optional.of(v.leaderId) : Optional.empty();
    }

    boolean isInParty(UUID playerId) {
        return viewByPlayer.containsKey(playerId);
    }

    boolean isLeader(UUID playerId) {
        PartyView v = viewByPlayer.get(playerId);
        return v != null && v.leaderId.equals(playerId);
    }

    /**
     * Authoritative reservation key for this party UUID on the proxy ( Velocity sync ).
     */
    void applyReservation(PartyProtocol.DecodedPartyReservation d) {
        if (!d.hasReservation || d.gameKey == null || d.gameKey.isBlank()) {
            reservationsByPartyId.remove(d.partyId);
            return;
        }
        reservationsByPartyId.put(d.partyId, d.gameKey);
    }

    Optional<String> reservationGameKeyOf(UUID playerId) {
        Optional<UUID> pid = partyIdOf(playerId);
        return pid.flatMap(id -> Optional.ofNullable(reservationsByPartyId.get(id)));
    }
}

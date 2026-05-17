package net.enelson.sopparty.bukkit.event;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

/**
 * Immutable party view after a proxy snapshot was applied to the backend cache.
 */
public final class PartyViewSnapshot {

    private final UUID partyId;
    private final UUID leaderId;
    private final List<UUID> members;

    public PartyViewSnapshot(UUID partyId, UUID leaderId, List<UUID> members) {
        this.partyId = partyId;
        this.leaderId = leaderId;
        this.members = Collections.unmodifiableList(new ArrayList<UUID>(members));
    }

    public UUID getPartyId() {
        return partyId;
    }

    public UUID getLeaderId() {
        return leaderId;
    }

    public List<UUID> getMembers() {
        return members;
    }
}

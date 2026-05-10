package net.enelson.sopparty.bukkit.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Fired on the server thread after SopParty's backend cache updates for {@link #getAffectedPlayerId()}.
 * {@link #getPartyView()} is {@code null} when the player is no longer in a synced party on this backend (solo).
 */
public final class SopPartyCacheSyncEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID affectedPlayerId;
    private final PartyViewSnapshot partyView;

    public SopPartyCacheSyncEvent(UUID affectedPlayerId, @Nullable PartyViewSnapshot partyView) {
        super(false);
        this.affectedPlayerId = affectedPlayerId;
        this.partyView = partyView;
    }

    public UUID getAffectedPlayerId() {
        return affectedPlayerId;
    }

    @Nullable
    public PartyViewSnapshot getPartyView() {
        return partyView;
    }

    public boolean isInPartyOnBackend() {
        return partyView != null;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

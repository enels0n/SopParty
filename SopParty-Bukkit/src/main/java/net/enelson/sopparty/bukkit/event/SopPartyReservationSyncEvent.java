package net.enelson.sopparty.bukkit.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Optional;
import java.util.UUID;

/**
 * Backend cache received a reservation update from Velocity for {@link #getPartyUuid()}.
 * Empty reservation means the slot was cleared for that party id.
 */
public final class SopPartyReservationSyncEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID partyUuid;
    private final Optional<String> reservationGameKey;

    public SopPartyReservationSyncEvent(UUID partyUuid, Optional<String> reservationGameKey) {
        super(false);
        this.partyUuid = partyUuid;
        this.reservationGameKey = reservationGameKey != null ? reservationGameKey : Optional.empty();
    }

    public UUID getPartyUuid() {
        return partyUuid;
    }

    public Optional<String> getReservationGameKey() {
        return reservationGameKey;
    }

    public boolean hasReservation() {
        return reservationGameKey.isPresent();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

package net.enelson.sopparty.bukkit;

import net.enelson.sopparty.bukkit.event.PartyViewSnapshot;
import net.enelson.sopparty.bukkit.event.SopPartyCacheSyncEvent;
import net.enelson.sopparty.bukkit.event.SopPartyReservationSyncEvent;
import net.enelson.sopparty.protocol.PartyProtocol;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

final class PartyPluginMessageListener implements PluginMessageListener {

    private final SopPartyPlugin plugin;
    private final Logger logger;
    private final PartyMemberCache cache;

    PartyPluginMessageListener(SopPartyPlugin plugin, Logger logger, PartyMemberCache cache) {
        this.plugin = plugin;
        this.logger = logger;
        this.cache = cache;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!PartyProtocol.CHANNEL.equals(channel) || message.length == 0) {
            return;
        }
        byte op = message[0];
        Runnable apply = () -> applyMessage(op, message);
        if (Bukkit.isPrimaryThread()) {
            apply.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, apply);
        }
    }

    private void applyMessage(byte op, byte[] message) {
        try {
            if (op == PartyProtocol.P2S_PARTY_SNAPSHOT) {
                PartyProtocol.PartySnapshot snap = PartyProtocol.decodePartySnapshot(message);
                cache.applySnapshot(snap.partyId, snap.leader, snap.members);
                PartyViewSnapshot view = new PartyViewSnapshot(snap.partyId, snap.leader, snap.members);
                for (UUID m : view.getMembers()) {
                    plugin.getServer().getPluginManager().callEvent(new SopPartyCacheSyncEvent(m, view));
                }
            } else if (op == PartyProtocol.P2S_CLEAR_PLAYER) {
                UUID id = PartyProtocol.decodeClearPlayer(message);
                cache.clearPlayer(id);
                plugin.getServer().getPluginManager().callEvent(new SopPartyCacheSyncEvent(id, null));
            } else if (op == PartyProtocol.P2S_PARTY_RESERVATION) {
                PartyProtocol.DecodedPartyReservation r = PartyProtocol.decodePartyReservation(message);
                cache.applyReservation(r);
                Optional<String> key = r.hasReservation && r.gameKey != null && !r.gameKey.trim().isEmpty()
                        ? Optional.of(r.gameKey)
                        : Optional.empty();
                plugin.getServer()
                        .getPluginManager()
                        .callEvent(new SopPartyReservationSyncEvent(r.partyId, key));
            }
        } catch (IOException e) {
            logger.warning("SopParty decode error: " + e.getMessage());
        }
    }
}

package net.enelson.sopparty.bukkit;

import net.enelson.sopparty.protocol.PartyProtocol;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.IOException;
import java.util.logging.Logger;

final class PartyPluginMessageListener implements PluginMessageListener {

    private final SopPartyPlugin plugin;
    private final Logger logger;
    private final PartyMemberCache cache;
    private final ProxyOnlinePlayerDirectory onlinePlayerDirectory;

    PartyPluginMessageListener(
            SopPartyPlugin plugin,
            Logger logger,
            PartyMemberCache cache,
            ProxyOnlinePlayerDirectory onlinePlayerDirectory) {
        this.plugin = plugin;
        this.logger = logger;
        this.cache = cache;
        this.onlinePlayerDirectory = onlinePlayerDirectory;
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
            if (!plugin.handleIncomingProxyPacket()) {
                return;
            }
            if (op == PartyProtocol.P2S_PARTY_SNAPSHOT) {
                PartyProtocol.PartySnapshot snap = PartyProtocol.decodePartySnapshot(message);
                plugin.applyPartySnapshot(snap.partyId, snap.leader, snap.members);
            } else if (op == PartyProtocol.P2S_CLEAR_PLAYER) {
                plugin.clearPartyState(PartyProtocol.decodeClearPlayer(message));
            } else if (op == PartyProtocol.P2S_PARTY_RESERVATION) {
                PartyProtocol.DecodedPartyReservation r = PartyProtocol.decodePartyReservation(message);
                plugin.applyReservationState(r.partyId, r.hasReservation ? r.gameKey : null);
            } else if (op == PartyProtocol.P2S_ONLINE_PLAYERS) {
                onlinePlayerDirectory.replaceAll(PartyProtocol.decodeOnlinePlayers(message));
            } else if (op == PartyProtocol.P2S_BACKEND_MESSAGE) {
                PartyProtocol.DecodedBackendMessage backendMessage = PartyProtocol.decodeBackendMessage(message);
                plugin.sendBackendPartyMessage(backendMessage.recipient, backendMessage.rawAmpersandMessage);
            }
        } catch (IOException e) {
            logger.warning("SopParty decode error: " + e.getMessage());
        }
    }
}

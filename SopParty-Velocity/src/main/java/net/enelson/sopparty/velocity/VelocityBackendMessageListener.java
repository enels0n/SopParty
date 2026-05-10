package net.enelson.sopparty.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.enelson.sopparty.protocol.PartyProtocol;
import org.slf4j.Logger;

import java.io.IOException;

final class VelocityBackendMessageListener {

    private final Logger logger;
    private final VelocityPartyService parties;
    private final MinecraftChannelIdentifier channelId;

    VelocityBackendMessageListener(Logger logger, VelocityPartyService parties) {
        this.logger = logger;
        this.parties = parties;
        this.channelId = MinecraftChannelIdentifier.from(PartyProtocol.CHANNEL);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(channelId)) {
            return;
        }
        if (!(event.getSource() instanceof com.velocitypowered.api.proxy.ServerConnection)) {
            return;
        }
        byte[] data = event.getData();
        if (data == null || data.length == 0) {
            return;
        }
        try {
            byte op = data[0];
            if (op == PartyProtocol.C2P_SYNC_REQUEST) {
                parties.handleSyncRequest(PartyProtocol.decodeSyncRequest(data));
            } else if (op == PartyProtocol.C2P_ACTION) {
                parties.handleAction(PartyProtocol.decodeAction(data));
            } else if (op == PartyProtocol.C2P_PARTY_RESERVE) {
                PartyProtocol.DecodedPartyReserve rsv = PartyProtocol.decodePartyReserve(data);
                parties.handlePartyReserve(rsv.actor, rsv.gameKey);
            }
        } catch (IOException e) {
            logger.warn("Bad SopParty packet from backend: {}", e.toString());
        }
    }
}

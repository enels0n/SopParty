package net.enelson.sopparty.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Optional;

/**
 * Blocks manual server switches for non-leader party members when a reservation is active.
 */
final class VelocityPartyPreConnectListener {

    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();

    private final VelocityPartyService parties;

    VelocityPartyPreConnectListener(VelocityPartyService parties) {
        this.parties = parties;
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        RegisteredServer targetServer = event.getResult().getServer().orElse(event.getOriginalServer());
        String targetName = targetServer.getServerInfo().getName();
        Optional<String> deny = parties.serverSwitchBlockReason(event.getPlayer().getUniqueId(), targetName);
        if (!deny.isPresent()) {
            return;
        }
        event.getPlayer().sendMessage(AMP.deserialize(deny.get()));
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
    }
}

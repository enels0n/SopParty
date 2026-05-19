package net.enelson.sopparty.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;

/**
 * Keeps authoritative party state clean on player disconnects.
 */
final class VelocityPartyConnectionListener {

    private final VelocityPartyService parties;

    VelocityPartyConnectionListener(VelocityPartyService parties) {
        this.parties = parties;
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        parties.broadcastOnlinePlayers();
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        parties.handleDisconnect(event.getPlayer().getUniqueId());
        parties.broadcastOnlinePlayers();
    }
}

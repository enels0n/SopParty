package net.enelson.sopparty.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;

/**
 * Keeps backend cache fresh and applies leader-follow on proxy server switches.
 */
final class VelocityPartyServerSwitchListener {

    private final VelocityPartyService parties;

    VelocityPartyServerSwitchListener(VelocityPartyService parties) {
        this.parties = parties;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        parties.refreshPartyOnBackend(event.getPlayer().getUniqueId());
        parties.followLeaderToServer(event.getPlayer().getUniqueId(), event.getServer());
    }
}

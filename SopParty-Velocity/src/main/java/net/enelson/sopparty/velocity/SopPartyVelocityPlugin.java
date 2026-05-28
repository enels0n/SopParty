package net.enelson.sopparty.velocity;


import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.enelson.sopparty.protocol.PartyProtocol;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

public final class SopPartyVelocityPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private VelocityPartyService partyService;
    private ScheduledTask invitePruneTask;

    @Inject
    public SopPartyVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        VelocityStartupConfig boot = loadBootConfig();
        partyService = new VelocityPartyService(server, logger, boot.settings(), boot.messages());
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("sopparty").build(),
                new SopPartyVelocityCommand(this));
        server.getChannelRegistrar().register(
                com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier.from(PartyProtocol.CHANNEL));
        server.getEventManager().register(this, new VelocityBackendMessageListener(logger, this));
        server.getEventManager().register(this, new VelocityPartyServerSwitchListener(partyService));
        server.getEventManager().register(this, new VelocityPartyConnectionListener(partyService));
        server.getEventManager().register(this, new VelocityPartyPreConnectListener(partyService));
        scheduleInvitePrune(boot.settings());
        logger.info("SopParty-Velocity enabled (maxParty={}, inviteTtl={}s, pruneEvery={}s).",
                boot.settings().maxPartySize(), boot.settings().inviteTtlSeconds(), boot.settings().invitePruneIntervalSeconds());
    }

    void reloadFromBackend(UUID actorUuid) {
        if (partyService == null) {
            return;
        }
        if (!server.getPlayer(actorUuid).map(player -> player.hasPermission("sopparty.admin.reload")).orElse(false)) {
            partyService.sendSystemMessage(actorUuid, new VelocityPartyMessages(new Properties()).reloadNoPermission());
            return;
        }

        boolean ok = reloadDirect();
        if (!ok) {
            partyService.sendSystemMessage(actorUuid, new VelocityPartyMessages(new Properties()).reloadFailure());
            return;
        }
        partyService.sendSystemMessage(actorUuid, new VelocityPartyMessages(new Properties()).reloadSuccess(partyService.getMaxPartySize()));
    }

    boolean reloadDirect() {
        try {
            VelocityStartupConfig boot = VelocityPartyConfigLoader.load(dataDirectory, logger);
            partyService.reloadConfiguration(boot.settings(), boot.messages());
            scheduleInvitePrune(boot.settings());
            logger.info("SopParty-Velocity reloaded (maxParty={}, inviteTtl={}s, pruneEvery={}s).",
                    boot.settings().maxPartySize(), boot.settings().inviteTtlSeconds(), boot.settings().invitePruneIntervalSeconds());
            return true;
        } catch (IOException e) {
            logger.error("Failed to reload SopParty config.", e);
            return false;
        }
    }

    private VelocityStartupConfig loadBootConfig() {
        try {
            return VelocityPartyConfigLoader.load(dataDirectory, logger);
        } catch (IOException e) {
            logger.error("Failed to load SopParty config; using built-in defaults.", e);
            VelocityPartySettings vs = new VelocityPartySettings(
                    VelocityPartySettings.DEFAULT_MAX_PARTY_SIZE,
                    VelocityPartySettings.DEFAULT_INVITE_TTL_SECONDS,
                    VelocityPartySettings.DEFAULT_INVITE_PRUNE_INTERVAL_SECONDS);
            VelocityPartyMessages vm = new VelocityPartyMessages(new Properties());
            return new VelocityStartupConfig(vs, vm);
        }
    }

    private void scheduleInvitePrune(VelocityPartySettings settings) {
        if (invitePruneTask != null) {
            invitePruneTask.cancel();
        }
        invitePruneTask = server.getScheduler()
                .buildTask(this, partyService::pruneInvites)
                .repeat(Duration.ofSeconds(settings.invitePruneIntervalSeconds()))
                .schedule();
    }

    public ProxyServer getServer() {
        return server;
    }

    public VelocityPartyService getPartyService() {
        return partyService;
    }

    public Logger getLogger() {
        return logger;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }
}

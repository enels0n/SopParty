package net.enelson.sopparty.velocity;


import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.enelson.sopparty.protocol.PartyProtocol;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

public final class SopPartyVelocityPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private VelocityPartyService partyService;

    @Inject
    public SopPartyVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        VelocityStartupConfig boot;
        try {
            boot = VelocityPartyConfigLoader.load(dataDirectory, logger);
        } catch (IOException e) {
            logger.error("Failed to load SopParty config; using built-in defaults.", e);
            VelocityPartySettings vs = new VelocityPartySettings(8, 120, 30);
            VelocityPartyMessages vm = new VelocityPartyMessages(new Properties());
            boot = new VelocityStartupConfig(vs, vm);
        }
        partyService = new VelocityPartyService(server, logger, boot.settings(), boot.messages());
        server.getChannelRegistrar().register(
                com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier.from(PartyProtocol.CHANNEL));
        server.getEventManager().register(this, new VelocityBackendMessageListener(logger, partyService));
        server.getEventManager().register(this, new VelocityPartyServerSwitchListener(partyService));
        server.getEventManager().register(this, new VelocityPartyConnectionListener(partyService));
        server.getEventManager().register(this, new VelocityPartyPreConnectListener(partyService));
        server.getScheduler()
                .buildTask(this, partyService::pruneInvites)
                .repeat(Duration.ofSeconds(boot.settings().invitePruneIntervalSeconds()))
                .schedule();
        logger.info("SopParty-Velocity enabled (maxParty={}, inviteTtl={}s, pruneEvery={}s).",
                boot.settings().maxPartySize(), boot.settings().inviteTtlSeconds(), boot.settings().invitePruneIntervalSeconds());
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

package net.enelson.sopparty.bukkit;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

final class SopPartyPaperConfig {

    private long syncDelayTicks;
    private long proxyDetectionTimeoutTicks;
    private int standaloneMaxPartySize;
    private int standaloneInviteTtlSeconds;
    private String msgCommandPlayersOnly;
    private String msgPartyHelp;
    private String msgUsageInvite;
    private String msgUsageKick;
    private String msgUsageReload;
    private String msgUsageSopPartyReload;
    private String msgUsageTransfer;
    private String msgNoPermission;
    private String msgReloadUseProxyConsole;
    private String msgUnknownSubcommand;
    private String msgProtocolError;
    private String reloadPermission;
    private int tabCompleteMinPrefixLength;
    private int tabCompleteMaxResults;

    void load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        syncDelayTicks = Math.max(0L, c.getLong("party-sync-delay-ticks", 0L));
        proxyDetectionTimeoutTicks = Math.max(1L, c.getLong("proxy-detection-timeout-ticks", 40L));
        standaloneMaxPartySize = clamp(c.getInt("standalone.max-party-size", 8), 2, 64);
        standaloneInviteTtlSeconds = clamp(c.getInt("standalone.invite-ttl-seconds", 120), 5, 3600);

        msgCommandPlayersOnly = str(c, "messages.command-players-only", "&cPlayers only.");
        msgPartyHelp = str(c, "messages.party-help",
                "&e/party create | invite | accept | deny | leave | kick | disband | transfer | list | reload");
        msgUsageInvite = str(c, "messages.usage-invite", "&cUsage: /party invite <player>");
        msgUsageKick = str(c, "messages.usage-kick", "&cUsage: /party kick <player>");
        msgUsageReload = str(c, "messages.usage-reload", "&cUsage: /party reload");
        msgUsageSopPartyReload = str(c, "messages.usage-sopparty-reload", "&cUsage: /sopparty reload");
        msgUsageTransfer = str(c, "messages.usage-transfer", "&cUsage: /party transfer <player>");
        msgNoPermission = str(c, "messages.no-permission", "&cYou do not have permission.");
        msgReloadUseProxyConsole = str(c, "messages.reload-use-proxy-console",
                "&eVelocity mode is active. Run /sopparty reload in the Velocity console.");
        msgUnknownSubcommand = str(c, "messages.unknown-subcommand", "&cUnknown subcommand.");
        msgProtocolError = str(c, "messages.protocol-error", "&cProtocol error.");
        reloadPermission = str(c, "permissions.reload", "sopparty.admin.reload");
        tabCompleteMinPrefixLength = Math.max(0, c.getInt("tab-complete.min-prefix-length", 2));
        tabCompleteMaxResults = Math.max(1, c.getInt("tab-complete.max-results", 20));
    }

    private static String str(FileConfiguration c, String path, String def) {
        String v = c.getString(path, def);
        return v == null ? def : v;
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    long syncDelayTicks() {
        return syncDelayTicks;
    }

    long proxyDetectionTimeoutTicks() {
        return proxyDetectionTimeoutTicks;
    }

    int standaloneMaxPartySize() {
        return standaloneMaxPartySize;
    }

    int standaloneInviteTtlSeconds() {
        return standaloneInviteTtlSeconds;
    }

    String messageCommandPlayersOnly() {
        return msgCommandPlayersOnly;
    }

    String messagePartyHelp() {
        return msgPartyHelp;
    }

    String messageUsageInvite() {
        return msgUsageInvite;
    }

    String messageUsageKick() {
        return msgUsageKick;
    }

    String messageUsageReload() {
        return msgUsageReload;
    }

    String messageUsageSopPartyReload() {
        return msgUsageSopPartyReload;
    }

    String messageUsageTransfer() {
        return msgUsageTransfer;
    }

    String messageNoPermission() {
        return msgNoPermission;
    }

    String messageReloadUseProxyConsole() {
        return msgReloadUseProxyConsole;
    }

    String messageUnknownSubcommand() {
        return msgUnknownSubcommand;
    }

    String messageProtocolError() {
        return msgProtocolError;
    }

    String reloadPermission() {
        return reloadPermission;
    }

    int tabCompleteMinPrefixLength() {
        return tabCompleteMinPrefixLength;
    }

    int tabCompleteMaxResults() {
        return tabCompleteMaxResults;
    }
}

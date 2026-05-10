package net.enelson.sopparty.bukkit;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

final class SopPartyPaperConfig {

    private long syncDelayTicks;
    private String msgCommandPlayersOnly;
    private String msgPartyHelp;
    private String msgUsageInvite;
    private String msgUsageKick;
    private String msgUsageTransfer;
    private String msgUnknownSubcommand;
    private String msgProtocolError;

    void load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        syncDelayTicks = Math.max(0L, c.getLong("party-sync-delay-ticks", 0L));

        msgCommandPlayersOnly = str(c, "messages.command-players-only", "&cPlayers only.");
        msgPartyHelp = str(c, "messages.party-help",
                "&e/party create | invite | accept | deny | leave | kick | disband | transfer | list");
        msgUsageInvite = str(c, "messages.usage-invite", "&cUsage: /party invite <player>");
        msgUsageKick = str(c, "messages.usage-kick", "&cUsage: /party kick <player>");
        msgUsageTransfer = str(c, "messages.usage-transfer", "&cUsage: /party transfer <player>");
        msgUnknownSubcommand = str(c, "messages.unknown-subcommand", "&cUnknown subcommand.");
        msgProtocolError = str(c, "messages.protocol-error", "&cProtocol error.");
    }

    private static String str(FileConfiguration c, String path, String def) {
        String v = c.getString(path, def);
        return v == null ? def : v;
    }

    long syncDelayTicks() {
        return syncDelayTicks;
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

    String messageUsageTransfer() {
        return msgUsageTransfer;
    }

    String messageUnknownSubcommand() {
        return msgUnknownSubcommand;
    }

    String messageProtocolError() {
        return msgProtocolError;
    }
}

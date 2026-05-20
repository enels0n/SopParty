package net.enelson.sopparty.bukkit;
import net.enelson.sopparty.api.SopPartyApi;
import net.enelson.sopparty.protocol.PartyProtocol;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SopPartyPlugin extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();
    private Method placeholderApiSetPlaceholders;

    private final SopPartyPaperConfig paperConfig = new SopPartyPaperConfig();
    private final PartyMemberCache memberCache = new PartyMemberCache();
    private final ProxyOnlinePlayerDirectory onlinePlayerDirectory = new ProxyOnlinePlayerDirectory();
    private final SopPartyApi partyApi = new DefaultSopPartyApi(this, memberCache);
    private final PartyPluginMessageListener incoming =
            new PartyPluginMessageListener(this, getLogger(), memberCache, onlinePlayerDirectory);

    @Override
    public void onEnable() {
        paperConfig.load(this);
        hookPlaceholderApi();

        getServer().getServicesManager().register(SopPartyApi.class, partyApi, this, ServicePriority.Normal);

        getServer().getMessenger().registerOutgoingPluginChannel(this, PartyProtocol.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, PartyProtocol.CHANNEL, incoming);

        if (getCommand("party") != null) {
            getCommand("party").setExecutor(this);
            getCommand("party").setTabCompleter(this);
        }

        getServer().getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTask(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                syncFromProxy(p);
            }
        });
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);

        getServer().getMessenger().unregisterIncomingPluginChannel(this, PartyProtocol.CHANNEL, incoming);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, PartyProtocol.CHANNEL);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        syncFromProxy(player);
        long followUp = paperConfig.syncDelayTicks();
        if (followUp > 0) {
            Bukkit.getScheduler().runTaskLater(this, () -> syncFromProxy(player), followUp);
        }
    }

    void sendAmpersandConfigured(CommandSender sender, String ampersandConfigured) {
        if (sender instanceof Player) {
            Player p = (Player) sender;
            p.sendMessage(sectionText(AMP.deserialize(applyPlayerPlaceholders(p, ampersandConfigured))));
            return;
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', ampersandConfigured));
    }

    void sendBackendPartyMessage(UUID recipient, String rawAmpersandMessage) {
        Player player = Bukkit.getPlayer(recipient);
        if (player == null || !player.isOnline()) {
            return;
        }
        sendAmpersandConfigured(player, rawAmpersandMessage);
    }

    private String applyPlayerPlaceholders(Player player, String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        if (placeholderApiSetPlaceholders == null || Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return input;
        }
        try {
            Object resolved = placeholderApiSetPlaceholders.invoke(null, player, input);
            return resolved instanceof String ? (String) resolved : input;
        } catch (Throwable ignored) {
            return input;
        }
    }

    private void hookPlaceholderApi() {
        try {
            Class<?> apiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            placeholderApiSetPlaceholders = apiClass.getMethod("setPlaceholders", Player.class, String.class);
        } catch (Throwable ignored) {
            placeholderApiSetPlaceholders = null;
        }
    }

    private static String sectionText(Component component) {
        return SECTION.serialize(component);
    }

    private void syncFromProxy(Player player) {
        if (!player.isOnline()) {
            return;
        }
        try {
            byte[] payload = PartyProtocol.encodeSyncRequest(player.getUniqueId());
            player.sendPluginMessage(this, PartyProtocol.CHANNEL, payload);
        } catch (IOException e) {
            getLogger().warning("Sync encode failed: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("party")) {
            return false;
        }
        if (!(sender instanceof Player)) {
            sendAmpersandConfigured(sender, paperConfig.messageCommandPlayersOnly());
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            sendAmpersandConfigured(player, paperConfig.messagePartyHelp());
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "create":
                    sendAction(player, PartyProtocol.Action.CREATE, null, null);
                    break;
                case "invite":
                    if (args.length < 2) {
                        sendAmpersandConfigured(player, paperConfig.messageUsageInvite());
                        return true;
                    }
                    sendAction(player, PartyProtocol.Action.INVITE, args[1], null);
                    break;
                case "accept": {
                    UUID inviter = null;
                    if (args.length >= 2) {
                        Player target = Bukkit.getPlayerExact(args[1]);
                        if (target != null) {
                            inviter = target.getUniqueId();
                        }
                    }
                    sendAction(player, PartyProtocol.Action.ACCEPT, null, inviter);
                    break;
                }
                case "deny": {
                    UUID inviter = null;
                    if (args.length >= 2) {
                        Player target = Bukkit.getPlayerExact(args[1]);
                        if (target != null) {
                            inviter = target.getUniqueId();
                        }
                    }
                    sendAction(player, PartyProtocol.Action.DENY, null, inviter);
                    break;
                }
                case "leave":
                    sendAction(player, PartyProtocol.Action.LEAVE, null, null);
                    break;
                case "disband":
                    sendAction(player, PartyProtocol.Action.DISBAND, null, null);
                    break;
                case "kick":
                    if (args.length < 2) {
                        sendAmpersandConfigured(player, paperConfig.messageUsageKick());
                        return true;
                    }
                    sendAction(player, PartyProtocol.Action.KICK, args[1], null);
                    break;
                case "transfer":
                    if (args.length < 2) {
                        sendAmpersandConfigured(player, paperConfig.messageUsageTransfer());
                        return true;
                    }
                    sendAction(player, PartyProtocol.Action.TRANSFER, args[1], null);
                    break;
                case "list":
                    sendAction(player, PartyProtocol.Action.LIST, null, null);
                    break;
                default:
                    sendAmpersandConfigured(player, paperConfig.messageUnknownSubcommand());
            }
        } catch (IOException e) {
            sendAmpersandConfigured(player, paperConfig.messageProtocolError());
            getLogger().warning(e.getMessage());
        }
        return true;
    }

    private void sendAction(Player player, PartyProtocol.Action action, String text, UUID uuidArg) throws IOException {
        byte[] payload = PartyProtocol.encodeAction(player.getUniqueId(), action, text, uuidArg);
        player.sendPluginMessage(this, PartyProtocol.CHANNEL, payload);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !command.getName().equalsIgnoreCase("party")) {
            return Collections.emptyList();
        }
        Player player = (Player) sender;
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<String>();
            for (String s : new String[]{"accept", "create", "deny", "disband", "invite", "kick", "leave", "list", "transfer"}) {
                if (s.startsWith(prefix)) {
                    out.add(s);
                }
            }
            return out;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if (prefix.length() < paperConfig.tabCompleteMinPrefixLength()) {
                return Collections.emptyList();
            }
            if ("invite".equals(sub) || "accept".equals(sub) || "deny".equals(sub)) {
                return suggestVisiblePlayers(player, prefix);
            }
            if ("kick".equals(sub) || "transfer".equals(sub)) {
                return suggestPartyMembers(player, prefix);
            }
        }
        return Collections.emptyList();
    }

    private List<String> suggestVisiblePlayers(Player actor, String prefixLower) {
        int limit = paperConfig.tabCompleteMaxResults();
        if (onlinePlayerDirectory.hasSnapshot()) {
            return onlinePlayerDirectory.suggest(prefixLower, limit, actor.getUniqueId());
        }
        List<String> out = new ArrayList<String>();
        List<Player> players = new ArrayList<Player>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        for (Player online : players) {
            if (online.getUniqueId().equals(actor.getUniqueId())) {
                continue;
            }
            String name = online.getName();
            if (!name.toLowerCase(Locale.ROOT).startsWith(prefixLower)) {
                continue;
            }
            out.add(name);
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    private List<String> suggestPartyMembers(Player actor, String prefixLower) {
        int limit = paperConfig.tabCompleteMaxResults();
        List<String> out = new ArrayList<String>();
        for (UUID memberId : memberCache.getMemberUuids(actor.getUniqueId())) {
            if (memberId.equals(actor.getUniqueId())) {
                continue;
            }
            String name = resolveKnownPlayerName(memberId);
            if (name == null || !name.toLowerCase(Locale.ROOT).startsWith(prefixLower)) {
                continue;
            }
            out.add(name);
            if (out.size() >= limit) {
                break;
            }
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private String resolveKnownPlayerName(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        return onlinePlayerDirectory.nameOf(playerId);
    }
}

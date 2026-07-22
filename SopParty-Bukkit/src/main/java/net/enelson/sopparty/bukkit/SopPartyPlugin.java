package net.enelson.sopparty.bukkit;

import org.bstats.bukkit.Metrics;
import net.enelson.sopparty.api.SopPartyApi;
import net.enelson.sopparty.bukkit.event.PartyViewSnapshot;
import net.enelson.sopparty.bukkit.event.SopPartyCacheSyncEvent;
import net.enelson.sopparty.bukkit.event.SopPartyReservationSyncEvent;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class SopPartyPlugin extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    private static final int BSTATS_PLUGIN_ID = 32809;

    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    private Method placeholderApiSetPlaceholders;

    private final SopPartyPaperConfig paperConfig = new SopPartyPaperConfig();
    private final PartyMemberCache memberCache = new PartyMemberCache();
    private final ProxyOnlinePlayerDirectory onlinePlayerDirectory = new ProxyOnlinePlayerDirectory();
    private final SopPartyApi partyApi = new DefaultSopPartyApi(this, memberCache);
    private final PartyPluginMessageListener incoming =
            new PartyPluginMessageListener(this, getLogger(), memberCache, onlinePlayerDirectory);
    private final StandalonePartyService standaloneParties = new StandalonePartyService(this, memberCache);

    private volatile PartyOperatingMode operatingMode = PartyOperatingMode.UNDECIDED;
    private volatile long firstProxyProbeAtMillis = -1L;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        paperConfig.load(this);
        standaloneParties.reloadSettings();
        hookPlaceholderApi();
        startMetricsIfConfigured();

        getServer().getServicesManager().register(SopPartyApi.class, partyApi, this, ServicePriority.Normal);
        getServer().getMessenger().registerOutgoingPluginChannel(this, PartyProtocol.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, PartyProtocol.CHANNEL, incoming);

        if (getCommand("party") != null) {
            getCommand("party").setExecutor(this);
            getCommand("party").setTabCompleter(this);
        }
        if (getCommand("sopparty") != null) {
            getCommand("sopparty").setExecutor(this);
        }

        getServer().getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTask(this, new Runnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    probeProxy(player);
                }
            }
        });
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                runModeDetection();
            }
        }, 20L, 20L);
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
        if (operatingMode == PartyOperatingMode.STANDALONE) {
            standaloneParties.handleJoin(player);
            return;
        }
        probeProxy(player);
        long followUp = paperConfig.syncDelayTicks();
        if (followUp > 0L) {
            Bukkit.getScheduler().runTaskLater(this, new Runnable() {
                @Override
                public void run() {
                    if (operatingMode != PartyOperatingMode.STANDALONE) {
                        probeProxy(player);
                    }
                }
            }, followUp);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (operatingMode == PartyOperatingMode.STANDALONE) {
            standaloneParties.handleQuit(event.getPlayer());
        }
    }

    boolean handleIncomingProxyPacket() {
        switchToProxyModeIfNeeded();
        return true;
    }

    void applyPartySnapshot(UUID partyId, UUID leaderId, List<UUID> members) {
        memberCache.applySnapshot(partyId, leaderId, members);
        PartyViewSnapshot view = new PartyViewSnapshot(partyId, leaderId, members);
        for (UUID memberId : view.getMembers()) {
            getServer().getPluginManager().callEvent(new SopPartyCacheSyncEvent(memberId, view));
        }
    }

    void clearPartyState(UUID playerId) {
        memberCache.clearPlayer(playerId);
        getServer().getPluginManager().callEvent(new SopPartyCacheSyncEvent(playerId, null));
    }

    void applyReservationState(UUID partyId, String reservationKey) {
        memberCache.setReservation(partyId, reservationKey);
        Optional<String> key = reservationKey != null && !reservationKey.trim().isEmpty()
                ? Optional.of(reservationKey)
                : Optional.<String>empty();
        getServer().getPluginManager().callEvent(new SopPartyReservationSyncEvent(partyId, key));
    }

    SopPartyPaperConfig paperConfig() {
        return paperConfig;
    }

    String getStandaloneMessage(String key, String def) {
        String value = getConfig().getString("messages." + key, def);
        return value == null ? def : value;
    }

    String resolveLocalPlayerName(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        String proxyName = onlinePlayerDirectory.nameOf(playerId);
        return proxyName != null ? proxyName : playerId.toString().substring(0, 8);
    }

    void sendMessageToPlayer(UUID recipient, String rawAmpersandMessage) {
        Player player = Bukkit.getPlayer(recipient);
        if (player != null && player.isOnline()) {
            sendAmpersandConfigured(player, rawAmpersandMessage);
        }
    }

    boolean sendReservationRequest(Player player, String reservationKeyOrBlank) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (operatingMode == PartyOperatingMode.STANDALONE) {
            return standaloneParties.handleReservationRequest(player, reservationKeyOrBlank);
        }
        if (operatingMode == PartyOperatingMode.UNDECIDED) {
            probeProxy(player);
            if (!activateStandaloneIfTimedOut()) {
                return false;
            }
            return standaloneParties.handleReservationRequest(player, reservationKeyOrBlank);
        }
        String key = reservationKeyOrBlank != null ? reservationKeyOrBlank.trim() : "";
        try {
            byte[] payload = PartyProtocol.encodePartyReserve(player.getUniqueId(), key);
            player.sendPluginMessage(this, PartyProtocol.CHANNEL, payload);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    void sendAmpersandConfigured(CommandSender sender, String ampersandConfigured) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            player.sendMessage(sectionText(AMP.deserialize(applyPlayerPlaceholders(player, ampersandConfigured))));
            return;
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', ampersandConfigured));
    }

    void sendBackendPartyMessage(UUID recipient, String rawAmpersandMessage) {
        sendMessageToPlayer(recipient, rawAmpersandMessage);
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

    private void runModeDetection() {
        if (operatingMode != PartyOperatingMode.UNDECIDED) {
            return;
        }
        boolean hasOnlinePlayers = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            hasOnlinePlayers = true;
            probeProxy(player);
        }
        if (!hasOnlinePlayers) {
            return;
        }
        activateStandaloneIfTimedOut();
    }

    private void probeProxy(Player player) {
        if (player == null || !player.isOnline() || operatingMode == PartyOperatingMode.STANDALONE) {
            return;
        }
        if (firstProxyProbeAtMillis < 0L) {
            firstProxyProbeAtMillis = System.currentTimeMillis();
        }
        try {
            byte[] payload = PartyProtocol.encodeSyncRequest(player.getUniqueId());
            player.sendPluginMessage(this, PartyProtocol.CHANNEL, payload);
        } catch (IOException e) {
            getLogger().warning("Sync encode failed: " + e.getMessage());
        }
    }

    private boolean activateStandaloneIfTimedOut() {
        if (operatingMode != PartyOperatingMode.UNDECIDED || firstProxyProbeAtMillis < 0L) {
            return operatingMode == PartyOperatingMode.STANDALONE;
        }
        long elapsedMs = System.currentTimeMillis() - firstProxyProbeAtMillis;
        long timeoutMs = paperConfig.proxyDetectionTimeoutTicks() * 50L;
        if (elapsedMs < timeoutMs) {
            return false;
        }
        operatingMode = PartyOperatingMode.STANDALONE;
        getLogger().info("SopParty did not receive a proxy response in time; using standalone mode.");
        for (Player player : Bukkit.getOnlinePlayers()) {
            standaloneParties.handleJoin(player);
        }
        return true;
    }

    private void switchToProxyModeIfNeeded() {
        if (operatingMode == PartyOperatingMode.PROXY) {
            return;
        }
        boolean wasStandalone = operatingMode == PartyOperatingMode.STANDALONE;
        operatingMode = PartyOperatingMode.PROXY;
        firstProxyProbeAtMillis = -1L;
        if (wasStandalone) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                clearPartyState(online.getUniqueId());
            }
            getLogger().info("SopParty received a late Velocity response; switching from standalone to proxy-authoritative mode.");
            Bukkit.getScheduler().runTask(this, new Runnable() {
                @Override
                public void run() {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        probeProxy(online);
                    }
                }
            });
            return;
        }
        getLogger().info("SopParty detected Velocity backend; using proxy-authoritative mode.");
    }

    private boolean prepareForCommand(Player player) {
        if (operatingMode == PartyOperatingMode.STANDALONE) {
            return true;
        }
        if (operatingMode == PartyOperatingMode.PROXY) {
            return true;
        }
        probeProxy(player);
        if (activateStandaloneIfTimedOut()) {
            return true;
        }
        sendAmpersandConfigured(player, getStandaloneMessage("proxy-detecting", "&eSopParty is checking for a proxy. Try again in a moment."));
        return false;
    }

    private void reloadLocalSettings() {
        paperConfig.load(this);
        standaloneParties.reloadSettings();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("sopparty")) {
            return handleAdminCommand(sender, args);
        }
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

        if ("reload".equals(sub)) {
            if (args.length > 1) {
                sendAmpersandConfigured(player, paperConfig.messageUsageReload());
                return true;
            }
            if (!player.hasPermission(paperConfig.reloadPermission())) {
                sendAmpersandConfigured(player, paperConfig.messageNoPermission());
                return true;
            }
            reloadLocalSettings();
            if (operatingMode == PartyOperatingMode.STANDALONE || activateStandaloneIfTimedOut()) {
                sendAmpersandConfigured(player,
                        format(getStandaloneMessage("standalone-reload-ok",
                                "&aSopParty standalone config reloaded. Max party size: &f{0}&a."),
                                paperConfig.standaloneMaxPartySize()));
                return true;
            }
            if (operatingMode == PartyOperatingMode.PROXY) {
                try {
                    sendProxyAction(player, PartyProtocol.Action.RELOAD, null, null);
                } catch (IOException e) {
                    sendAmpersandConfigured(player, paperConfig.messageProtocolError());
                    getLogger().warning(e.getMessage());
                }
                return true;
            }
            sendAmpersandConfigured(player, getStandaloneMessage("reload-local-ok", "&aSopParty local config reloaded."));
            return true;
        }

        if (!prepareForCommand(player)) {
            return true;
        }

        UUID inviterUuid = null;
        if (args.length >= 2 && ("accept".equals(sub) || "deny".equals(sub))) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null) {
                inviterUuid = target.getUniqueId();
            }
        }

        if (operatingMode == PartyOperatingMode.STANDALONE) {
            standaloneParties.handleAction(player, sub, args, inviterUuid);
            return true;
        }

        try {
            if ("create".equals(sub)) {
                sendProxyAction(player, PartyProtocol.Action.CREATE, null, null);
            } else if ("invite".equals(sub)) {
                if (args.length < 2) {
                    sendAmpersandConfigured(player, paperConfig.messageUsageInvite());
                    return true;
                }
                sendProxyAction(player, PartyProtocol.Action.INVITE, args[1], null);
            } else if ("accept".equals(sub)) {
                sendProxyAction(player, PartyProtocol.Action.ACCEPT, null, inviterUuid);
            } else if ("deny".equals(sub)) {
                sendProxyAction(player, PartyProtocol.Action.DENY, null, inviterUuid);
            } else if ("leave".equals(sub)) {
                sendProxyAction(player, PartyProtocol.Action.LEAVE, null, null);
            } else if ("disband".equals(sub)) {
                sendProxyAction(player, PartyProtocol.Action.DISBAND, null, null);
            } else if ("kick".equals(sub)) {
                if (args.length < 2) {
                    sendAmpersandConfigured(player, paperConfig.messageUsageKick());
                    return true;
                }
                sendProxyAction(player, PartyProtocol.Action.KICK, args[1], null);
            } else if ("transfer".equals(sub)) {
                if (args.length < 2) {
                    sendAmpersandConfigured(player, paperConfig.messageUsageTransfer());
                    return true;
                }
                sendProxyAction(player, PartyProtocol.Action.TRANSFER, args[1], null);
            } else if ("list".equals(sub)) {
                sendProxyAction(player, PartyProtocol.Action.LIST, null, null);
            } else {
                sendAmpersandConfigured(player, paperConfig.messageUnknownSubcommand());
            }
        } catch (IOException e) {
            sendAmpersandConfigured(player, paperConfig.messageProtocolError());
            getLogger().warning(e.getMessage());
        }
        return true;
    }

    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (args.length != 1 || !"reload".equalsIgnoreCase(args[0])) {
            sendAmpersandConfigured(sender, paperConfig.messageUsageSopPartyReload());
            return true;
        }
        if (!(sender instanceof Player)) {
            reloadLocalSettings();
            if (operatingMode == PartyOperatingMode.STANDALONE || activateStandaloneIfTimedOut()) {
                sendAmpersandConfigured(sender,
                        format(getStandaloneMessage("standalone-reload-ok",
                                "&aSopParty standalone config reloaded. Max party size: &f{0}&a."),
                                paperConfig.standaloneMaxPartySize()));
            } else {
                sendAmpersandConfigured(sender, paperConfig.messageReloadUseProxyConsole());
            }
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission(paperConfig.reloadPermission())) {
            sendAmpersandConfigured(player, paperConfig.messageNoPermission());
            return true;
        }
        return onCommand(player, getCommand("party"), "party", new String[]{"reload"});
    }

    private void sendProxyAction(Player player, PartyProtocol.Action action, String text, UUID uuidArg) throws IOException {
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
            for (String s : new String[]{"accept", "create", "deny", "disband", "invite", "kick", "leave", "list", "reload", "transfer"}) {
                if (s.startsWith(prefix)) {
                    if ("reload".equals(s) && !player.hasPermission(paperConfig.reloadPermission())) {
                        continue;
                    }
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
            String name = resolveLocalPlayerName(memberId);
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

    private static String format(String template, Object... replacements) {
        String out = template;
        for (int i = 0; i < replacements.length; i++) {
            out = out.replace("{" + i + "}", String.valueOf(replacements[i]));
        }
        return out;
    }

    private void startMetricsIfConfigured() {
        if (!getConfig().getBoolean("bstats.enabled", true)) {
            return;
        }
        new Metrics(this, BSTATS_PLUGIN_ID);
    }
}

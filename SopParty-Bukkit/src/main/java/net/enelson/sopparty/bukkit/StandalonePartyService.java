package net.enelson.sopparty.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class StandalonePartyService {

    private final SopPartyPlugin plugin;
    private final PartyMemberCache cache;

    private final Map<UUID, UUID> partyOfPlayer = new ConcurrentHashMap<UUID, UUID>();
    private final Map<UUID, StandaloneParty> parties = new ConcurrentHashMap<UUID, StandaloneParty>();
    private final Map<UUID, PendingInvite> pendingInvites = new ConcurrentHashMap<UUID, PendingInvite>();

    private volatile int maxPartySize;
    private volatile long inviteTtlMillis;

    private static final class PendingInvite {
        private final UUID partyId;
        private final UUID inviter;
        private final long expiresAtMs;

        private PendingInvite(UUID partyId, UUID inviter, long expiresAtMs) {
            this.partyId = partyId;
            this.inviter = inviter;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static final class StandaloneParty {
        private final UUID id;
        private UUID leader;
        private final LinkedHashSet<UUID> members = new LinkedHashSet<UUID>();

        private StandaloneParty(UUID id, UUID leader) {
            this.id = id;
            this.leader = leader;
            this.members.add(leader);
        }
    }

    StandalonePartyService(SopPartyPlugin plugin, PartyMemberCache cache) {
        this.plugin = plugin;
        this.cache = cache;
        reloadSettings();
    }

    void reloadSettings() {
        SopPartyPaperConfig cfg = plugin.paperConfig();
        maxPartySize = cfg.standaloneMaxPartySize();
        inviteTtlMillis = cfg.standaloneInviteTtlSeconds() * 1000L;
        pruneInvites();
    }

    void handleJoin(Player player) {
        pruneInvites();
        refreshPlayer(player.getUniqueId());
    }

    void handleQuit(Player player) {
        pendingInvites.remove(player.getUniqueId());
        leaveInternal(player.getUniqueId(), false);
    }

    boolean handleReservationRequest(Player player, String reservationKeyOrBlank) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        Optional<StandaloneParty> opt = getParty(player.getUniqueId());
        if (!opt.isPresent()) {
            plugin.sendAmpersandConfigured(player, msg("standalone-reserve-not-in-party", "&cYou are not in a party."));
            return true;
        }
        StandaloneParty party = opt.get();
        if (!party.leader.equals(player.getUniqueId())) {
            plugin.sendAmpersandConfigured(player, msg("standalone-reserve-leader-only", "&cOnly the party leader may set reservations."));
            return true;
        }
        String key = reservationKeyOrBlank != null ? reservationKeyOrBlank.trim() : "";
        plugin.applyReservationState(party.id, key.isEmpty() ? null : key);
        return true;
    }

    void handleAction(Player player, String subcommand, String[] args, UUID inviterUuidArg) {
        pruneInvites();
        if ("create".equals(subcommand)) {
            create(player);
            return;
        }
        if ("invite".equals(subcommand)) {
            invite(player, args.length >= 2 ? args[1] : null);
            return;
        }
        if ("accept".equals(subcommand)) {
            accept(player, inviterUuidArg);
            return;
        }
        if ("deny".equals(subcommand)) {
            deny(player, inviterUuidArg);
            return;
        }
        if ("leave".equals(subcommand)) {
            leave(player.getUniqueId());
            return;
        }
        if ("disband".equals(subcommand)) {
            disband(player.getUniqueId());
            return;
        }
        if ("kick".equals(subcommand)) {
            kick(player.getUniqueId(), args.length >= 2 ? args[1] : null);
            return;
        }
        if ("transfer".equals(subcommand)) {
            transfer(player.getUniqueId(), args.length >= 2 ? args[1] : null);
            return;
        }
        if ("list".equals(subcommand)) {
            list(player.getUniqueId());
        }
    }

    private void create(Player leader) {
        UUID leaderId = leader.getUniqueId();
        if (partyOfPlayer.containsKey(leaderId)) {
            plugin.sendAmpersandConfigured(leader, msg("standalone-create-already-in-party", "&cYou are already in a party."));
            return;
        }
        StandaloneParty party = new StandaloneParty(UUID.randomUUID(), leaderId);
        parties.put(party.id, party);
        partyOfPlayer.put(leaderId, party.id);
        plugin.sendAmpersandConfigured(leader, msg("standalone-create-success", "&aParty created. You are the leader."));
        refreshParty(party);
    }

    private void invite(Player leader, String targetName) {
        Optional<StandaloneParty> opt = getParty(leader.getUniqueId());
        if (!opt.isPresent()) {
            plugin.sendAmpersandConfigured(leader, msg("standalone-invite-not-in-party", "&cYou are not in a party. Use /party create first."));
            return;
        }
        StandaloneParty party = opt.get();
        if (!party.leader.equals(leader.getUniqueId())) {
            plugin.sendAmpersandConfigured(leader, msg("standalone-invite-leader-only", "&cOnly the leader can invite."));
            return;
        }
        if (targetName == null || targetName.trim().isEmpty()) {
            plugin.sendAmpersandConfigured(leader, plugin.paperConfig().messageUsageInvite());
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName.trim());
        if (target == null || !target.isOnline()) {
            plugin.sendAmpersandConfigured(leader, msg("standalone-invite-player-offline", "&cPlayer not found online."));
            return;
        }
        if (target.getUniqueId().equals(leader.getUniqueId())) {
            plugin.sendAmpersandConfigured(leader, msg("standalone-invite-self", "&cYou cannot invite yourself."));
            return;
        }
        if (partyOfPlayer.containsKey(target.getUniqueId())) {
            plugin.sendAmpersandConfigured(leader, msg("standalone-invite-target-in-party", "&cThat player is already in a party."));
            return;
        }
        if (party.members.size() >= maxPartySize) {
            plugin.sendAmpersandConfigured(leader, msg("standalone-invite-party-full", "&cParty is full."));
            return;
        }
        pendingInvites.put(target.getUniqueId(),
                new PendingInvite(party.id, leader.getUniqueId(), System.currentTimeMillis() + inviteTtlMillis));
        plugin.sendAmpersandConfigured(leader,
                format(msg("standalone-invite-sent", "&aInvite sent to &f{0}&a."), target.getName()));
        plugin.sendAmpersandConfigured(target,
                format(msg("standalone-invite-notify-target", "&eParty invite from &f{0}&e. /party accept {1}"),
                        leader.getName(), leader.getName()));
    }

    private void accept(Player invited, UUID inviterUuid) {
        PendingInvite invite = pendingInvites.remove(invited.getUniqueId());
        if (invite == null) {
            plugin.sendAmpersandConfigured(invited, msg("standalone-accept-no-invite", "&cNo pending invite."));
            return;
        }
        if (invite.expiresAtMs < System.currentTimeMillis()) {
            plugin.sendAmpersandConfigured(invited, msg("standalone-accept-expired", "&cInvite expired."));
            return;
        }
        if (inviterUuid != null && !invite.inviter.equals(inviterUuid)) {
            pendingInvites.put(invited.getUniqueId(), invite);
            plugin.sendAmpersandConfigured(invited, msg("standalone-accept-mismatch", "&cThat invite does not match."));
            return;
        }
        StandaloneParty party = parties.get(invite.partyId);
        if (party == null || !party.members.contains(invite.inviter)) {
            plugin.sendAmpersandConfigured(invited, msg("standalone-accept-party-missing", "&cParty no longer exists."));
            return;
        }
        if (partyOfPlayer.containsKey(invited.getUniqueId())) {
            plugin.sendAmpersandConfigured(invited, msg("standalone-accept-already-in-party", "&cYou are already in a party."));
            return;
        }
        if (party.members.size() >= maxPartySize) {
            plugin.sendAmpersandConfigured(invited, msg("standalone-accept-party-full", "&cParty became full."));
            return;
        }
        party.members.add(invited.getUniqueId());
        partyOfPlayer.put(invited.getUniqueId(), party.id);
        plugin.sendAmpersandConfigured(invited, msg("standalone-accept-success", "&aJoined the party."));
        String joinedName = invited.getName();
        for (UUID memberId : party.members) {
            if (!memberId.equals(invited.getUniqueId())) {
                plugin.sendMessageToPlayer(memberId,
                        format(msg("standalone-accept-broadcast-join", "&f{0} &ajoined the party."), joinedName));
            }
        }
        refreshParty(party);
    }

    private void deny(Player invited, UUID inviterUuid) {
        PendingInvite invite = pendingInvites.remove(invited.getUniqueId());
        if (invite == null) {
            plugin.sendAmpersandConfigured(invited, msg("standalone-accept-no-invite", "&cNo pending invite."));
            return;
        }
        if (inviterUuid != null && !invite.inviter.equals(inviterUuid)) {
            pendingInvites.put(invited.getUniqueId(), invite);
            plugin.sendAmpersandConfigured(invited, msg("standalone-accept-mismatch", "&cThat invite does not match."));
            return;
        }
        plugin.sendMessageToPlayer(invite.inviter,
                format(msg("standalone-deny-notify-inviter", "&e{0} &cdenied the invite."), invited.getName()));
        plugin.sendAmpersandConfigured(invited, msg("standalone-deny-success", "&eInvite denied."));
    }

    private void leave(UUID playerId) {
        if (!leaveInternal(playerId, true)) {
            plugin.sendMessageToPlayer(playerId, msg("standalone-not-in-party", "&cYou are not in a party."));
        }
    }

    private boolean leaveInternal(UUID playerId, boolean notifyPlayer) {
        Optional<StandaloneParty> opt = getParty(playerId);
        if (!opt.isPresent()) {
            return false;
        }
        StandaloneParty party = opt.get();
        party.members.remove(playerId);
        partyOfPlayer.remove(playerId);
        plugin.clearPartyState(playerId);
        if (notifyPlayer) {
            plugin.sendMessageToPlayer(playerId, msg("standalone-leave-success", "&eYou left the party."));
        }
        if (party.members.isEmpty()) {
            parties.remove(party.id);
            plugin.applyReservationState(party.id, null);
            return true;
        }
        if (party.leader.equals(playerId)) {
            party.leader = party.members.iterator().next();
            broadcastParty(party,
                    format(msg("standalone-leadership-transferred", "&eLeadership transferred to &f{0}&e."),
                            plugin.resolveLocalPlayerName(party.leader)));
        }
        refreshParty(party);
        return true;
    }

    private void disband(UUID leaderId) {
        Optional<StandaloneParty> opt = getParty(leaderId);
        if (!opt.isPresent()) {
            plugin.sendMessageToPlayer(leaderId, msg("standalone-not-in-party", "&cYou are not in a party."));
            return;
        }
        StandaloneParty party = opt.get();
        if (!party.leader.equals(leaderId)) {
            plugin.sendMessageToPlayer(leaderId, msg("standalone-leader-only-disband", "&cOnly the leader can disband."));
            return;
        }
        List<UUID> members = new ArrayList<UUID>(party.members);
        parties.remove(party.id);
        plugin.applyReservationState(party.id, null);
        for (UUID memberId : members) {
            partyOfPlayer.remove(memberId);
            plugin.clearPartyState(memberId);
            plugin.sendMessageToPlayer(memberId, msg("standalone-disband-notice", "&cParty disbanded."));
        }
    }

    private void kick(UUID leaderId, String targetName) {
        if (targetName == null || targetName.trim().isEmpty()) {
            plugin.sendMessageToPlayer(leaderId, plugin.paperConfig().messageUsageKick());
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName.trim());
        if (target == null || !target.isOnline()) {
            plugin.sendMessageToPlayer(leaderId, msg("standalone-kick-not-online", "&cPlayer not online."));
            return;
        }
        Optional<StandaloneParty> opt = getParty(leaderId);
        if (!opt.isPresent()) {
            plugin.sendMessageToPlayer(leaderId, msg("standalone-not-in-party", "&cYou are not in a party."));
            return;
        }
        StandaloneParty party = opt.get();
        if (!party.leader.equals(leaderId)) {
            plugin.sendMessageToPlayer(leaderId, msg("standalone-leader-only-kick", "&cOnly the leader can kick."));
            return;
        }
        if (target.getUniqueId().equals(leaderId)) {
            plugin.sendMessageToPlayer(leaderId, msg("standalone-kick-self-hint", "&cKick yourself via /party leave."));
            return;
        }
        if (!party.members.contains(target.getUniqueId())) {
            plugin.sendMessageToPlayer(leaderId, msg("standalone-kick-not-in-your-party", "&cThat player is not in your party."));
            return;
        }
        party.members.remove(target.getUniqueId());
        partyOfPlayer.remove(target.getUniqueId());
        plugin.clearPartyState(target.getUniqueId());
        plugin.sendAmpersandConfigured(target, msg("standalone-kick-target-notice", "&cYou were kicked from the party."));
        plugin.sendMessageToPlayer(leaderId, msg("standalone-kick-leader-ok", "&aPlayer kicked."));
        broadcastParty(party,
                format(msg("standalone-kick-broadcast", "&f{0} &ewas kicked."), target.getName()));
        refreshParty(party);
    }

    private void transfer(UUID leaderId, String newLeaderName) {
        if (newLeaderName == null || newLeaderName.trim().isEmpty()) {
            plugin.sendMessageToPlayer(leaderId, plugin.paperConfig().messageUsageTransfer());
            return;
        }
        Player newLeader = Bukkit.getPlayerExact(newLeaderName.trim());
        if (newLeader == null || !newLeader.isOnline()) {
            plugin.sendMessageToPlayer(leaderId, msg("standalone-kick-not-online", "&cPlayer not online."));
            return;
        }
        Optional<StandaloneParty> opt = getParty(leaderId);
        if (!opt.isPresent()) {
            plugin.sendMessageToPlayer(leaderId, msg("standalone-not-in-party", "&cYou are not in a party."));
            return;
        }
        StandaloneParty party = opt.get();
        if (!party.leader.equals(leaderId)) {
            plugin.sendMessageToPlayer(leaderId, msg("standalone-leader-only-transfer", "&cOnly the leader can transfer."));
            return;
        }
        if (!party.members.contains(newLeader.getUniqueId())) {
            plugin.sendMessageToPlayer(leaderId, msg("standalone-transfer-not-on-party", "&cThat player is not in your party."));
            return;
        }
        party.leader = newLeader.getUniqueId();
        broadcastParty(party,
                format(msg("standalone-transfer-broadcast", "&e{0} &ais now party leader."), newLeader.getName()));
        refreshParty(party);
    }

    private void list(UUID actorId) {
        Optional<StandaloneParty> opt = getParty(actorId);
        if (!opt.isPresent()) {
            plugin.sendMessageToPlayer(actorId, msg("standalone-not-in-party", "&cYou are not in a party."));
            return;
        }
        StandaloneParty party = opt.get();
        StringBuilder out = new StringBuilder(format(msg("standalone-list-prefix", "&6Party &7(&f{0}&7 leader)&6:"),
                plugin.resolveLocalPlayerName(party.leader)));
        for (UUID memberId : party.members) {
            out.append(format(msg("standalone-list-member", " &f{0}"), plugin.resolveLocalPlayerName(memberId)));
        }
        plugin.sendMessageToPlayer(actorId, out.toString());
    }

    private Optional<StandaloneParty> getParty(UUID playerId) {
        UUID partyId = partyOfPlayer.get(playerId);
        if (partyId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(parties.get(partyId));
    }

    private void refreshPlayer(UUID playerId) {
        Optional<StandaloneParty> opt = getParty(playerId);
        if (!opt.isPresent()) {
            plugin.clearPartyState(playerId);
            return;
        }
        StandaloneParty party = opt.get();
        plugin.applyPartySnapshot(party.id, party.leader, new ArrayList<UUID>(party.members));
        plugin.applyReservationState(party.id, cache.reservationGameKeyOf(playerId).orElse(null));
    }

    private void refreshParty(StandaloneParty party) {
        plugin.applyPartySnapshot(party.id, party.leader, new ArrayList<UUID>(party.members));
        plugin.applyReservationState(party.id, cache.reservationGameKeyOf(party.leader).orElse(null));
    }

    private void broadcastParty(StandaloneParty party, String message) {
        for (UUID memberId : party.members) {
            plugin.sendMessageToPlayer(memberId, message);
        }
    }

    private void pruneInvites() {
        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<UUID>();
        for (Map.Entry<UUID, PendingInvite> entry : pendingInvites.entrySet()) {
            if (entry.getValue().expiresAtMs < now) {
                expired.add(entry.getKey());
            }
        }
        for (UUID playerId : expired) {
            pendingInvites.remove(playerId);
        }
    }

    private String msg(String key, String def) {
        return plugin.getStandaloneMessage(key, def);
    }

    private static String format(String template, Object... replacements) {
        String out = template;
        for (int i = 0; i < replacements.length; i++) {
            out = out.replace("{" + i + "}", String.valueOf(replacements[i]));
        }
        return out;
    }
}

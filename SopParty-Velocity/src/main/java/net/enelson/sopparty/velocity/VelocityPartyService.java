package net.enelson.sopparty.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.enelson.sopparty.protocol.PartyProtocol;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative party state on the proxy (memory-only MVP).
 */
public final class VelocityPartyService {

    private final ProxyServer proxy;
    private final Logger logger;
    private final VelocityPartyBroadcaster broadcaster;
    private final VelocityPartyMessages msgs;
    private final int maxPartySize;
    private final long inviteTtlMillis;

    private final Map<UUID, UUID> partyOfPlayer = new ConcurrentHashMap<UUID, UUID>();
    private final Map<UUID, VelocityParty> parties = new ConcurrentHashMap<UUID, VelocityParty>();

    /** invited player -> inviter uuid + party id + expiry */
    private final Map<UUID, PendingInvite> pendingInvites = new ConcurrentHashMap<UUID, PendingInvite>();

    /** party id → opaque reservation key game plugins agree on ( Velocity authoritative ). */
    private final Map<UUID, String> partyReservations = new ConcurrentHashMap<UUID, String>();

    private static final class PendingInvite {
        private final UUID partyId;
        private final UUID inviter;
        private final long expiresAtMs;

        PendingInvite(UUID partyId, UUID inviter, long expiresAtMs) {
            this.partyId = partyId;
            this.inviter = inviter;
            this.expiresAtMs = expiresAtMs;
        }
    }

    public VelocityPartyService(
            ProxyServer proxy,
            Logger logger,
            VelocityPartySettings settings,
            VelocityPartyMessages msgs) {
        this.proxy = proxy;
        this.logger = logger;
        this.broadcaster = new VelocityPartyBroadcaster(proxy);
        this.msgs = msgs;
        this.maxPartySize = settings.maxPartySize();
        this.inviteTtlMillis = settings.inviteTtlMillis();
    }

    public Optional<VelocityParty> getParty(UUID playerId) {
        UUID pid = partyOfPlayer.get(playerId);
        if (pid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(parties.get(pid));
    }

    public void handleSyncRequest(UUID playerId) {
        broadcastPartyViewOf(playerId);
    }

    /**
     * Public entry for proxy lifecycle hooks ( {@link VelocityPartyServerSwitchListener} ).
     */
    public void refreshPartyOnBackend(UUID playerId) {
        broadcastPartyViewOf(playerId);
    }

    /**
     * Leader-follow behavior: when the leader reaches a backend, move online members there too.
     */
    public void followLeaderToServer(UUID actorUuid, RegisteredServer target) {
        Optional<VelocityParty> opt = getParty(actorUuid);
        if (opt.isEmpty()) {
            return;
        }
        VelocityParty p = opt.get();
        if (!p.getLeader().equals(actorUuid)) {
            return;
        }
        String targetName = target.getServerInfo().getName();
        String leaderName = resolveName(actorUuid);
        for (UUID memberId : p.getMembers()) {
            if (memberId.equals(actorUuid)) {
                continue;
            }
            Optional<Player> member = proxy.getPlayer(memberId);
            if (member.isEmpty()) {
                continue;
            }
            Optional<RegisteredServer> current = member.get().getCurrentServer().map(sc -> sc.getServer());
            if (current.isPresent() && targetName.equals(current.get().getServerInfo().getName())) {
                continue;
            }
            String memberName = member.get().getUsername();
            member.get().createConnectionRequest(target).connect().whenComplete((result, error) -> {
                if (error != null) {
                    logger.warn("Leader-follow failed for {} -> {} (leader={}): {}",
                            memberName, targetName, leaderName, error.toString());
                    return;
                }
                if (result == null || !result.isSuccessful()) {
                    logger.warn("Leader-follow denied for {} -> {} (leader={}, status={})",
                            memberName,
                            targetName,
                            leaderName,
                            result != null ? result.getStatus() : "null-result");
                    return;
                }
                refreshPartyOnBackend(memberId);
            });
        }
    }

    private static String truncateUtfChars(String s, int maxChars) {
        if (maxChars <= 0) {
            return "";
        }
        int count = s.codePointCount(0, s.length());
        if (count <= maxChars) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, maxChars));
    }

    public void handlePartyReserve(UUID actorUuid, String rawGameKey) {
        Optional<VelocityParty> opt = getParty(actorUuid);
        if (!opt.isPresent()) {
            message(actorUuid, msgs.reserveNotInParty());
            return;
        }
        VelocityParty p = opt.get();
        if (!p.getLeader().equals(actorUuid)) {
            message(actorUuid, msgs.reserveLeaderOnly());
            return;
        }
        String trimmed = rawGameKey != null ? rawGameKey.trim() : "";
        UUID pid = p.getId();
        if (trimmed.isEmpty()) {
            partyReservations.remove(pid);
        } else {
            partyReservations.put(pid, truncateUtfChars(trimmed, 64));
        }
        broadcastReservation(pid);
    }

    private void broadcastReservation(UUID partyUuid) {
        try {
            String k = partyReservations.get(partyUuid);
            boolean has = k != null && !k.isBlank();
            byte[] relay = PartyProtocol.encodePartyReservation(partyUuid, has, has ? k : "");
            broadcaster.broadcast(relay);
        } catch (IOException e) {
            logger.warn("Reservation sync encode failed: {}", e.toString());
        }
    }

    private void broadcastPartyViewOf(UUID playerId) {
        UUID pid = partyOfPlayer.get(playerId);
        if (pid == null) {
            pushClear(playerId);
            return;
        }
        VelocityParty p = parties.get(pid);
        if (p == null) {
            pushClear(playerId);
            return;
        }
        pushSnapshot(p);
    }

    private void pushSnapshot(VelocityParty p) {
        try {
            byte[] payload = PartyProtocol.encodePartySnapshot(p.getId(), p.getLeader(), p.memberList());
            broadcaster.broadcastPartySnapshot(payload, p.getMembers());
        } catch (IOException e) {
            logger.warn("Encode snapshot failed: {}", e.toString());
        }
    }

    private void pushClear(UUID player) {
        try {
            broadcaster.broadcast(PartyProtocol.encodeClearPlayer(player));
        } catch (IOException e) {
            logger.warn("Encode clear failed: {}", e.toString());
        }
    }

    private void refreshEveryoneInParty(VelocityParty p) {
        pushSnapshot(p);
        broadcastReservation(p.getId());
    }

    public void handleAction(PartyProtocol.DecodedAction decoded) {
        if (decoded.action == null) {
            return;
        }
        UUID actor = decoded.actor;
        switch (decoded.action) {
            case CREATE:
                create(actor);
                break;
            case INVITE:
                invite(actor, decoded.textArg);
                break;
            case ACCEPT:
                accept(actor, decoded.uuidArg);
                break;
            case DENY:
                deny(actor, decoded.uuidArg);
                break;
            case LEAVE:
                leave(actor);
                break;
            case DISBAND:
                disband(actor);
                break;
            case KICK:
                kick(actor, decoded.textArg);
                break;
            case TRANSFER:
                transfer(actor, decoded.textArg);
                break;
            case LIST:
                list(actor);
                break;
            default:
                break;
        }
    }

    private void create(UUID leader) {
        if (partyOfPlayer.containsKey(leader)) {
            message(leader, msgs.createAlreadyInParty());
            return;
        }
        VelocityParty p = new VelocityParty(UUID.randomUUID(), leader);
        parties.put(p.getId(), p);
        partyOfPlayer.put(leader, p.getId());
        message(leader, msgs.createSuccess());
        pushSnapshot(p);
        broadcastReservation(p.getId());
    }

    private void invite(UUID leader, String targetName) {
        Optional<VelocityParty> opt = getParty(leader);
        if (!opt.isPresent()) {
            message(leader, msgs.inviteNotInParty());
            return;
        }
        VelocityParty p = opt.get();
        if (!p.getLeader().equals(leader)) {
            message(leader, msgs.inviteLeaderOnly());
            return;
        }
        if (targetName == null || targetName.trim().isEmpty()) {
            message(leader, msgs.inviteNeedName());
            return;
        }
        Optional<Player> target = proxy.getPlayer(targetName.trim());
        if (!target.isPresent()) {
            message(leader, msgs.invitePlayerOffline());
            return;
        }
        Player tp = target.get();
        if (tp.getUniqueId().equals(leader)) {
            message(leader, msgs.inviteSelf());
            return;
        }
        if (partyOfPlayer.containsKey(tp.getUniqueId())) {
            message(leader, msgs.inviteTargetInParty());
            return;
        }
        if (p.size() >= maxPartySize) {
            message(leader, msgs.invitePartyFull());
            return;
        }
        pendingInvites.put(tp.getUniqueId(), new PendingInvite(p.getId(), leader, System.currentTimeMillis() + inviteTtlMillis));
        message(leader, msgs.inviteSentLeader(tp.getUsername()));
        message(tp.getUniqueId(), msgs.inviteNotifyTarget(resolveName(leader)));
    }

    private void accept(UUID invited, UUID inviterUuid) {
        PendingInvite inv = pendingInvites.remove(invited);
        if (inv == null) {
            message(invited, msgs.acceptNoInvite());
            return;
        }
        if (inv.expiresAtMs < System.currentTimeMillis()) {
            message(invited, msgs.acceptExpired());
            return;
        }
        if (inviterUuid != null && !inv.inviter.equals(inviterUuid)) {
            pendingInvites.put(invited, inv);
            message(invited, msgs.acceptMismatch());
            return;
        }
        VelocityParty p = parties.get(inv.partyId);
        if (p == null || !p.contains(inv.inviter)) {
            message(invited, msgs.acceptPartyMissing());
            return;
        }
        if (partyOfPlayer.containsKey(invited)) {
            message(invited, msgs.acceptAlreadyInParty());
            return;
        }
        if (p.size() >= maxPartySize) {
            message(invited, msgs.acceptPartyFull());
            return;
        }
        p.addMember(invited);
        partyOfPlayer.put(invited, p.getId());
        message(invited, msgs.acceptSuccess());
        String joinerResolved = resolveName(invited);
        for (UUID m : p.getMembers()) {
            if (!m.equals(invited)) {
                message(m, msgs.acceptBroadcastJoin(joinerResolved));
            }
        }
        refreshEveryoneInParty(p);
        moveInviteeToLeaderServer(invited, p.getLeader());
    }

    /**
     * On /party accept, invitee should immediately follow the party leader's backend server.
     */
    private void moveInviteeToLeaderServer(UUID inviteeUuid, UUID leaderUuid) {
        Optional<Player> invitee = proxy.getPlayer(inviteeUuid);
        Optional<Player> leader = proxy.getPlayer(leaderUuid);
        if (invitee.isEmpty() || leader.isEmpty()) {
            return;
        }
        Optional<RegisteredServer> leaderServer = leader.get().getCurrentServer().map(sc -> sc.getServer());
        if (leaderServer.isEmpty()) {
            return;
        }
        String targetName = leaderServer.get().getServerInfo().getName();
        Optional<RegisteredServer> inviteeServer = invitee.get().getCurrentServer().map(sc -> sc.getServer());
        if (inviteeServer.isPresent() && targetName.equals(inviteeServer.get().getServerInfo().getName())) {
            return;
        }
        String inviteeName = invitee.get().getUsername();
        invitee.get().createConnectionRequest(leaderServer.get()).connect().whenComplete((result, error) -> {
            if (error != null) {
                logger.warn("Accept-follow failed for {} -> {}: {}", inviteeName, targetName, error.toString());
                return;
            }
            if (result == null || !result.isSuccessful()) {
                logger.warn("Accept-follow denied for {} -> {} (status={})",
                        inviteeName,
                        targetName,
                        result != null ? result.getStatus() : "null-result");
            }
        });
    }

    private void deny(UUID invited, UUID inviterUuid) {
        PendingInvite inv = pendingInvites.remove(invited);
        if (inv == null) {
            message(invited, msgs.acceptNoInvite());
            return;
        }
        message(inv.inviter, msgs.denyNotifyInviter(resolveName(invited)));
        message(invited, msgs.denySuccessInvitee());
    }

    private void leave(UUID player) {
        Optional<VelocityParty> opt = getParty(player);
        if (!opt.isPresent()) {
            message(player, msgs.notInPartyShort());
            return;
        }
        VelocityParty p = opt.get();
        p.removeMember(player);
        partyOfPlayer.remove(player);
        pushClear(player);
        message(player, msgs.leaveSuccess());
        if (p.isEmpty()) {
            UUID pid = p.getId();
            parties.remove(pid);
            partyReservations.remove(pid);
            broadcastReservation(pid);
            return;
        }
        if (p.getLeader().equals(player)) {
            UUID newLeader = p.memberList().get(0);
            p.setLeader(newLeader);
            broadcastPartyMessage(p, msgs.leadershipTransferred(resolveName(newLeader)));
        }
        refreshEveryoneInParty(p);
    }

    private void disband(UUID leader) {
        Optional<VelocityParty> opt = getParty(leader);
        if (!opt.isPresent()) {
            message(leader, msgs.notInPartyShort());
            return;
        }
        VelocityParty p = opt.get();
        if (!p.getLeader().equals(leader)) {
            message(leader, msgs.leaderOnlyDisband());
            return;
        }
        UUID pid = p.getId();
        partyReservations.remove(pid);
        for (UUID m : p.getMembers()) {
            partyOfPlayer.remove(m);
            pushClear(m);
            message(m, msgs.disbandNotice());
        }
        parties.remove(pid);
        broadcastReservation(pid);
    }

    private void kick(UUID leader, String targetName) {
        if (targetName == null || targetName.trim().isEmpty()) {
            message(leader, msgs.kickUsage());
            return;
        }
        Optional<Player> tp = proxy.getPlayer(targetName.trim());
        if (!tp.isPresent()) {
            message(leader, msgs.kickNotOnlineShort());
            return;
        }
        UUID target = tp.get().getUniqueId();
        Optional<VelocityParty> opt = getParty(leader);
        if (!opt.isPresent()) {
            message(leader, msgs.notInPartyShort());
            return;
        }
        VelocityParty p = opt.get();
        if (!p.getLeader().equals(leader)) {
            message(leader, msgs.leaderOnlyKick());
            return;
        }
        if (!p.contains(target)) {
            message(leader, msgs.kickNotInYourParty());
            return;
        }
        if (target.equals(leader)) {
            message(leader, msgs.kickSelfHint());
            return;
        }
        p.removeMember(target);
        partyOfPlayer.remove(target);
        pushClear(target);
        message(target, msgs.kickTargetNotice());
        message(leader, msgs.kickLeaderOk());
        broadcastPartyMessage(p, msgs.kickedBroadcast(resolveName(target)));
        refreshEveryoneInParty(p);
    }

    private void transfer(UUID leader, String newLeaderName) {
        if (newLeaderName == null || newLeaderName.trim().isEmpty()) {
            message(leader, msgs.transferUsage());
            return;
        }
        Optional<Player> tp = proxy.getPlayer(newLeaderName.trim());
        if (!tp.isPresent()) {
            message(leader, msgs.kickNotOnlineShort());
            return;
        }
        UUID newLeader = tp.get().getUniqueId();
        Optional<VelocityParty> opt = getParty(leader);
        if (!opt.isPresent()) {
            message(leader, msgs.notInPartyShort());
            return;
        }
        VelocityParty p = opt.get();
        if (!p.getLeader().equals(leader)) {
            message(leader, msgs.leaderOnlyTransfer());
            return;
        }
        if (!p.contains(newLeader)) {
            message(leader, msgs.transferNotOnParty());
            return;
        }
        p.setLeader(newLeader);
        broadcastPartyMessage(p, msgs.transferNowLeaderBroadcast(resolveName(newLeader)));
        refreshEveryoneInParty(p);
    }

    private void list(UUID actor) {
        Optional<VelocityParty> opt = getParty(actor);
        if (!opt.isPresent()) {
            message(actor, msgs.notInPartyShort());
            return;
        }
        VelocityParty p = opt.get();
        StringBuilder sb = new StringBuilder(msgs.partyListPrefix(resolveName(p.getLeader())));
        for (UUID m : p.memberList()) {
            sb.append(msgs.partyListMemberSep(resolveName(m)));
        }
        message(actor, sb.toString());
    }

    private void broadcastPartyMessage(VelocityParty p, String miniMessageLegacyAmpersand) {
        for (UUID m : p.getMembers()) {
            message(m, miniMessageLegacyAmpersand);
        }
    }

    private String resolveName(UUID id) {
        Optional<Player> pl = proxy.getPlayer(id);
        return pl.map(Player::getUsername).orElseGet(() -> id.toString().substring(0, 8));
    }

    private void message(UUID playerId, String rawAmpersand) {
        Optional<Player> pl = proxy.getPlayer(playerId);
        if (pl.isEmpty()) {
            return;
        }
        pl.get().sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand()
                .deserialize(rawAmpersand));
    }

    void pruneInvites() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, PendingInvite>> it = pendingInvites.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PendingInvite> e = it.next();
            if (e.getValue().expiresAtMs < now) {
                it.remove();
            }
        }
    }
}

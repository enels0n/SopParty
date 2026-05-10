package net.enelson.sopparty.velocity;

import java.util.Properties;

/**
 * Player-facing Velocity messages (&-color MiniMessage-as-legacy). Keys in {@code sopparty.properties} under {@code msg.*}.
 */
public final class VelocityPartyMessages {

    private final String createAlreadyInParty;
    private final String createSuccess;
    private final String inviteNotInParty;
    private final String inviteLeaderOnly;
    private final String inviteNeedName;
    private final String invitePlayerOffline;
    private final String inviteSelf;
    private final String inviteTargetInParty;
    private final String invitePartyFull;
    private final String inviteSentLeader;
    private final String inviteNotifyTarget;

    private final String acceptNoInvite;
    private final String acceptExpired;
    private final String acceptMismatch;
    private final String acceptPartyMissing;
    private final String acceptAlreadyInParty;
    private final String acceptPartyFull;
    private final String acceptSuccess;
    private final String acceptBroadcastJoin;

    private final String denyNotifyInviter;
    private final String denySuccessInvitee;

    private final String notInPartyShort;
    private final String leaderOnlyKick;
    private final String leaderOnlyDisband;
    private final String leaderOnlyTransfer;

    private final String kickUsage;
    private final String transferUsage;
    private final String kickNotOnlineShort;
    private final String kickNotInYourParty;

    private final String leaveSuccess;
    private final String leadershipTransferred;
    private final String kickSelfHint;
    private final String kickTargetNotice;
    private final String kickLeaderOk;
    private final String kickedBroadcast;

    private final String disbandNotice;

    private final String transferNotOnParty;
    private final String transferNowLeaderBroadcast;

    private final String partyListPrefix;
    private final String partyListMemberSep;

    private final String reserveNotInParty;
    private final String reserveLeaderOnly;

    private static String r(Properties p, String key, String fallback) {
        String v = p.getProperty(key);
        return v != null && !v.isBlank() ? v : fallback;
    }

    private static String sub(String tmpl, Object... replacements) {
        String out = tmpl;
        for (int i = 0; i < replacements.length; i++) {
            out = out.replace("{" + i + "}", String.valueOf(replacements[i]));
        }
        return out;
    }

    VelocityPartyMessages(Properties p) {
        createAlreadyInParty = r(p, "msg.create.err.already-in-party", "&cYou are already in a party.");
        createSuccess = r(p, "msg.create.ok.created", "&aParty created. You are the leader.");
        inviteNotInParty = r(p, "msg.invite.err.not-in-party", "&cYou are not in a party. Use /party create first.");
        inviteLeaderOnly = r(p, "msg.invite.err.leader-only", "&cOnly the leader can invite.");
        inviteNeedName = r(p, "msg.invite.err.need-name", "&cSpecify a player name.");
        invitePlayerOffline = r(p, "msg.invite.err.player-offline", "&cPlayer not found online.");
        inviteSelf = r(p, "msg.invite.err.self", "&cYou cannot invite yourself.");
        inviteTargetInParty = r(p, "msg.invite.err.target-has-party", "&cThat player is already in a party.");
        invitePartyFull = r(p, "msg.invite.err.full", "&cParty is full.");
        inviteSentLeader = r(p, "msg.invite.ok.sent", "&aInvite sent to &f{0}&a.");
        inviteNotifyTarget = r(p, "msg.invite.ok.notify-target", "&eParty invite from &f{0}&e. /party accept {1}");

        acceptNoInvite = r(p, "msg.accept.err.no-invite", "&cNo pending invite.");
        acceptExpired = r(p, "msg.accept.err.expired", "&cInvite expired.");
        acceptMismatch = r(p, "msg.accept.err.mismatch", "&cThat invite does not match.");
        acceptPartyMissing = r(p, "msg.accept.err.party-gone", "&cParty no longer exists.");
        acceptAlreadyInParty = r(p, "msg.accept.err.already-party", "&cYou are already in a party.");
        acceptPartyFull = r(p, "msg.accept.err.full", "&cParty became full.");
        acceptSuccess = r(p, "msg.accept.ok.joined", "&aJoined the party.");
        acceptBroadcastJoin = r(p, "msg.accept.broadcast.joined", "&f{0} &ajoined the party.");

        denyNotifyInviter = r(p, "msg.deny.to-inviter", "&e{0} &cdenied the invite.");
        denySuccessInvitee = r(p, "msg.deny.to-invitee", "&eInvite denied.");

        notInPartyShort = r(p, "msg.err.not-in-party", "&cYou are not in a party.");
        leaderOnlyKick = r(p, "msg.kick.err.leader-only", "&cOnly the leader can kick.");
        leaderOnlyDisband = r(p, "msg.disband.err.leader-only", "&cOnly the leader can disband.");
        leaderOnlyTransfer = r(p, "msg.transfer.err.leader-only", "&cOnly the leader can transfer.");

        kickUsage = r(p, "msg.usage.kick", "&cUsage: /party kick <player>");
        transferUsage = r(p, "msg.usage.transfer", "&cUsage: /party transfer <player>");
        kickNotOnlineShort = r(p, "msg.err.player-offline", "&cPlayer not online.");
        kickNotInYourParty = r(p, "msg.err.not-your-party-player", "&cThat player is not in your party.");

        leaveSuccess = r(p, "msg.leave.ok.left", "&eYou left the party.");
        leadershipTransferred = r(p, "msg.leave.broadcast.new-leader", "&eLeadership transferred to &f{0}&e.");
        kickSelfHint = r(p, "msg.kick.err.kick-self", "&cKick yourself via /party leave.");
        kickTargetNotice = r(p, "msg.kick.target.kicked", "&cYou were kicked from the party.");
        kickLeaderOk = r(p, "msg.kick.leader.ok", "&aPlayer kicked.");
        kickedBroadcast = r(p, "msg.kick.broadcast", "&f{0} &ewas kicked.");

        disbandNotice = r(p, "msg.disband.broadcast", "&cParty disbanded.");

        transferNotOnParty = r(p, "msg.transfer.err.not-member", "&cThat player is not in your party.");
        transferNowLeaderBroadcast = r(p, "msg.transfer.broadcast", "&e{0} &ais now party leader.");

        partyListPrefix = r(p, "msg.list.prefix", "&6Party &7(&f{0}&7 leader)&6:");
        partyListMemberSep = r(p, "msg.list.member", " &f{0}");

        reserveNotInParty = r(p, "msg.reserve.err.not-in-party", "&cYou are not in a party.");
        reserveLeaderOnly = r(p, "msg.reserve.err.leader-only", "&cOnly the party leader may set reservations.");
    }

    public String createAlreadyInParty() {
        return createAlreadyInParty;
    }

    public String createSuccess() {
        return createSuccess;
    }

    String inviteNotInParty() {
        return inviteNotInParty;
    }

    String inviteLeaderOnly() {
        return inviteLeaderOnly;
    }

    String inviteNeedName() {
        return inviteNeedName;
    }

    String invitePlayerOffline() {
        return invitePlayerOffline;
    }

    String inviteSelf() {
        return inviteSelf;
    }

    String inviteTargetInParty() {
        return inviteTargetInParty;
    }

    String invitePartyFull() {
        return invitePartyFull;
    }

    public String inviteSentLeader(String inviteeUsername) {
        return sub(inviteSentLeader, inviteeUsername);
    }

    public String inviteNotifyTarget(String leaderDisplay) {
        return sub(inviteNotifyTarget, leaderDisplay, leaderDisplay);
    }

    String acceptNoInvite() {
        return acceptNoInvite;
    }

    String acceptExpired() {
        return acceptExpired;
    }

    String acceptMismatch() {
        return acceptMismatch;
    }

    String acceptPartyMissing() {
        return acceptPartyMissing;
    }

    String acceptAlreadyInParty() {
        return acceptAlreadyInParty;
    }

    String acceptPartyFull() {
        return acceptPartyFull;
    }

    String acceptSuccess() {
        return acceptSuccess;
    }

    public String acceptBroadcastJoin(String joinerResolved) {
        return sub(acceptBroadcastJoin, joinerResolved);
    }

    String denyNotifyInviter(String inviteeResolved) {
        return sub(denyNotifyInviter, inviteeResolved);
    }

    String denySuccessInvitee() {
        return denySuccessInvitee;
    }

    String notInPartyShort() {
        return notInPartyShort;
    }

    String leaderOnlyKick() {
        return leaderOnlyKick;
    }

    String leaderOnlyDisband() {
        return leaderOnlyDisband;
    }

    String leaderOnlyTransfer() {
        return leaderOnlyTransfer;
    }

    String kickUsage() {
        return kickUsage;
    }

    String transferUsage() {
        return transferUsage;
    }

    String kickNotOnlineShort() {
        return kickNotOnlineShort;
    }

    String kickNotInYourParty() {
        return kickNotInYourParty;
    }

    String leaveSuccess() {
        return leaveSuccess;
    }

    String leadershipTransferred(String newLeaderResolved) {
        return sub(leadershipTransferred, newLeaderResolved);
    }

    String kickSelfHint() {
        return kickSelfHint;
    }

    String kickTargetNotice() {
        return kickTargetNotice;
    }

    String kickLeaderOk() {
        return kickLeaderOk;
    }

    String kickedBroadcast(String targetResolved) {
        return sub(kickedBroadcast, targetResolved);
    }

    String disbandNotice() {
        return disbandNotice;
    }

    String transferNotOnParty() {
        return transferNotOnParty;
    }

    String transferNowLeaderBroadcast(String newLeaderResolved) {
        return sub(transferNowLeaderBroadcast, newLeaderResolved);
    }

    String partyListPrefix(String leaderResolved) {
        return sub(partyListPrefix, leaderResolved);
    }

    String partyListMemberSep(String memberResolved) {
        return sub(partyListMemberSep, memberResolved);
    }

    String reserveNotInParty() {
        return reserveNotInParty;
    }

    String reserveLeaderOnly() {
        return reserveLeaderOnly;
    }
}

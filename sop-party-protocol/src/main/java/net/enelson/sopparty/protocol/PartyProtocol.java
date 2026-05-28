package net.enelson.sopparty.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Binary framing for Velocity ⇄ Paper plugin messages on {@link #CHANNEL}.
 */
public final class PartyProtocol {

    public static final String CHANNEL = "sopparty:main";

    public static final byte C2P_SYNC_REQUEST = 0x01;
    public static final byte C2P_ACTION = 0x02;
    /** Backend → Velocity: relay party chat to all backends ( Velocity validates party membership ). */
    public static final byte C2P_PARTY_CHAT = 0x03;
    /** Backend → Velocity: set or clear reserve key for player's party ({@code leader-only}; empty clears). */
    public static final byte C2P_PARTY_RESERVE = 0x04;

    public static final byte P2S_PARTY_SNAPSHOT = 0x10;
    public static final byte P2S_CLEAR_PLAYER = 0x11;
    /** Velocity → all backends: display party chat to local recipients. */
    public static final byte P2S_PARTY_CHAT_RELAY = 0x12;
    /** Velocity → all backends: sync party reservation slot (opaque game key etc.). */
    public static final byte P2S_PARTY_RESERVATION = 0x13;
    /** Velocity → all backends: snapshot of proxy-wide online player names for tab-complete. */
    public static final byte P2S_ONLINE_PLAYERS = 0x14;
    /** Velocity → Bukkit backend: deliver player-facing party message so backend can apply PAPI before sending. */
    public static final byte P2S_BACKEND_MESSAGE = 0x15;

    public enum Action {
        CREATE((byte) 1),
        INVITE((byte) 2),
        ACCEPT((byte) 3),
        DENY((byte) 4),
        LEAVE((byte) 5),
        DISBAND((byte) 6),
        KICK((byte) 7),
        TRANSFER((byte) 8),
        LIST((byte) 9),
        RELOAD((byte) 10);

        private final byte id;

        Action(byte id) {
            this.id = id;
        }

        public byte id() {
            return id;
        }

        public static Action from(byte raw) {
            for (Action a : values()) {
                if (a.id == raw) {
                    return a;
                }
            }
            return null;
        }
    }

    private PartyProtocol() {
    }

    public static byte[] encodeSyncRequest(UUID player) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeByte(C2P_SYNC_REQUEST);
        writeUuid(out, player);
        out.flush();
        return bos.toByteArray();
    }

    public static UUID decodeSyncRequest(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        byte op = in.readByte();
        if (op != C2P_SYNC_REQUEST) {
            throw new IOException("Unexpected opcode: " + op);
        }
        return readUuid(in);
    }

    public static byte[] encodeAction(UUID actor, Action action, String textArg, UUID uuidArg) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeByte(C2P_ACTION);
        writeUuid(out, actor);
        out.writeByte(action.id());
        writeUtf(out, textArg == null ? "" : textArg);
        if (uuidArg != null) {
            out.writeBoolean(true);
            writeUuid(out, uuidArg);
        } else {
            out.writeBoolean(false);
        }
        out.flush();
        return bos.toByteArray();
    }

    public static final class DecodedAction {
        public final UUID actor;
        public final Action action;
        public final String textArg;
        public final UUID uuidArg;

        public DecodedAction(UUID actor, Action action, String textArg, UUID uuidArg) {
            this.actor = actor;
            this.action = action;
            this.textArg = textArg;
            this.uuidArg = uuidArg;
        }
    }

    public static DecodedAction decodeAction(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        byte op = in.readByte();
        if (op != C2P_ACTION) {
            throw new IOException("Unexpected opcode: " + op);
        }
        UUID actor = readUuid(in);
        Action action = Action.from(in.readByte());
        String text = readUtf(in);
        UUID uuidArg = in.readBoolean() ? readUuid(in) : null;
        return new DecodedAction(actor, action, text, uuidArg);
    }

    public static byte[] encodePartySnapshot(UUID partyId, UUID leader, List<UUID> members) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeByte(P2S_PARTY_SNAPSHOT);
        writeUuid(out, partyId);
        writeUuid(out, leader);
        out.writeInt(members.size());
        for (UUID m : members) {
            writeUuid(out, m);
        }
        out.flush();
        return bos.toByteArray();
    }

    public static final class PartySnapshot {
        public final UUID partyId;
        public final UUID leader;
        public final List<UUID> members;

        public PartySnapshot(UUID partyId, UUID leader, List<UUID> members) {
            this.partyId = partyId;
            this.leader = leader;
            this.members = Collections.unmodifiableList(new ArrayList<UUID>(members));
        }
    }

    public static PartySnapshot decodePartySnapshot(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        byte op = in.readByte();
        if (op != P2S_PARTY_SNAPSHOT) {
            throw new IOException("Unexpected opcode: " + op);
        }
        UUID partyId = readUuid(in);
        UUID leader = readUuid(in);
        int n = in.readInt();
        List<UUID> members = new ArrayList<UUID>(n);
        for (int i = 0; i < n; i++) {
            members.add(readUuid(in));
        }
        return new PartySnapshot(partyId, leader, members);
    }

    public static byte[] encodeClearPlayer(UUID player) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeByte(P2S_CLEAR_PLAYER);
        writeUuid(out, player);
        out.flush();
        return bos.toByteArray();
    }

    public static UUID decodeClearPlayer(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        byte op = in.readByte();
        if (op != P2S_CLEAR_PLAYER) {
            throw new IOException("Unexpected opcode: " + op);
        }
        return readUuid(in);
    }

    public static final class DecodedPartyChat {
        public final UUID actor;
        public final String senderDisplayPlain;
        public final String messagePlain;

        public DecodedPartyChat(UUID actor, String senderDisplayPlain, String messagePlain) {
            this.actor = actor;
            this.senderDisplayPlain = senderDisplayPlain;
            this.messagePlain = messagePlain;
        }
    }

    public static byte[] encodePartyChat(byte opcode, UUID actor, String senderDisplayPlain, String messagePlain)
            throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeByte(opcode);
        writeUuid(out, actor);
        writeUtf(out, senderDisplayPlain == null ? "" : senderDisplayPlain);
        writeUtf(out, messagePlain == null ? "" : messagePlain);
        out.flush();
        return bos.toByteArray();
    }

    public static DecodedPartyChat decodePartyChat(byte[] data, byte expectedOpcode) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        byte op = in.readByte();
        if (op != expectedOpcode) {
            throw new IOException("Unexpected opcode: " + op);
        }
        UUID actor = readUuid(in);
        String display = readUtf(in);
        String msg = readUtf(in);
        return new DecodedPartyChat(actor, display, msg);
    }

    public static byte[] encodePartyReserve(UUID actorUuid, String gameKeyUtf8) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeByte(C2P_PARTY_RESERVE);
        writeUuid(out, actorUuid);
        writeUtf(out, gameKeyUtf8 == null ? "" : gameKeyUtf8);
        out.flush();
        return bos.toByteArray();
    }

    public static final class DecodedPartyReserve {
        public final UUID actor;
        public final String gameKey;

        public DecodedPartyReserve(UUID actor, String gameKey) {
            this.actor = actor;
            this.gameKey = gameKey;
        }
    }

    public static DecodedPartyReserve decodePartyReserve(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        byte op = in.readByte();
        if (op != C2P_PARTY_RESERVE) {
            throw new IOException("Unexpected opcode: " + op);
        }
        return new DecodedPartyReserve(readUuid(in), readUtf(in));
    }

    public static byte[] encodePartyReservation(UUID partyUuid, boolean hasReservation, String gameKeyUtf8) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeByte(P2S_PARTY_RESERVATION);
        writeUuid(out, partyUuid);
        out.writeBoolean(hasReservation);
        if (hasReservation) {
            writeUtf(out, gameKeyUtf8 != null ? gameKeyUtf8 : "");
        }
        out.flush();
        return bos.toByteArray();
    }

    public static final class DecodedPartyReservation {
        public final UUID partyId;
        public final boolean hasReservation;
        public final String gameKey;

        public DecodedPartyReservation(UUID partyId, boolean hasReservation, String gameKey) {
            this.partyId = partyId;
            this.hasReservation = hasReservation;
            this.gameKey = gameKey;
        }
    }

    public static DecodedPartyReservation decodePartyReservation(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        byte op = in.readByte();
        if (op != P2S_PARTY_RESERVATION) {
            throw new IOException("Unexpected opcode: " + op);
        }
        UUID partyId = readUuid(in);
        boolean has = in.readBoolean();
        String game = has ? readUtf(in) : "";
        return new DecodedPartyReservation(partyId, has, game);
    }

    public static final class OnlinePlayerEntry {
        public final UUID playerId;
        public final String playerName;

        public OnlinePlayerEntry(UUID playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
        }
    }

    public static byte[] encodeOnlinePlayers(List<OnlinePlayerEntry> players) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeByte(P2S_ONLINE_PLAYERS);
        out.writeInt(players.size());
        for (OnlinePlayerEntry player : players) {
            writeUuid(out, player.playerId);
            writeUtf(out, player.playerName == null ? "" : player.playerName);
        }
        out.flush();
        return bos.toByteArray();
    }

    public static List<OnlinePlayerEntry> decodeOnlinePlayers(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        byte op = in.readByte();
        if (op != P2S_ONLINE_PLAYERS) {
            throw new IOException("Unexpected opcode: " + op);
        }
        int size = in.readInt();
        List<OnlinePlayerEntry> playerNames = new ArrayList<OnlinePlayerEntry>(Math.max(size, 0));
        for (int i = 0; i < size; i++) {
            playerNames.add(new OnlinePlayerEntry(readUuid(in), readUtf(in)));
        }
        return playerNames;
    }

    public static byte[] encodeBackendMessage(UUID recipient, String rawAmpersandMessage) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeByte(P2S_BACKEND_MESSAGE);
        writeUuid(out, recipient);
        writeUtf(out, rawAmpersandMessage == null ? "" : rawAmpersandMessage);
        out.flush();
        return bos.toByteArray();
    }

    public static final class DecodedBackendMessage {
        public final UUID recipient;
        public final String rawAmpersandMessage;

        public DecodedBackendMessage(UUID recipient, String rawAmpersandMessage) {
            this.recipient = recipient;
            this.rawAmpersandMessage = rawAmpersandMessage;
        }
    }

    public static DecodedBackendMessage decodeBackendMessage(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        byte op = in.readByte();
        if (op != P2S_BACKEND_MESSAGE) {
            throw new IOException("Unexpected opcode: " + op);
        }
        return new DecodedBackendMessage(readUuid(in), readUtf(in));
    }

    private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeUtf(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.writeShort(b.length);
        out.write(b);
    }

    private static String readUtf(DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        byte[] b = new byte[len];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }
}

package net.enelson.sopparty.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.enelson.sopparty.protocol.PartyProtocol;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pushes protocol payloads to Paper backends so local caches stay aligned.
 * <p>
 * Party snapshots are sent once per backend that currently hosts an online party member (via each
 * member's backend connection). This matches Velocity's documented pattern and fixes cases where a
 * raw {@link RegisteredServer#sendPluginMessage} fan-out never reaches incoming Spigot listeners.
 * Relay / reservation payloads still fan out globally so every shard can react.
 */
final class VelocityPartyBroadcaster {

    private final ProxyServer proxy;

    VelocityPartyBroadcaster(ProxyServer proxy) {
        this.proxy = proxy;
    }

    void broadcast(byte[] payload) {
        MinecraftChannelIdentifier id = MinecraftChannelIdentifier.from(PartyProtocol.CHANNEL);
        for (RegisteredServer rs : proxy.getAllServers()) {
            rs.sendPluginMessage(id, payload);
        }
    }

    /**
     * Sends the same snapshot to each distinct backend currently holding at least one online party
     * member. Falls back to {@link #broadcast(byte[])} if no party member is online (memory sync for
     * empty networks / offline-first edge cases).
     */
    void broadcastPartySnapshot(byte[] payload, Iterable<UUID> partyMemberIds) {
        MinecraftChannelIdentifier id = MinecraftChannelIdentifier.from(PartyProtocol.CHANNEL);
        Set<String> messagedServers = new HashSet<String>();
        for (UUID memberId : partyMemberIds) {
            Optional<Player> optionalPlayer = proxy.getPlayer(memberId);
            if (!optionalPlayer.isPresent()) {
                continue;
            }
            Optional<ServerConnection> connection = optionalPlayer.get().getCurrentServer();
            if (!connection.isPresent()) {
                continue;
            }
            RegisteredServer backend = connection.get().getServer();
            if (messagedServers.add(backend.getServerInfo().getName())) {
                backend.sendPluginMessage(id, payload);
            }
        }
        if (messagedServers.isEmpty()) {
            broadcast(payload);
        }
    }
}

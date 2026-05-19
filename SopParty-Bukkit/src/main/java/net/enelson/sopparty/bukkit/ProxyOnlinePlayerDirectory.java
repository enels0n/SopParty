package net.enelson.sopparty.bukkit;

import net.enelson.sopparty.protocol.PartyProtocol;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

final class ProxyOnlinePlayerDirectory {

    private final AtomicReference<Map<UUID, String>> namesById =
            new AtomicReference<Map<UUID, String>>(Collections.<UUID, String>emptyMap());

    void replaceAll(Collection<PartyProtocol.OnlinePlayerEntry> players) {
        Map<UUID, String> copy = new LinkedHashMap<UUID, String>();
        for (PartyProtocol.OnlinePlayerEntry player : players) {
            if (player == null || player.playerId == null) {
                continue;
            }
            String name = player.playerName != null ? player.playerName.trim() : "";
            if (!name.isEmpty()) {
                copy.put(player.playerId, name);
            }
        }
        namesById.set(Collections.unmodifiableMap(copy));
    }

    boolean hasSnapshot() {
        return !namesById.get().isEmpty();
    }

    List<String> suggest(String prefixLower, int limit, UUID excludePlayerId) {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<UUID, String> entry : namesById.get().entrySet()) {
            if (excludePlayerId != null && excludePlayerId.equals(entry.getKey())) {
                continue;
            }
            String name = entry.getValue();
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

    String nameOf(UUID playerId) {
        return namesById.get().get(playerId);
    }
}

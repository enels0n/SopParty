package net.enelson.sopparty.bukkit;

import net.enelson.sopparty.api.SopPartyApi;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class DefaultSopPartyApi implements SopPartyApi {

    private final JavaPlugin plugin;
    private final PartyMemberCache cache;

    DefaultSopPartyApi(JavaPlugin plugin, PartyMemberCache cache) {
        this.plugin = plugin;
        this.cache = cache;
    }

    @Override
    public List<UUID> getMemberUuids(Player player) {
        return cache.getMemberUuids(player.getUniqueId());
    }

    @Override
    public Optional<UUID> getPartyId(Player player) {
        return cache.partyIdOf(player.getUniqueId());
    }

    @Override
    public Optional<UUID> getLeaderUuid(Player player) {
        return cache.leaderOf(player.getUniqueId());
    }

    @Override
    public boolean isInParty(Player player) {
        return cache.isInParty(player.getUniqueId());
    }

    @Override
    public boolean isLeader(Player player) {
        return cache.isLeader(player.getUniqueId());
    }

    @Override
    public Optional<String> getPartyReservationGameKey(Player player) {
        return cache.reservationGameKeyOf(player.getUniqueId());
    }

    @Override
    public boolean sendReservationRequest(Player player, String reservationKeyOrBlank) {
        return plugin instanceof SopPartyPlugin
                && ((SopPartyPlugin) plugin).sendReservationRequest(player, reservationKeyOrBlank);
    }
}

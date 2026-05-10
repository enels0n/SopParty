package net.enelson.sopparty.api;

import org.bukkit.Bukkit;

/**
 * Locates the {@link SopPartyApi} registered by the SopParty plugin.
 */
public final class SopPartyServices {

    private SopPartyServices() {
    }

    /**
     * @return registered API, or {@code null} if SopParty is absent or not enabled yet
     */
    public static SopPartyApi get() {
        return Bukkit.getServicesManager().load(SopPartyApi.class);
    }
}

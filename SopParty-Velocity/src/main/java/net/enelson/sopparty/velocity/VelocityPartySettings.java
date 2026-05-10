package net.enelson.sopparty.velocity;

/**
 * Loaded from {@code sopparty.properties} on the proxy.
 */
public final class VelocityPartySettings {

    private final int maxPartySize;
    private final int inviteTtlSeconds;
    private final int invitePruneIntervalSeconds;

    public VelocityPartySettings(int maxPartySize, int inviteTtlSeconds, int invitePruneIntervalSeconds) {
        this.maxPartySize = clamp(maxPartySize, 2, 64);
        this.inviteTtlSeconds = clamp(inviteTtlSeconds, 5, 3600);
        this.invitePruneIntervalSeconds = clamp(invitePruneIntervalSeconds, 5, 600);
    }

    public int maxPartySize() {
        return maxPartySize;
    }

    public int inviteTtlSeconds() {
        return inviteTtlSeconds;
    }

    public int invitePruneIntervalSeconds() {
        return invitePruneIntervalSeconds;
    }

    public long inviteTtlMillis() {
        return inviteTtlSeconds * 1000L;
    }

    private static int clamp(int v, int min, int max) {
        return Math.min(max, Math.max(min, v));
    }
}

package net.enelson.sopparty.velocity;

/** Loaded from Velocity data directory ({@link VelocityPartyConfigLoader}). */
public final class VelocityStartupConfig {

    private final VelocityPartySettings settings;
    private final VelocityPartyMessages messages;

    VelocityStartupConfig(VelocityPartySettings settings, VelocityPartyMessages messages) {
        this.settings = settings;
        this.messages = messages;
    }

    public VelocityPartySettings settings() {
        return settings;
    }

    public VelocityPartyMessages messages() {
        return messages;
    }
}

package net.enelson.sopparty.velocity;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class VelocityPartyConfigLoader {

    private VelocityPartyConfigLoader() {
    }

    static VelocityStartupConfig load(Path dataDirectory, Logger logger) throws IOException {
        if (!Files.isDirectory(dataDirectory)) {
            Files.createDirectories(dataDirectory);
        }

        Properties defaultsProps = new Properties();
        try (InputStream in = VelocityPartyConfigLoader.class.getClassLoader()
                .getResourceAsStream("sopparty-default.properties")) {
            if (in != null) {
                defaultsProps.load(in);
            }
        }

        Path configFile = dataDirectory.resolve("sopparty.properties");
        if (!Files.exists(configFile)) {
            try (InputStream in = VelocityPartyConfigLoader.class.getClassLoader()
                    .getResourceAsStream("sopparty-default.properties")) {
                if (in != null) {
                    Files.copy(in, configFile);
                } else {
                    String defaultConfig =
                            "max-party-size=" + VelocityPartySettings.DEFAULT_MAX_PARTY_SIZE + "\n"
                                    + "invite-ttl-seconds=" + VelocityPartySettings.DEFAULT_INVITE_TTL_SECONDS + "\n"
                                    + "invite-prune-interval-seconds=" + VelocityPartySettings.DEFAULT_INVITE_PRUNE_INTERVAL_SECONDS + "\n";
                    Files.write(configFile, defaultConfig.getBytes(StandardCharsets.UTF_8));
                }
            }
            logger.info("Created default SopParty config at {}", configFile.toAbsolutePath());
        }

        Properties effective = new Properties(defaultsProps);
        try (Reader reader = Files.newBufferedReader(configFile)) {
            effective.load(reader);
        }

        VelocityPartySettings settings = new VelocityPartySettings(
                parseInt(effective, "max-party-size", VelocityPartySettings.DEFAULT_MAX_PARTY_SIZE),
                parseInt(effective, "invite-ttl-seconds", VelocityPartySettings.DEFAULT_INVITE_TTL_SECONDS),
                parseInt(effective, "invite-prune-interval-seconds", VelocityPartySettings.DEFAULT_INVITE_PRUNE_INTERVAL_SECONDS));
        VelocityPartyMessages messages = new VelocityPartyMessages(effective);
        return new VelocityStartupConfig(settings, messages);
    }

    private static int parseInt(Properties p, String key, int def) {
        String s = p.getProperty(key);
        if (s == null || s.trim().isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}

package net.enelson.sopparty.velocity;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
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
                    Files.writeString(configFile, """
                            max-party-size=8
                            invite-ttl-seconds=120
                            invite-prune-interval-seconds=30
                            """);
                }
            }
            logger.info("Created default SopParty config at {}", configFile.toAbsolutePath());
        }

        Properties effective = new Properties(defaultsProps);
        try (Reader reader = Files.newBufferedReader(configFile)) {
            effective.load(reader);
        }

        VelocityPartySettings settings = new VelocityPartySettings(
                parseInt(effective, "max-party-size", 8),
                parseInt(effective, "invite-ttl-seconds", 120),
                parseInt(effective, "invite-prune-interval-seconds", 30));
        VelocityPartyMessages messages = new VelocityPartyMessages(effective);
        return new VelocityStartupConfig(settings, messages);
    }

    private static int parseInt(Properties p, String key, int def) {
        String s = p.getProperty(key);
        if (s == null || s.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}

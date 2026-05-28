package net.enelson.sopparty.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Collections;
import java.util.List;

final class SopPartyVelocityCommand implements SimpleCommand {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final SopPartyVelocityPlugin plugin;

    SopPartyVelocityCommand(SopPartyVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
            if (source instanceof Player && !((Player) source).hasPermission("sopparty.admin.reload")) {
                source.sendMessage(color(new VelocityPartyMessages(new java.util.Properties()).reloadNoPermission()));
                return;
            }
            boolean ok = plugin.reloadDirect();
            if (!ok) {
                source.sendMessage(color(new VelocityPartyMessages(new java.util.Properties()).reloadFailure()));
                return;
            }
            source.sendMessage(color(new VelocityPartyMessages(new java.util.Properties())
                    .reloadSuccess(plugin.getPartyService().getMaxPartySize())));
            return;
        }

        source.sendMessage(color("&e/sopparty reload"));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return Collections.singletonList("reload");
        }
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase())) {
            return Collections.singletonList("reload");
        }
        return Collections.emptyList();
    }

    private static Component color(String input) {
        return LEGACY.deserialize(input);
    }
}

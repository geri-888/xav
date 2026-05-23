package hu.xavpn.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import hu.xavpn.common.CheckFlag;
import hu.xavpn.common.CheckResult;
import hu.xavpn.common.IpApiRecord;
import hu.xavpn.common.PlayerIndex;
import hu.xavpn.common.TextUtil;
import hu.xavpn.common.XavConfig;
import hu.xavpn.common.XavCore;
import hu.xavpn.common.XavLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Plugin(id = "xavpn", name = "XAVPN", version = "1.0.0", description = "IPAPI.is based anti VPN/proxy/datacenter checker.", authors = {"XAVPN"})
public final class XavVelocityPlugin {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private XavCore core;

    @Inject
    public XavVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        core = new XavCore(dataDirectory.toFile(), new VelocityLogger());
        try {
            core.load();
        } catch (IOException exception) {
            logger.error("XAVPN nem tudott betolteni: {}", exception.getMessage());
            return;
        }
        CommandMeta meta = server.getCommandManager().metaBuilder("xav").aliases("xavpn").plugin(this).build();
        server.getCommandManager().register(meta, new XavCommand());
    }

    @Subscribe
    public CompletableFuture<Void> onPreLogin(final PreLoginEvent event) {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                String name = event.getUsername();
                String ip = event.getConnection().getRemoteAddress() == null || event.getConnection().getRemoteAddress().getAddress() == null
                        ? ""
                        : event.getConnection().getRemoteAddress().getAddress().getHostAddress();
                CheckResult result = core.check(name, ip, false);
                if (!result.isAllowed()) {
                    notifyModerators(name, result);
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(component(core.buildBlockedKickMessage(result))));
                }
            }
        });
    }

    private final class XavCommand implements SimpleCommand {
        @Override
        public void execute(final Invocation invocation) {
            String[] args = invocation.arguments();
            if (args.length == 0) {
                send(invocation, core.prefix() + TextUtil.color(core.getConfig().getUsage()));
                return;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("kivetel".equals(sub)) {
                whitelist(invocation, args, true);
                return;
            }
            if ("eltavolit".equals(sub) || "eltávolít".equals(sub)) {
                whitelist(invocation, args, false);
                return;
            }
            if ("ip".equals(sub)) {
                ip(invocation, args);
                return;
            }
            if ("alt".equals(sub) || "alts".equals(sub)) {
                alt(invocation, args);
                return;
            }
            send(invocation, core.prefix() + TextUtil.color(core.getConfig().getUsage()));
        }

        private void whitelist(Invocation invocation, String[] args, boolean add) {
            if (!invocation.source().hasPermission(XavConfig.PERM_WHITELIST)) {
                send(invocation, core.prefix() + TextUtil.color(core.getConfig().getNoPermission()));
                return;
            }
            if (args.length < 2) {
                send(invocation, core.prefix() + TextUtil.color("&eHasznalat: /xav " + (add ? "kivetel" : "eltavolit") + " <jatekos>"));
                return;
            }
            try {
                boolean changed = add ? core.getWhitelist().add(args[1]) : core.getWhitelist().remove(args[1]);
                send(invocation, core.prefix() + TextUtil.color(changed
                        ? (add ? "&aHozzaadva a kivetellistahoz: " : "&aEltavolitva a kivetellistarol: ") + args[1]
                        : "&eNem tortent valtozas: " + args[1]));
            } catch (IOException exception) {
                send(invocation, core.prefix() + TextUtil.color("&cNem sikerult menteni a whitelistet."));
            }
        }

        private void ip(final Invocation invocation, String[] args) {
            if (!invocation.source().hasPermission(XavConfig.PERM_MOD)) {
                send(invocation, core.prefix() + TextUtil.color(core.getConfig().getNoPermission()));
                return;
            }
            if (args.length < 2) {
                send(invocation, core.prefix() + TextUtil.color("&eHasznalat: /xav ip <jatekos>"));
                return;
            }
            final String target = args[1];
            final String ip = resolveIp(target);
            if (ip == null) {
                send(invocation, core.prefix() + TextUtil.color("&cNincs ismert IP ehhez a jatekoshoz: " + target));
                return;
            }
            send(invocation, core.prefix() + TextUtil.color("&7IPAPI lekerdezes: &f" + ip));
            server.getScheduler().buildTask(XavVelocityPlugin.this, new Runnable() {
                @Override
                public void run() {
                    try {
                        IpApiRecord record = core.lookupForCommand(ip);
                        send(invocation, formatIp(target, record));
                    } catch (IOException exception) {
                        send(invocation, core.prefix() + TextUtil.color("&cAPI hiba: " + exception.getMessage()));
                    }
                }
            }).schedule();
        }

        private void alt(Invocation invocation, String[] args) {
            if (!invocation.source().hasPermission(XavConfig.PERM_MOD)) {
                send(invocation, core.prefix() + TextUtil.color(core.getConfig().getNoPermission()));
                return;
            }
            if (args.length < 2) {
                send(invocation, core.prefix() + TextUtil.color("&eHasznalat: /xav alt <jatekos|ip>"));
                return;
            }
            String ip = looksLikeIp(args[1]) ? args[1] : resolveIp(args[1]);
            if (ip == null) {
                send(invocation, core.prefix() + TextUtil.color("&cNincs ismert IP ehhez: " + args[1]));
                return;
            }
            List<PlayerIndex.Entry> matches = core.getPlayerIndex().findByIp(ip);
            List<String> names = new ArrayList<String>();
            for (PlayerIndex.Entry entry : matches) {
                names.add(entry.getName());
            }
            send(invocation, core.prefix() + TextUtil.color("&7Alt check &f" + ip + "&7: &e" + (names.isEmpty() ? "nincs talalat" : join(names))));
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            String[] args = invocation.arguments();
            if (args.length == 1) {
                return filter(Arrays.asList("kivetel", "eltavolit", "ip", "alt"), args[0]);
            }
            if (args.length == 2) {
                List<String> names = new ArrayList<String>();
                for (Player player : server.getAllPlayers()) {
                    names.add(player.getUsername());
                }
                return filter(names, args[1]);
            }
            return Collections.emptyList();
        }
    }

    private String resolveIp(String playerName) {
        Optional<Player> player = server.getPlayer(playerName);
        if (player.isPresent()) {
            InetSocketAddress address = player.get().getRemoteAddress();
            if (address != null && address.getAddress() != null) {
                String ip = address.getAddress().getHostAddress();
                core.recordPlayer(player.get().getUsername(), ip);
                return ip;
            }
        }
        PlayerIndex.Entry entry = core.getPlayerIndex().get(playerName);
        return entry == null ? null : entry.getIp();
    }

    private String formatIp(String target, IpApiRecord record) {
        List<CheckFlag> matched = new ArrayList<CheckFlag>();
        for (CheckFlag check : core.getConfig().getChecks()) {
            if (record.isFlagged(check.getKey())) {
                matched.add(check);
            }
        }
        CheckResult result = CheckResult.blocked(record, matched);
        return core.prefix() + TextUtil.color("&f" + target + " &7IP: &e" + record.getIp()
                + " &7ISP: &e" + record.getIsp()
                + " &7Kodok: &e" + result.getCodes());
    }

    private void notifyModerators(String playerName, CheckResult result) {
        String message = core.buildModeratorBlockedMessage(playerName, result);
        for (Player player : server.getAllPlayers()) {
            if (player.hasPermission(XavConfig.PERM_MOD)) {
                player.sendMessage(component(message));
            }
        }
        logger.info(LegacyComponentSerializer.legacySection().serialize(component(message)));
    }

    private static void send(SimpleCommand.Invocation invocation, String message) {
        invocation.source().sendMessage(component(message));
    }

    private static Component component(String legacy) {
        return LEGACY.deserialize(legacy);
    }

    private static List<String> filter(List<String> values, String prefix) {
        List<String> matches = new ArrayList<String>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(value);
            }
        }
        return matches;
    }

    private static boolean looksLikeIp(String value) {
        return value.matches("[0-9a-fA-F:.]+");
    }

    private static String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(values.get(index));
        }
        return builder.toString();
    }

    private final class VelocityLogger implements XavLogger {
        @Override
        public void info(String message) {
            logger.info(message);
        }

        @Override
        public void warn(String message) {
            logger.warn(message);
        }

        @Override
        public void warn(String message, Throwable throwable) {
            logger.warn(message, throwable);
        }
    }
}

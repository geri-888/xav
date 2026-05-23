package hu.xavpn.bungee;

import hu.xavpn.common.CheckFlag;
import hu.xavpn.common.CheckResult;
import hu.xavpn.common.IpApiRecord;
import hu.xavpn.common.PlayerIndex;
import hu.xavpn.common.TextUtil;
import hu.xavpn.common.XavConfig;
import hu.xavpn.common.XavCore;
import hu.xavpn.common.XavLogger;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class XavBungeePlugin extends Plugin implements Listener {
    private XavCore core;

    @Override
    public void onEnable() {
        core = new XavCore(getDataFolder(), new BungeeLogger());
        try {
            core.load();
        } catch (IOException exception) {
            getLogger().severe("XAVPN nem tudott betolteni: " + exception.getMessage());
            return;
        }
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new XavCommand());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(final PreLoginEvent event) {
        event.registerIntent(this);
        getProxy().getScheduler().runAsync(this, new Runnable() {
            @Override
            public void run() {
                try {
                    String name = event.getConnection().getName();
                    String ip = event.getConnection().getAddress() == null || event.getConnection().getAddress().getAddress() == null
                            ? ""
                            : event.getConnection().getAddress().getAddress().getHostAddress();
                    CheckResult result = core.check(name, ip, false);
                    if (!result.isAllowed()) {
                        notifyModerators(name, result);
                        event.setCancelled(true);
                        event.setCancelReason(TextComponent.fromLegacyText(core.buildBlockedKickMessage(result)));
                    }
                } finally {
                    event.completeIntent(XavBungeePlugin.this);
                }
            }
        });
    }

    private final class XavCommand extends Command implements TabExecutor {
        private XavCommand() {
            super("xav", null, "xavpn");
        }

        @Override
        public void execute(final CommandSender sender, String[] args) {
            if (args.length == 0) {
                send(sender, core.prefix() + TextUtil.color(core.getConfig().getUsage()));
                return;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("kivetel".equals(sub)) {
                whitelist(sender, args, true);
                return;
            }
            if ("eltavolit".equals(sub) || "eltávolít".equals(sub)) {
                whitelist(sender, args, false);
                return;
            }
            if ("ip".equals(sub)) {
                ip(sender, args);
                return;
            }
            if ("alt".equals(sub) || "alts".equals(sub)) {
                alt(sender, args);
                return;
            }
            send(sender, core.prefix() + TextUtil.color(core.getConfig().getUsage()));
        }

        private void whitelist(CommandSender sender, String[] args, boolean add) {
            if (!sender.hasPermission(XavConfig.PERM_WHITELIST)) {
                send(sender, core.prefix() + TextUtil.color(core.getConfig().getNoPermission()));
                return;
            }
            if (args.length < 2) {
                send(sender, core.prefix() + TextUtil.color("&eHasznalat: /xav " + (add ? "kivetel" : "eltavolit") + " <jatekos>"));
                return;
            }
            try {
                boolean changed = add ? core.getWhitelist().add(args[1]) : core.getWhitelist().remove(args[1]);
                send(sender, core.prefix() + TextUtil.color(changed
                        ? (add ? "&aHozzaadva a kivetellistahoz: " : "&aEltavolitva a kivetellistarol: ") + args[1]
                        : "&eNem tortent valtozas: " + args[1]));
            } catch (IOException exception) {
                send(sender, core.prefix() + TextUtil.color("&cNem sikerult menteni a whitelistet."));
            }
        }

        private void ip(final CommandSender sender, String[] args) {
            if (!sender.hasPermission(XavConfig.PERM_MOD)) {
                send(sender, core.prefix() + TextUtil.color(core.getConfig().getNoPermission()));
                return;
            }
            if (args.length < 2) {
                send(sender, core.prefix() + TextUtil.color("&eHasznalat: /xav ip <jatekos>"));
                return;
            }
            final String target = args[1];
            final String ip = resolveIp(target);
            if (ip == null) {
                send(sender, core.prefix() + TextUtil.color("&cNincs ismert IP ehhez a jatekoshoz: " + target));
                return;
            }
            send(sender, core.prefix() + TextUtil.color("&7IPAPI lekerdezes: &f" + ip));
            getProxy().getScheduler().runAsync(XavBungeePlugin.this, new Runnable() {
                @Override
                public void run() {
                    try {
                        IpApiRecord record = core.lookupForCommand(ip);
                        send(sender, formatIp(target, record));
                    } catch (IOException exception) {
                        send(sender, core.prefix() + TextUtil.color("&cAPI hiba: " + exception.getMessage()));
                    }
                }
            });
        }

        private void alt(CommandSender sender, String[] args) {
            if (!sender.hasPermission(XavConfig.PERM_MOD)) {
                send(sender, core.prefix() + TextUtil.color(core.getConfig().getNoPermission()));
                return;
            }
            if (args.length < 2) {
                send(sender, core.prefix() + TextUtil.color("&eHasznalat: /xav alt <jatekos|ip>"));
                return;
            }
            String ip = looksLikeIp(args[1]) ? args[1] : resolveIp(args[1]);
            if (ip == null) {
                send(sender, core.prefix() + TextUtil.color("&cNincs ismert IP ehhez: " + args[1]));
                return;
            }
            List<PlayerIndex.Entry> matches = core.getPlayerIndex().findByIp(ip);
            List<String> names = new ArrayList<String>();
            for (PlayerIndex.Entry entry : matches) {
                names.add(entry.getName());
            }
            send(sender, core.prefix() + TextUtil.color("&7Alt check &f" + ip + "&7: &e" + (names.isEmpty() ? "nincs talalat" : join(names))));
        }

        @Override
        public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return filter(Arrays.asList("kivetel", "eltavolit", "ip", "alt"), args[0]);
            }
            if (args.length == 2) {
                List<String> names = new ArrayList<String>();
                for (ProxiedPlayer player : ProxyServer.getInstance().getPlayers()) {
                    names.add(player.getName());
                }
                return filter(names, args[1]);
            }
            return Collections.emptyList();
        }
    }

    private String resolveIp(String playerName) {
        ProxiedPlayer player = getProxy().getPlayer(playerName);
        if (player != null && player.getAddress() != null) {
            InetSocketAddress address = player.getAddress();
            if (address.getAddress() != null) {
                String ip = address.getAddress().getHostAddress();
                core.recordPlayer(player.getName(), ip);
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
        for (ProxiedPlayer player : getProxy().getPlayers()) {
            if (player.hasPermission(XavConfig.PERM_MOD)) {
                send(player, message);
            }
        }
        getLogger().info(ChatColor.stripColor(message));
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

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message)));
    }

    private final class BungeeLogger implements XavLogger {
        @Override
        public void info(String message) {
            getLogger().info(message);
        }

        @Override
        public void warn(String message) {
            getLogger().warning(message);
        }

        @Override
        public void warn(String message, Throwable throwable) {
            getLogger().warning(message);
        }
    }
}

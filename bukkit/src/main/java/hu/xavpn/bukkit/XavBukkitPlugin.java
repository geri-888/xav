package hu.xavpn.bukkit;

import hu.xavpn.common.CheckFlag;
import hu.xavpn.common.CheckResult;
import hu.xavpn.common.IpApiRecord;
import hu.xavpn.common.PlayerIndex;
import hu.xavpn.common.TextUtil;
import hu.xavpn.common.XavConfig;
import hu.xavpn.common.XavCore;
import hu.xavpn.common.XavLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class XavBukkitPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<String, CheckResult> pending = new ConcurrentHashMap<String, CheckResult>();
    private XavCore core;

    @Override
    public void onEnable() {
        core = new XavCore(getDataFolder(), new BukkitLogger());
        try {
            core.load();
        } catch (IOException exception) {
            getLogger().severe("XAVPN nem tudott betolteni: " + exception.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("xav") != null) {
            getCommand("xav").setExecutor(this);
            getCommand("xav").setTabCompleter(this);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String ip = event.getAddress() == null ? "" : event.getAddress().getHostAddress();
        CheckResult result = core.check(event.getName(), ip, false);
        pending.put(normalize(event.getName()), result);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        String name = event.getPlayer().getName();
        String ip = event.getAddress() == null ? "" : event.getAddress().getHostAddress();
        core.recordPlayer(name, ip);
        if (event.getPlayer().hasPermission(XavConfig.PERM_BYPASS) || core.isWhitelisted(name)) {
            pending.remove(normalize(name));
            return;
        }
        CheckResult result = pending.remove(normalize(name));
        if (result != null && !result.isAllowed()) {
            notifyModerators(name, result);
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, core.buildBlockedKickMessage(result));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(core.prefix() + TextUtil.color(core.getConfig().getUsage()));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("kivetel".equals(sub)) {
            return whitelist(sender, args, true);
        }
        if ("eltavolit".equals(sub) || "eltávolít".equals(sub)) {
            return whitelist(sender, args, false);
        }
        if ("ip".equals(sub)) {
            return ip(sender, args);
        }
        if ("alt".equals(sub) || "alts".equals(sub)) {
            return alt(sender, args);
        }
        sender.sendMessage(core.prefix() + TextUtil.color(core.getConfig().getUsage()));
        return true;
    }

    private boolean whitelist(final CommandSender sender, String[] args, boolean add) {
        if (!sender.hasPermission(XavConfig.PERM_WHITELIST)) {
            sender.sendMessage(core.prefix() + TextUtil.color(core.getConfig().getNoPermission()));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(core.prefix() + TextUtil.color("&eHasznalat: /xav " + (add ? "kivetel" : "eltavolit") + " <jatekos>"));
            return true;
        }
        try {
            boolean changed = add ? core.getWhitelist().add(args[1]) : core.getWhitelist().remove(args[1]);
            sender.sendMessage(core.prefix() + TextUtil.color(changed
                    ? (add ? "&aHozzaadva a kivetellistahoz: " : "&aEltavolitva a kivetellistarol: ") + args[1]
                    : "&eNem tortent valtozas: " + args[1]));
        } catch (IOException exception) {
            sender.sendMessage(core.prefix() + TextUtil.color("&cNem sikerult menteni a whitelistet."));
        }
        return true;
    }

    private boolean ip(final CommandSender sender, String[] args) {
        if (!sender.hasPermission(XavConfig.PERM_MOD)) {
            sender.sendMessage(core.prefix() + TextUtil.color(core.getConfig().getNoPermission()));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(core.prefix() + TextUtil.color("&eHasznalat: /xav ip <jatekos>"));
            return true;
        }
        final String target = args[1];
        final String ip = resolveIp(target);
        if (ip == null) {
            sender.sendMessage(core.prefix() + TextUtil.color("&cNincs ismert IP ehhez a jatekoshoz: " + target));
            return true;
        }
        sender.sendMessage(core.prefix() + TextUtil.color("&7IPAPI lekerdezes: &f" + ip));
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                final String message;
                try {
                    IpApiRecord record = core.lookupForCommand(ip);
                    message = formatIp(target, record);
                } catch (IOException exception) {
                    final String error = exception.getMessage();
                    Bukkit.getScheduler().runTask(XavBukkitPlugin.this, new Runnable() {
                        @Override
                        public void run() {
                            sender.sendMessage(core.prefix() + TextUtil.color("&cAPI hiba: " + error));
                        }
                    });
                    return;
                }
                Bukkit.getScheduler().runTask(XavBukkitPlugin.this, new Runnable() {
                    @Override
                    public void run() {
                        sender.sendMessage(message);
                    }
                });
            }
        });
        return true;
    }

    private boolean alt(CommandSender sender, String[] args) {
        if (!sender.hasPermission(XavConfig.PERM_MOD)) {
            sender.sendMessage(core.prefix() + TextUtil.color(core.getConfig().getNoPermission()));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(core.prefix() + TextUtil.color("&eHasznalat: /xav alt <jatekos|ip>"));
            return true;
        }
        String ip = looksLikeIp(args[1]) ? args[1] : resolveIp(args[1]);
        if (ip == null) {
            sender.sendMessage(core.prefix() + TextUtil.color("&cNincs ismert IP ehhez: " + args[1]));
            return true;
        }
        List<PlayerIndex.Entry> matches = core.getPlayerIndex().findByIp(ip);
        List<String> names = new ArrayList<String>();
        for (PlayerIndex.Entry entry : matches) {
            names.add(entry.getName());
        }
        sender.sendMessage(core.prefix() + TextUtil.color("&7Alt check &f" + ip + "&7: &e" + (names.isEmpty() ? "nincs talalat" : join(names))));
        return true;
    }

    private String resolveIp(String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(XavConfig.PERM_MOD)) {
                player.sendMessage(message);
            }
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("kivetel", "eltavolit", "ip", "alt"), args[0]);
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(names, args[1]);
        }
        return Collections.emptyList();
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

    private static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
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

    private final class BukkitLogger implements XavLogger {
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

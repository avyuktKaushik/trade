package me.optrade;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class TradeCommand implements CommandExecutor, TabCompleter {

    private final OpTradePlugin plugin;
    private final TradeManager manager;

    public TradeCommand(OpTradePlugin plugin, TradeManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        // plugin.yml already limits this to ops (optrade.use: default op); double-check anyway.
        if (!player.hasPermission("optrade.use")) return true;

        if (args.length != 1) {
            player.sendMessage(plugin.msg("usage"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !player.canSee(target)) {
            player.sendMessage(plugin.msg("not-found"));
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(plugin.msg("self"));
            return true;
        }
        if (manager.isTrading(player)) {
            player.sendMessage(plugin.msg("you-busy"));
            return true;
        }
        if (manager.isTrading(target)) {
            player.sendMessage(plugin.msg("busy", Placeholder.unparsed("player", target.getName())));
            return true;
        }

        manager.start(player, target);
        player.sendMessage(plugin.msg("opened", Placeholder.unparsed("player", target.getName())));
        target.sendMessage(plugin.msg("opened", Placeholder.unparsed("player", player.getName())));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player self)) return List.of();
        String prefix = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(self) && self.canSee(p))
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(prefix))
                .toList();
    }
}

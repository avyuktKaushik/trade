package me.optrade;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class OpTradePlugin extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private TradeManager tradeManager;
    private int countdownSeconds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        countdownSeconds = Math.max(1, getConfig().getInt("countdown-seconds", 5));

        tradeManager = new TradeManager(this);
        getServer().getPluginManager().registerEvents(new TradeListener(tradeManager), this);

        PluginCommand command = getCommand("trade");
        if (command != null) {
            TradeCommand executor = new TradeCommand(this, tradeManager);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        // Hand every item back to its owner so nothing is lost on shutdown.
        if (tradeManager != null) tradeManager.cancelAll("the server is stopping");
    }

    public int countdownSeconds() {
        return countdownSeconds;
    }

    /** A message from config.yml with the prefix in front. */
    public Component msg(String key, TagResolver... resolvers) {
        String prefix = getConfig().getString("messages.prefix", "");
        String body = getConfig().getString("messages." + key, "<red>Missing message: " + key);
        return MM.deserialize(prefix + body, resolvers);
    }

    public Component parse(String path, String fallback, TagResolver... resolvers) {
        return MM.deserialize(getConfig().getString(path, fallback), resolvers);
    }
}

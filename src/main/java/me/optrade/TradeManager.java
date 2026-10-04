package me.optrade;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public final class TradeManager {

    private final OpTradePlugin plugin;
    private final Map<UUID, TradeSession> sessions = new HashMap<>();

    public TradeManager(OpTradePlugin plugin) {
        this.plugin = plugin;
    }

    public OpTradePlugin plugin() {
        return plugin;
    }

    public boolean isTrading(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void start(Player left, Player right) {
        TradeSession session = new TradeSession(this, left, right);
        sessions.put(left.getUniqueId(), session);
        sessions.put(right.getUniqueId(), session);
        session.open();
    }

    void remove(TradeSession session) {
        sessions.remove(session.left().getUniqueId(), session);
        sessions.remove(session.right().getUniqueId(), session);
    }

    public void cancelAll(String reason) {
        for (TradeSession session : new HashSet<>(sessions.values())) {
            session.cancel(reason, true);
        }
        sessions.clear();
    }
}

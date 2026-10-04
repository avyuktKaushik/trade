package me.optrade;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * One trade between two players. Both players look at the SAME inventory:
 *
 *   L L L L | R R R R     rows 0-4: left player's offer (cols 0-3),
 *   L L L L | R R R R               divider (col 4),
 *   L L L L | R R R R               right player's offer (cols 5-8)
 *   L L L L | R R R R
 *   L L L L | R R R R
 *   A H . . S . . H A     row 5: accept buttons (A), heads (H), status (S)
 */
public final class TradeSession implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int[] LEFT_SLOTS = buildSlots(0);
    private static final int[] RIGHT_SLOTS = buildSlots(5);
    private static final int LEFT_ACCEPT = 45, LEFT_HEAD = 46, STATUS = 49, RIGHT_HEAD = 52, RIGHT_ACCEPT = 53;

    private final TradeManager manager;
    private final OpTradePlugin plugin;
    private final Player left;
    private final Player right;
    private final Inventory inventory;

    private boolean leftAccepted;
    private boolean rightAccepted;
    private boolean ended;
    private boolean checkQueued;
    private BukkitTask countdown;
    private ItemStack[] snapshot;

    TradeSession(TradeManager manager, Player left, Player right) {
        this.manager = manager;
        this.plugin = manager.plugin();
        this.left = left;
        this.right = right;
        Component title = plugin.parse("gui-title", "Trade",
                Placeholder.unparsed("left", left.getName()),
                Placeholder.unparsed("right", right.getName()));
        this.inventory = Bukkit.createInventory(this, SIZE, title);
        drawFrame();
        this.snapshot = currentOffers();
    }

    private static int[] buildSlots(int startCol) {
        int[] slots = new int[20];
        int i = 0;
        for (int row = 0; row < 5; row++)
            for (int col = startCol; col < startCol + 4; col++)
                slots[i++] = row * 9 + col;
        return slots;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player left() { return left; }
    public Player right() { return right; }
    public boolean isEnded() { return ended; }

    void open() {
        left.openInventory(inventory);
        right.openInventory(inventory);
    }

    // ---------------------------------------------------------------- slots

    public boolean isOwnSlot(Player player, int slot) {
        int[] own = player.equals(left) ? LEFT_SLOTS : RIGHT_SLOTS;
        for (int s : own) if (s == slot) return true;
        return false;
    }

    public boolean isOwnAcceptButton(Player player, int slot) {
        return slot == (player.equals(left) ? LEFT_ACCEPT : RIGHT_ACCEPT);
    }

    /** Shift-click from the player's own inventory: move the stack onto their side of the GUI. */
    public ItemStack shiftIn(Player player, ItemStack item) {
        int[] own = player.equals(left) ? LEFT_SLOTS : RIGHT_SLOTS;
        ItemStack remaining = item.clone();
        // First top up matching stacks, then use empty slots.
        for (int slot : own) {
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || !existing.isSimilar(remaining)) continue;
            int space = existing.getMaxStackSize() - existing.getAmount();
            if (space <= 0) continue;
            int moved = Math.min(space, remaining.getAmount());
            existing.setAmount(existing.getAmount() + moved);
            inventory.setItem(slot, existing);
            remaining.setAmount(remaining.getAmount() - moved);
            if (remaining.getAmount() <= 0) return null;
        }
        for (int slot : own) {
            ItemStack existing = inventory.getItem(slot);
            if (existing != null && !existing.getType().isAir()) continue;
            inventory.setItem(slot, remaining);
            return null;
        }
        return remaining;
    }

    // ---------------------------------------------------------------- change detection

    private ItemStack[] currentOffers() {
        ItemStack[] out = new ItemStack[LEFT_SLOTS.length + RIGHT_SLOTS.length];
        int i = 0;
        for (int s : LEFT_SLOTS) out[i++] = copy(inventory.getItem(s));
        for (int s : RIGHT_SLOTS) out[i++] = copy(inventory.getItem(s));
        return out;
    }

    private static ItemStack copy(ItemStack item) {
        return (item == null || item.getType().isAir()) ? null : item.clone();
    }

    /** Called after any click/drag that may have touched the offer slots. Runs next tick, once. */
    public void queueChangeCheck() {
        if (checkQueued || ended) return;
        checkQueued = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            checkQueued = false;
            checkForChanges();
        });
    }

    /** @return true if something changed since the last snapshot (and accepts were reset). */
    private boolean checkForChanges() {
        if (ended) return false;
        ItemStack[] now = currentOffers();
        if (Arrays.equals(now, snapshot)) return false;
        snapshot = now;
        if (leftAccepted || rightAccepted || countdown != null) {
            resetAccepts();
            Component msg = plugin.msg("changed");
            left.sendMessage(msg);
            right.sendMessage(msg);
            playBoth(Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f);
        }
        return true;
    }

    // ---------------------------------------------------------------- accepting

    public void toggleAccept(Player player) {
        if (ended) return;
        // Make sure both players are accepting exactly what is in the GUI right now.
        checkForChanges();

        boolean isLeft = player.equals(left);
        boolean nowAccepted = isLeft ? (leftAccepted = !leftAccepted) : (rightAccepted = !rightAccepted);

        Component msg = plugin.msg(nowAccepted ? "accepted" : "unaccepted",
                Placeholder.unparsed("player", player.getName()));
        left.sendMessage(msg);
        right.sendMessage(msg);

        if (!nowAccepted) stopCountdown();
        if (leftAccepted && rightAccepted) startCountdown();

        playBoth(nowAccepted ? Sound.UI_BUTTON_CLICK : Sound.BLOCK_NOTE_BLOCK_BASS, 1.2f);
        drawButtons();
    }

    private void resetAccepts() {
        leftAccepted = false;
        rightAccepted = false;
        stopCountdown();
        drawButtons();
    }

    private void startCountdown() {
        stopCountdown();
        int total = plugin.countdownSeconds();
        Component msg = plugin.msg("countdown-started", Placeholder.unparsed("seconds", String.valueOf(total)));
        left.sendMessage(msg);
        right.sendMessage(msg);

        countdown = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining = total;

            @Override
            public void run() {
                if (ended) return;
                if (remaining <= 0) {
                    complete();
                    return;
                }
                inventory.setItem(STATUS, statusItem(remaining));
                playBoth(Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f + (total - remaining) * 0.15f);
                remaining--;
            }
        }, 0L, 20L);
    }

    private void stopCountdown() {
        if (countdown != null) {
            countdown.cancel();
            countdown = null;
        }
        if (!ended) inventory.setItem(STATUS, statusItem(-1));
    }

    // ---------------------------------------------------------------- ending

    private void complete() {
        // Last-moment safety: anything changed or anyone gone → no trade.
        if (checkForChanges()) return;
        if (!left.isOnline() || !right.isOnline()) {
            cancel("a player left", false);
            return;
        }

        ended = true;
        stopCountdown();
        List<ItemStack> fromLeft = take(LEFT_SLOTS);
        List<ItemStack> fromRight = take(RIGHT_SLOTS);
        manager.remove(this);

        closeFor(left);
        closeFor(right);

        give(left, fromRight);
        give(right, fromLeft);

        left.sendMessage(plugin.msg("completed"));
        right.sendMessage(plugin.msg("completed"));
        playBoth(Sound.ENTITY_PLAYER_LEVELUP, 1.0f);
    }

    /**
     * Cancels the trade and gives every item back to whoever put it in.
     *
     * @param closeNow true to close the GUIs immediately; false to close next tick
     *                 (required when called from inside an InventoryCloseEvent).
     */
    public void cancel(String reason, boolean closeNow) {
        if (ended) return;
        ended = true;
        stopCountdown();
        List<ItemStack> leftItems = take(LEFT_SLOTS);
        List<ItemStack> rightItems = take(RIGHT_SLOTS);
        manager.remove(this);

        give(left, leftItems);
        give(right, rightItems);

        Component msg = plugin.msg("cancelled", Placeholder.unparsed("reason", reason));
        left.sendMessage(msg);
        right.sendMessage(msg);

        if (closeNow) {
            closeFor(left);
            closeFor(right);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                closeFor(left);
                closeFor(right);
            });
        }
    }

    private void closeFor(Player player) {
        if (player.isOnline() && player.getOpenInventory().getTopInventory().equals(inventory)) {
            player.closeInventory();
        }
    }

    private List<ItemStack> take(int[] slots) {
        List<ItemStack> items = new ArrayList<>();
        for (int s : slots) {
            ItemStack item = inventory.getItem(s);
            if (item != null && !item.getType().isAir()) items.add(item);
            inventory.setItem(s, null);
        }
        return items;
    }

    private void give(Player player, List<ItemStack> items) {
        if (items.isEmpty()) return;
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(items.toArray(new ItemStack[0]));
        if (!leftover.isEmpty()) {
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
            player.sendMessage(plugin.msg("inventory-full"));
        }
    }

    // ---------------------------------------------------------------- drawing

    private void drawFrame() {
        ItemStack divider = named(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "));
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int row = 0; row < 5; row++) inventory.setItem(row * 9 + 4, divider);
        for (int s = 45; s < 54; s++) inventory.setItem(s, filler);
        inventory.setItem(LEFT_HEAD, head(left, "◀ " + left.getName() + "'s offer"));
        inventory.setItem(RIGHT_HEAD, head(right, right.getName() + "'s offer ▶"));
        inventory.setItem(STATUS, statusItem(-1));
        drawButtons();
    }

    private void drawButtons() {
        if (ended) return;
        inventory.setItem(LEFT_ACCEPT, button(left, leftAccepted));
        inventory.setItem(RIGHT_ACCEPT, button(right, rightAccepted));
    }

    private ItemStack button(Player owner, boolean accepted) {
        ItemStack item = named(accepted ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                Component.text(owner.getName() + ": " + (accepted ? "ACCEPTED" : "NOT ACCEPTED"),
                        accepted ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD));
        item.editMeta(meta -> meta.lore(List.of(
                plain(accepted ? "Click to take back your accept" : "Click to accept the trade", NamedTextColor.GRAY),
                plain("(only " + owner.getName() + " can click this)", NamedTextColor.DARK_GRAY))));
        return item;
    }

    private ItemStack statusItem(int secondsLeft) {
        if (secondsLeft < 0) {
            ItemStack item = named(Material.CLOCK, Component.text("Waiting for both players to accept", NamedTextColor.YELLOW));
            item.editMeta(meta -> meta.lore(List.of(
                    plain("Put items on your side, then hit your accept button.", NamedTextColor.GRAY),
                    plain("Changing any item resets both accepts.", NamedTextColor.GRAY))));
            return item;
        }
        ItemStack item = named(Material.LIME_STAINED_GLASS_PANE,
                Component.text("Trading in " + secondsLeft + "...", NamedTextColor.GREEN, TextDecoration.BOLD));
        item.setAmount(Math.max(1, Math.min(64, secondsLeft)));
        item.editMeta(meta -> meta.lore(List.of(plain("Change any item to cancel.", NamedTextColor.GRAY))));
        return item;
    }

    private static ItemStack head(Player player, String label) {
        ItemStack item = named(Material.PLAYER_HEAD, Component.text(label, NamedTextColor.GOLD));
        item.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(player));
        return item;
    }

    private static ItemStack named(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(name.decoration(TextDecoration.ITALIC, false)));
        return item;
    }

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private void playBoth(Sound sound, float pitch) {
        left.playSound(left.getLocation(), sound, 1f, pitch);
        right.playSound(right.getLocation(), sound, 1f, pitch);
    }
}

package me.optrade;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

public final class TradeListener implements Listener {

    /** Things a player may do to a slot on their own side of the GUI. */
    private static final Set<InventoryAction> ALLOWED_IN_GUI = EnumSet.of(
            InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_ONE, InventoryAction.PICKUP_SOME,
            InventoryAction.PLACE_ALL, InventoryAction.PLACE_ONE, InventoryAction.PLACE_SOME,
            InventoryAction.SWAP_WITH_CURSOR, InventoryAction.MOVE_TO_OTHER_INVENTORY,
            InventoryAction.HOTBAR_SWAP);

    private final TradeManager manager;

    public TradeListener(TradeManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof TradeSession session)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (session.isEnded()) {
            event.setCancelled(true);
            return;
        }

        int raw = event.getRawSlot();
        InventoryAction action = event.getAction();

        // Double-click "collect to cursor" can pull items from anywhere, including the other side.
        if (action == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        if (raw >= 0 && raw < top.getSize()) {
            // Clicked inside the trade GUI.
            if (session.isOwnAcceptButton(player, raw)) {
                event.setCancelled(true);
                session.toggleAccept(player);
                return;
            }
            if (!session.isOwnSlot(player, raw) || !ALLOWED_IN_GUI.contains(action)) {
                event.setCancelled(true);
                return;
            }
            session.queueChangeCheck();
            return;
        }

        if (raw >= top.getSize() && action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            // Shift-click from their own inventory: we place it on THEIR side ourselves,
            // otherwise vanilla would drop it into the first free slot (possibly the other side).
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) return;
            ItemStack remainder = session.shiftIn(player, clicked);
            event.setCurrentItem(remainder);
            session.queueChangeCheck();
        }
        // Any other click in their own inventory is harmless.
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof TradeSession session)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (session.isEnded()) {
            event.setCancelled(true);
            return;
        }

        boolean touchesGui = false;
        for (int raw : event.getRawSlots()) {
            if (raw < top.getSize()) {
                if (!session.isOwnSlot(player, raw)) {
                    event.setCancelled(true);
                    return;
                }
                touchesGui = true;
            }
        }
        if (touchesGui) session.queueChangeCheck();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeSession session)) return;
        if (session.isEnded()) return;
        // Closing the GUI (Esc, logging out, being kicked, dying...) cancels the trade.
        session.cancel(event.getPlayer().getName() + " closed the trade", false);
    }
}

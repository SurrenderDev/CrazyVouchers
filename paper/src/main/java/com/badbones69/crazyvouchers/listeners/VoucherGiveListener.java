package com.badbones69.crazyvouchers.listeners;

import com.badbones69.crazyvouchers.CrazyVouchers;
import com.badbones69.crazyvouchers.api.objects.Voucher;
import com.badbones69.crazyvouchers.utils.VoucherAntiDupeManager;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class VoucherGiveListener implements Listener {

    private final CrazyVouchers plugin;
    private final VoucherAntiDupeManager antiDupe;

    public VoucherGiveListener(CrazyVouchers plugin, VoucherAntiDupeManager antiDupe) {
        this.plugin   = plugin;
        this.antiDupe = antiDupe;
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase();
        if (msg.startsWith("/giveall")) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                plugin.getServer().getOnlinePlayers().forEach(this::tagNewVouchers), 1L);

        } else if (msg.startsWith("/give ")) {
            String[] parts = msg.split(" ");
            if (parts.length >= 2) {
                String target = parts[1];
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (target.equalsIgnoreCase("@a")) {
                        plugin.getServer().getOnlinePlayers().forEach(this::tagNewVouchers);
                    } else {
                        Player p = plugin.getServer().getPlayerExact(target);
                        if (p != null) tagNewVouchers(p);
                    }
                }, 1L);
            }
        }
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        Item dropped = event.getItem();
        ItemStack stack = dropped.getItemStack();
        if (stack == null) return;

        if (!Voucher.hasVoucherID(stack) && looksLikeVoucher(stack)) {
            tagSingleVoucher(event.getPlayer(), dropped, stack);
        }
    }

    private void tagNewVouchers(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) continue;
            if (!Voucher.hasVoucherID(stack) && looksLikeVoucher(stack)) {
                tagSingleVoucher(player, null, stack);
            }
        }
    }

    private boolean looksLikeVoucher(ItemStack stack) {
        // adjust this as needed—default to PAPER
        return stack.getType() == Material.PAPER;
    }

    private void tagSingleVoucher(Player player, Item dropped, ItemStack stack) {
        String orig    = player.getName();
        String date    = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String time    = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss a"));
        String uuid    = UUID.randomUUID().toString();

        Voucher.setOriginalPlayer(stack, orig);
        Voucher.setCreationDate(stack, date);
        Voucher.setCreationTime(stack, time);
        Voucher.setVoucherID(stack, uuid);

        antiDupe.addVoucher(uuid, orig, date, time);

        if (dropped != null) {
            dropped.setItemStack(stack);
        }
    }
}

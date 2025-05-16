package com.badbones69.crazyvouchers.listeners;

import ch.jalu.configme.SettingsManager;
import com.badbones69.crazyvouchers.CrazyVouchers;
import com.badbones69.crazyvouchers.Methods;
import com.badbones69.crazyvouchers.api.CrazyManager;
import com.badbones69.crazyvouchers.api.enums.FileKeys;
import com.badbones69.crazyvouchers.api.enums.config.Messages;
import com.badbones69.crazyvouchers.api.enums.misc.PersistentKeys;
import com.badbones69.crazyvouchers.api.enums.misc.PermissionKeys;
import com.badbones69.crazyvouchers.api.events.VoucherRedeemEvent;
import com.badbones69.crazyvouchers.api.objects.Voucher;
import com.badbones69.crazyvouchers.api.objects.VoucherCommand;
import com.badbones69.crazyvouchers.config.ConfigManager;
import com.badbones69.crazyvouchers.config.types.ConfigKeys;
import com.badbones69.crazyvouchers.utils.ScheduleUtils;
import com.badbones69.crazyvouchers.utils.VoucherAntiDupeManager;
import com.ryderbelserion.fusion.paper.FusionPaper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.persistence.PersistentDataContainerView;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoucherClickListener implements Listener {

    private final CrazyVouchers plugin         = CrazyVouchers.get();
    private final CrazyManager crazyManager    = plugin.getCrazyManager();
    private final Server server                = plugin.getServer();
    private final FusionPaper fusion           = plugin.getFusion();
    private final SettingsManager config       = ConfigManager.getConfig();
    private final VoucherAntiDupeManager antiDupe = plugin.getAntiDupeManager();

    // Configurable via config.yml
    private final String duplicateMsg = plugin.getConfig()
        .getString("anti-dupe.duplicate-message", "&cThis voucher has already been redeemed!");
    private final String loreMarker  = plugin.getConfig()
        .getString("anti-dupe.lore-marker", "&4&lDUPED ITEM");
    private final String webhookUrl  = plugin.getConfig()
        .getString("discord-webhook-url", "");

    // Two‐step auth & placeholder map
    private final Map<UUID, String> twoAuth       = new HashMap<>();
    private final Map<String, String> placeholders = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawnerChange(PlayerInteractEvent event) {
        ItemStack item = getItemInHand(event.getPlayer());
        Player player  = event.getPlayer();
        Action action  = event.getAction();
        Block block    = event.getClickedBlock();

        if (action != Action.RIGHT_CLICK_BLOCK) return;
        if (block == null || block.getType() != Material.SPAWNER) return;
        if (!item.getType().toString().endsWith("SPAWN_EGG")) return;

        PlayerInventory inv = player.getInventory();
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            if (crazyManager.getVoucherFromItem(inv.getItemInOffHand()) != null) {
                event.setCancelled(true);
            }
        }
        if (crazyManager.getVoucherFromItem(inv.getItemInMainHand()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVoucherClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        PlayerInventory inv = player.getInventory();

        // Off‐hand prevention
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            ItemStack off = inv.getItemInOffHand();
            if (crazyManager.getVoucherFromItem(off) != null && !crazyManager.getVoucherFromItem(off).isEdible()) {
                event.setCancelled(true);
                Messages.no_permission_to_use_voucher_offhand.sendMessage(player);
            }
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) return;
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = inv.getItemInMainHand();
        com.badbones69.crazyvouchers.api.objects.Voucher voucher = crazyManager.getVoucherFromItem(item);

        if (voucher != null && !voucher.isEdible()) {
            event.setCancelled(true);
            useVoucher(player, voucher, item);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        com.badbones69.crazyvouchers.api.objects.Voucher voucher = crazyManager.getVoucherFromItem(item);

        if (voucher != null && voucher.isEdible()) {
            Player player = event.getPlayer();
            event.setCancelled(true);

            if (item.getAmount() > 1) {
                Messages.unstack_item.sendMessage(player);
            } else {
                useVoucher(player, voucher, item);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStandClick(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.HAND &&
            crazyManager.getVoucherFromItem(getItemInHand(event.getPlayer())) != null) {
            event.setCancelled(true);
        }
    }

    private void useVoucher(Player player, com.badbones69.crazyvouchers.api.objects.Voucher voucher, ItemStack item) {
        FileConfiguration userCfg = FileKeys.users.getConfiguration();
        String argument = crazyManager.getArgument(item, voucher);

        // Survival‐only check
        if (player.getGameMode() == GameMode.CREATIVE &&
            config.getProperty(ConfigKeys.must_be_in_survival)) {
            Messages.survival_mode.sendMessage(player);
            return;
        }

        // JSON‐based anti‐dupe
        if (config.getProperty(ConfigKeys.dupe_protection)) {
            PersistentDataContainerView view = item.getPersistentDataContainer();
            if (view.has(PersistentKeys.dupe_protection.getNamespacedKey())) {
                String id = view.get(PersistentKeys.dupe_protection.getNamespacedKey(),
                                     PersistentDataType.STRING);
                if (antiDupe.isRedeemed(id)) {
                    // 1) Add DUPED lore
                    ItemLore.Builder loreBuilder = ItemLore.lore();
                    ItemLore existing = item.getData(DataComponentTypes.LORE);
                    if (existing != null) loreBuilder.addLines(existing.lines());
                    loreBuilder.addLine(fusion.color(player, loreMarker, placeholders));
                    item.setData(DataComponentTypes.LORE, loreBuilder.build());

                    // 2) Alert player
                    player.sendMessage(fusion.color(player, duplicateMsg, placeholders));

                    // 3) Send Discord embed
                    sendWebhook(id, player, item);
                    return;
                } else {
                    antiDupe.markRedeemed(id);
                }
            }
        }

        // Permission & world checks
        if (!passesPermissionChecks(player, voucher, argument)) return;

        // Two‐step authentication
        if (!voucher.isEdible() && voucher.useTwoStepAuthentication()) {
            UUID uuid = player.getUniqueId();
            if (twoAuth.containsKey(uuid) &&
                twoAuth.get(uuid).equalsIgnoreCase(voucher.getName())) {
                twoAuth.remove(uuid);
            } else {
                Messages.two_step_authentication.sendMessage(player);
                twoAuth.put(uuid, voucher.getName());
                return;
            }
        }

        // Fire redeem event
        twoAuth.remove(player.getUniqueId());
        VoucherRedeemEvent evt = new VoucherRedeemEvent(player, voucher, argument);
        server.getPluginManager().callEvent(evt);
        if (evt.isCancelled()) return;

        // Actual redemption
        voucherClick(player, item, voucher, argument);
    }

    private ItemStack getItemInHand(Player player) {
        return player.getInventory().getItemInMainHand();
    }

    private boolean passesPermissionChecks(Player player, com.badbones69.crazyvouchers.api.objects.Voucher voucher, String argument) {
        populate(player, argument);

        if (!player.isOp()) {
            if (voucher.useWhiteListPermissions()) {
                return voucher.hasPermission(true, player,
                        voucher.getWhitelistPermissions(),
                        voucher.getWhitelistCommands(),
                        placeholders,
                        voucher.getWhitelistPermissionMessage(),
                        argument);
            }

            if (voucher.usesWhitelistWorlds() &&
                !voucher.getWhitelistWorlds().contains(player.getWorld().getName().toLowerCase())) {
                player.sendMessage(fusion.color(player, voucher.getWhitelistWorldMessage(), placeholders));
                ScheduleUtils.dispatch(c -> {
                    for (String cmd : voucher.getWhitelistWorldCommands()) {
                        server.dispatchCommand(server.getConsoleSender(), Methods.placeholders(player, cmd, placeholders));
                    }
                });
                return false;
            }

            if (voucher.useBlackListPermissions()) {
                return voucher.hasPermission(true, player,
                        voucher.getBlackListPermissions(),
                        voucher.getBlacklistCommands(),
                        placeholders,
                        voucher.getBlackListMessage(),
                        argument);
            }
        }

        return true;
    }

    private void populate(Player player, String argument) {
        placeholders.put("{arg}", argument != null ? argument : "");
        placeholders.put("{player}", player.getName());
        placeholders.put("{world}", player.getWorld().getName());
        Location loc = player.getLocation();
        placeholders.put("{x}", String.valueOf(loc.getBlockX()));
        placeholders.put("{y}", String.valueOf(loc.getBlockY()));
        placeholders.put("{z}", String.valueOf(loc.getBlockZ()));
        placeholders.put("{prefix}", ConfigManager.getConfig().getProperty(ConfigKeys.command_prefix));
    }

    private void voucherClick(Player player, ItemStack item, com.badbones69.crazyvouchers.api.objects.Voucher voucher, String argument) {
        Methods.removeItem(item, player);

        if (voucher.hasCooldown()) {
            voucher.addCooldown(player);
        }

        populate(player, argument);

        // Dispatch commands
        ScheduleUtils.dispatch(c -> {
            for (String cmd : voucher.getCommands()) {
                server.dispatchCommand(server.getConsoleSender(),
                    Methods.placeholders(player, crazyManager.replaceRandom(cmd), placeholders));
            }
            // Random commands
            for (VoucherCommand rc : voucher.getRandomCommands()) {
                for (String cmd : rc.getCommands()) {
                    server.dispatchCommand(server.getConsoleSender(),
                        Methods.placeholders(player, crazyManager.replaceRandom(cmd), placeholders));
                }
            }
            // Chance commands
            for (VoucherCommand cc : voucher.getChanceCommands()) {
                for (String cmd : cc.getCommands()) {
                    server.dispatchCommand(server.getConsoleSender(),
                        Methods.placeholders(player, crazyManager.replaceRandom(cmd), placeholders));
                }
            }
        });

        // Give reward items
        for (ItemStack reward : voucher.getItems().stream().map(b -> b.asItemStack()).toList()) {
            Methods.addItem(player, reward);
        }

        // Play sounds
        if (voucher.playSounds()) {
            for (Sound sound : voucher.getSounds()) {
                player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, voucher.getVolume(), voucher.getPitch());
            }
        }

        // Fireworks
        if (voucher.useFirework()) {
            Methods.firework(player.getLocation(), voucher.getFireworkColors());
        }

        // Used message
        String msg = voucher.getVoucherUsedMessage();
        if (!msg.isEmpty()) {
            player.sendMessage(fusion.color(player, msg, placeholders));
        }

        // Limiter
        if (voucher.useLimiter()) {
            UUID uuid = player.getUniqueId();
            FileConfiguration users = FileKeys.users.getConfiguration();
            users.set("Players." + uuid + ".UserName", player.getName());
            users.set("Players." + uuid + ".Vouchers." + voucher.getName(),
                      users.getInt("Players." + uuid + ".Vouchers." + voucher.getName()) + 1);
            FileKeys.users.save();
        }
    }

    private void sendWebhook(String id, Player redeemer, ItemStack item) {
        if (webhookUrl.isEmpty()) return;

        String original = antiDupe.getOriginalPlayer(id);
        String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
            ? fusion.plainText(item.getItemMeta().displayName())
            : item.getType().toString();
        int amount = item.getAmount();

        String json = "{"
            + "\"embeds\":[{"
            + "\"title\":\"Duplicate Voucher Attempted\","
            + "\"color\":16711680,"
            + "\"fields\":["
            + field("Original Player", original) + ","
            + field("Redeemer", redeemer.getName()) + ","
            + field("Voucher", name) + ","
            + field("Amount", String.valueOf(amount))
            + "]"
            + "}]}";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(webhookUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes());
                }
                conn.getInputStream().close();
                conn.disconnect();
            } catch (Exception e) {
                plugin.getLogger().severe("Webhook failed: " + e.getMessage());
            }
        });
    }

    private String field(String name, String value) {
        return "{\"name\":\"" + name + "\",\"value\":\"" + value + "\",\"inline\":false}";
    }
}

package com.badbones69.crazyvouchers.api.objects;

import ch.jalu.configme.SettingsManager;
import com.badbones69.crazyvouchers.CrazyVouchers;
import com.badbones69.crazyvouchers.Methods;
import com.badbones69.crazyvouchers.api.enums.FileSystem;
import com.badbones69.crazyvouchers.api.enums.config.Messages;
import com.badbones69.crazyvouchers.api.enums.misc.PersistentKeys;
import com.badbones69.crazyvouchers.config.ConfigManager;
import com.badbones69.crazyvouchers.config.types.ConfigKeys;
import com.badbones69.crazyvouchers.utils.ItemUtils;
import com.badbones69.crazyvouchers.utils.VoucherAntiDupeManager;
import com.ryderbelserion.fusion.core.utils.StringUtils;
import com.ryderbelserion.fusion.paper.api.builder.items.modern.ItemBuilder;
import com.ryderbelserion.fusion.paper.utils.ColorUtils;
import org.bukkit.Color;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.persistence.PersistentDataType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

public class Voucher {

    private final ItemBuilder itemBuilder;
    private final String name;
    private final boolean hasCooldown;
    private final int cooldownInterval;
    private boolean usesArgs;
    private final boolean glowing;
    private final String usedMessage;
    private final boolean whitelistPermissionToggle;
    private final List<String> whitelistPermissions = new ArrayList<>();
    private List<String> whitelistCommands = new ArrayList<>();
    private String whitelistPermissionMessage;
    private final boolean whitelistWorldsToggle;
    private String whitelistWorldMessage;
    private final List<String> whitelistWorlds = new ArrayList<>();
    private List<String> whitelistWorldCommands = new ArrayList<>();
    private final boolean blacklistPermissionsToggle;
    private String blacklistPermissionMessage;
    private List<String> blacklistCommands = new ArrayList<>();
    private List<String> blacklistPermissions = new ArrayList<>();
    private final boolean limiterToggle;
    private int limiterLimit;
    private final boolean twoStepAuthentication;
    private final boolean soundToggle;
    private float volume;
    private float pitch;
    private final List<Sound> sounds = new ArrayList<>();
    private final boolean fireworkToggle;
    private final List<Color> fireworkColors = new ArrayList<>();
    private boolean isEdible;
    private List<String> commands = new ArrayList<>();
    private final List<VoucherCommand> randomCommands = new ArrayList<>();
    private final List<VoucherCommand> chanceCommands = new ArrayList<>();
    private final Map<String, String> requiredPlaceholders = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private List<ItemBuilder> items = new ArrayList<>();
    private String requiredPlaceholdersMessage;

    private final SettingsManager config = ConfigManager.getConfig();
    private final CrazyVouchers plugin = CrazyVouchers.get();
    private final VoucherAntiDupeManager antiDupe = plugin.getAntiDupeManager();

    public Voucher(int number) {
        this.name = Integer.toString(number);
        this.usesArgs = false;
        this.itemBuilder = ItemBuilder.from(ItemType.STONE).setDisplayName(this.name);
        this.usedMessage = "";
        this.whitelistPermissionToggle = false;
        this.whitelistPermissionMessage = "";
        this.whitelistWorldsToggle = false;
        this.whitelistWorldMessage = "";
        this.blacklistPermissionsToggle = false;
        this.blacklistPermissionMessage = "";
        this.limiterToggle = false;
        this.limiterLimit = 0;
        this.twoStepAuthentication = false;
        this.soundToggle = false;
        this.volume = 1f;
        this.pitch = 1f;
        this.fireworkToggle = false;
        this.isEdible = false;
        this.glowing = false;
        this.hasCooldown = false;
        this.cooldownInterval = 0;
    }

    public Voucher(FileConfiguration fileConfiguration, String name) {
        this.name = name.replaceAll("\\.yml$", "");
        this.usesArgs = false;

        FileSystem system = config.getProperty(ConfigKeys.file_system);
        String path = system == FileSystem.SINGLE
                ? "vouchers." + name + "."
                : "voucher.";

        this.hasCooldown = fileConfiguration.getBoolean(path + "cooldown.toggle", false);
        this.cooldownInterval = fileConfiguration.getInt(path + "cooldown.interval", 5);

        this.itemBuilder = ItemBuilder
                .from(fileConfiguration.getString(path + "item", "stone").toLowerCase())
                .setDisplayName(fileConfiguration.getString(path + "name", ""))
                .withDisplayLore(fileConfiguration.getStringList(path + "lore"));

        if (fileConfiguration.contains(path + "player")) {
            String playerName = fileConfiguration.getString(path + "player", "");
            if (!playerName.isBlank()) {
                this.itemBuilder.asSkullBuilder().withName(playerName).build();
            }
        }

        if (fileConfiguration.contains(path + "display-damage")) {
            this.itemBuilder.setItemDamage(fileConfiguration.getInt(path + "display-damage"));
        }

        if (fileConfiguration.contains(path + "display-trim.material")
         && fileConfiguration.contains(path + "display-trim.pattern")) {
            String mat = fileConfiguration.getString(path + "display-trim.material", "quartz").toLowerCase();
            String pat = fileConfiguration.getString(path + "display-trim.pattern", "sentry").toLowerCase();
            this.itemBuilder.setTrim(pat, mat);
        }

        if (fileConfiguration.contains(path + "skull")) {
            this.itemBuilder.withSkull(fileConfiguration.getString(path + "skull", ""));
        }

        this.glowing = fileConfiguration.getBoolean(path + "glowing");

        // detect {arg} usage
        if (this.itemBuilder.getPlainName().toLowerCase().contains("{arg}")) this.usesArgs = true;
        if (!this.usesArgs) {
            for (String lore : this.itemBuilder.getPlainLore()) {
                if (lore.toLowerCase().contains("{arg}")) {
                    this.usesArgs = true;
                    break;
                }
            }
        }

        if (fileConfiguration.contains(path + "commands")) {
            this.commands = fileConfiguration.getStringList(path + "commands");
        }
        if (fileConfiguration.contains(path + "random-commands")) {
            for (String cmd : fileConfiguration.getStringList(path + "random-commands")) {
                this.randomCommands.add(new VoucherCommand(cmd));
            }
        }
        if (fileConfiguration.contains(path + "chance-commands")) {
            for (String line : fileConfiguration.getStringList(path + "chance-commands")) {
                try {
                    String[] split = line.split(" ");
                    VoucherCommand vc = new VoucherCommand(line.substring(split[0].length() + 1));
                    int chance = Integer.parseInt(split[0]);
                    for (int i = 0; i < chance; i++) {
                        this.chanceCommands.add(vc);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Error parsing chance-commands", e);
                }
            }
        }

        // items
        if (config.getProperty(ConfigKeys.use_different_items_layout)
         && !fileConfiguration.isList("items")) {
            this.items = ItemUtils.convertConfigurationSection(
                    fileConfiguration.getConfigurationSection("items"));
        } else {
            this.items = ItemUtils.convertStringList(
                    fileConfiguration.getStringList(path + "items"));
        }

        this.usedMessage = getMessage(path + "options.message", fileConfiguration);

        // whitelist perms
        if (fileConfiguration.contains(path + "options.permission.whitelist-permission")) {
            this.whitelistPermissionToggle = fileConfiguration.getBoolean(
                    path + "options.permission.whitelist-permission.toggle");
            if (fileConfiguration.contains(path + "options.permission.whitelist-permission.node")) {
                this.whitelistPermissions.add(
                        fileConfiguration.getString(path + "options.permission.whitelist-permission.node")
                                .toLowerCase());
            }
            this.whitelistPermissions.addAll(
                    fileConfiguration.getStringList(path + "options.permission.whitelist-permission.permissions")
                            .stream().map(String::toLowerCase).toList());
            this.whitelistCommands = fileConfiguration.getStringList(
                    path + "options.permission.whitelist-permission.commands");
            this.whitelistPermissionMessage = fileConfiguration.contains(
                            path + "options.permission.whitelist-permission.message")
                    ? getMessage(path + "options.permission.whitelist-permission.message", fileConfiguration)
                    : Messages.no_permission_to_use_voucher.getString();
        } else {
            this.whitelistPermissionToggle = false;
        }

        // whitelist worlds
        if (fileConfiguration.contains(path + "options.whitelist-worlds.toggle")) {
            this.whitelistWorlds = fileConfiguration.getStringList(
                    path + "options.whitelist-worlds.worlds")
                    .stream().map(String::toLowerCase).toList();
            this.whitelistWorldsToggle = fileConfiguration.getBoolean(
                    path + "options.whitelist-worlds.toggle");
            this.whitelistWorldMessage = fileConfiguration.contains(
                    path + "options.whitelist-worlds.message")
                    ? getMessage(path + "options.whitelist-worlds.message", fileConfiguration)
                    : Messages.not_in_whitelisted_world.getString();
            this.whitelistWorldCommands = fileConfiguration.getStringList(
                    path + "options.whitelist-worlds.commands");
        } else {
            this.whitelistWorldsToggle = false;
        }

        // blacklist perms
        if (fileConfiguration.contains(path + "options.permission.blacklist-permission")) {
            this.blacklistPermissionsToggle = fileConfiguration.getBoolean(
                    path + "options.permission.blacklist-permission.toggle");
            this.blacklistPermissionMessage = fileConfiguration.contains(
                            path + "options.permission.blacklist-permission.message")
                    ? getMessage(path + "options.permission.blacklist-permission.message", fileConfiguration)
                    : Messages.has_blacklist_permission.getString();
            this.blacklistPermissions = fileConfiguration.getStringList(
                    path + "options.permission.blacklist-permission.permissions");
            this.blacklistCommands = fileConfiguration.getStringList(
                    path + "options.permission.blacklist-permission.commands");
        } else {
            this.blacklistPermissionsToggle = false;
        }

        // limiter
        if (fileConfiguration.contains(path + "options.limiter")) {
            this.limiterToggle = fileConfiguration.getBoolean(path + "options.limiter.toggle");
            this.limiterLimit = fileConfiguration.getInt(path + "options.limiter.limit");
        } else {
            this.limiterToggle = false;
        }

        // required placeholders
        if (fileConfiguration.contains(path + "options.required-placeholders")) {
            this.requiredPlaceholdersMessage = getMessage(
                    path + "options.required-placeholders-message", fileConfiguration);
            fileConfiguration.getConfigurationSection(path + "options.required-placeholders")
                    .getKeys(false)
                    .forEach(key -> {
                        String ph = fileConfiguration.getString(path + "options.required-placeholders." + key + ".placeholder");
                        String val = fileConfiguration.getString(path + "options.required-placeholders." + key + ".value");
                        this.requiredPlaceholders.put(ph, val);
                    });
        }

        this.twoStepAuthentication = fileConfiguration.getBoolean(path + "options.two-step-authentication", false);

        // sounds
        if (fileConfiguration.contains(path + "options.sound")) {
            this.soundToggle = fileConfiguration.getBoolean(path + "options.sound.toggle");
            this.volume = (float) fileConfiguration.getDouble(path + "options.sound.volume");
            this.pitch = (float) fileConfiguration.getDouble(path + "options.sound.pitch");
            fileConfiguration.getStringList(path + "options.sound.sounds")
                    .forEach(s -> this.sounds.add(
                            com.ryderbelserion.fusion.paper.utils.ItemUtils.getSound(s)));
        } else {
            this.soundToggle = false;
        }

        // hide tooltips & item model
        if (fileConfiguration.getBoolean(path + "components.hide-tooltip", false)) {
            this.itemBuilder.hideToolTip();
        }
        if (fileConfiguration.contains(path + "components.hide-tooltip-advanced")) {
            this.itemBuilder.hideComponents(
                    fileConfiguration.getStringList(path + "components.hide-tooltip-advanced"));
        }
        if (fileConfiguration.contains(path + "components.item-model.namespace")
         && fileConfiguration.contains(path + "components.item-model.key")) {
            this.itemBuilder.setItemModel(
                    fileConfiguration.getString(path + "components.item-model.namespace", ""),
                    fileConfiguration.getString(path + "components.item-model.key", "")
            );
        }

        // firework
        if (fileConfiguration.getBoolean(path + "options.firework.toggle")) {
            String colors = fileConfiguration.getString(path + "options.firework.colors", "");
            for (String c : colors.split(",\\s*")) {
                this.fireworkColors.add(ColorUtils.getColor(c));
            }
            this.fireworkToggle = !this.fireworkColors.isEmpty();
        } else {
            this.fireworkToggle = false;
        }

        // edible
        this.isEdible = fileConfiguration.getBoolean(path + "options.is-edible", false);
    }

    public String getName() {
        return this.name;
    }

    public boolean usesArguments() {
        return this.usesArgs;
    }

    public ItemStack buildItem() {
        return buildItem("", 1);
    }

    public ItemStack buildItem(int amount) {
        return buildItem("", amount);
    }

    public List<ItemStack> buildItems(String argument, int amount) {
        List<ItemStack> out = new ArrayList<>();
        if (config.getProperty(ConfigKeys.dupe_protection)) {
            for (int i = 0; i < amount; i++) {
                out.add(buildItem(argument, 1));
            }
        } else {
            out.add(buildItem(argument, amount));
        }
        return out;
    }

    public ItemStack buildItem(String argument, int amount) {
        ItemStack item = itemBuilder
                .setAmount(amount)
                .addPlaceholder("{arg}", argument)
                .setEnchantGlint(glowing)
                .asItemStack();

        // set voucher-item key
        item.editPersistentDataContainer(container ->
                container.set(PersistentKeys.voucher_item.getNamespacedKey(),
                              PersistentDataType.STRING, getName()));

        // set dupe-protection + metadata
        if (config.getProperty(ConfigKeys.dupe_protection)) {
            String uuid = UUID.randomUUID().toString();
            // store the unique id
            item.editPersistentDataContainer(c ->
                    c.set(PersistentKeys.dupe_protection.getNamespacedKey(),
                          PersistentDataType.STRING, uuid));

            // capture timestamps
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss a"));

            // NBT metadata
            com.badbones69.crazyvouchers.api.objects.Voucher.setVoucherID(item, uuid);
            com.badbones69.crazyvouchers.api.objects.Voucher.setOriginalPlayer(item, "None");
            com.badbones69.crazyvouchers.api.objects.Voucher.setCreationDate(item, date);
            com.badbones69.crazyvouchers.api.objects.Voucher.setCreationTime(item, time);

            // record in JSON
            antiDupe.addVoucher(uuid, "None", date, time);
        }

        return item;
    }

    // cooldown methods
    public boolean hasCooldown() { return this.hasCooldown; }
    public int getCooldown() { return this.cooldownInterval; }
    public boolean isCooldown(Player p) {
        return cooldowns.getOrDefault(p.getUniqueId(), 0L) >= System.currentTimeMillis();
    }
    public void addCooldown(Player p) {
        cooldowns.put(p.getUniqueId(),
                System.currentTimeMillis() + (1000L * getCooldown()));
    }
    public void removeCooldown(Player p) {
        cooldowns.remove(p.getUniqueId());
    }

    // permission & message helpers delegate to Methods
    public boolean hasPermission(boolean exec, Player p,
                                 List<String> perms, List<String> cmds,
                                 Map<String,String> ph, String msg, String arg) {
        return Methods.hasPermission(exec, p, perms, cmds, ph, msg, arg);
    }

    // getters for messages, lists, etc.
    public String getVoucherUsedMessage() { return this.usedMessage; }
    public boolean useWhiteListPermissions() { return this.whitelistPermissionToggle; }
    public List<String> getWhitelistPermissions() { return this.whitelistPermissions; }
    public List<String> getWhitelistCommands() { return this.whitelistCommands; }
    public String getWhitelistPermissionMessage() { return this.whitelistPermissionMessage; }
    public boolean usesWhitelistWorlds() { return this.whitelistWorldsToggle; }
    public List<String> getWhitelistWorlds() { return this.whitelistWorlds; }
    public String getWhitelistWorldMessage() { return this.whitelistWorldMessage; }
    public List<String> getWhitelistWorldCommands() { return this.whitelistWorldCommands; }
    public boolean useBlackListPermissions() { return this.blacklistPermissionsToggle; }
    public List<String> getBlackListPermissions() { return this.blacklistPermissions; }
    public String getBlackListMessage() { return this.blacklistPermissionMessage; }
    public List<String> getBlacklistCommands() { return this.blacklistCommands; }
    public boolean useLimiter() { return this.limiterToggle; }
    public int getLimiterLimit() { return this.limiterLimit; }
    public boolean useTwoStepAuthentication() { return this.twoStepAuthentication; }
    public boolean playSounds() { return this.soundToggle; }
    public List<Sound> getSounds() { return this.sounds; }
    public float getPitch() { return this.pitch; }
    public float getVolume() { return this.volume; }
    public boolean useFirework() { return this.fireworkToggle; }
    public List<Color> getFireworkColors() { return this.fireworkColors; }
    public List<String> getCommands() { return this.commands; }
    public List<VoucherCommand> getRandomCommands() { return this.randomCommands; }
    public List<VoucherCommand> getChanceCommands() { return this.chanceCommands; }
    public List<ItemBuilder> getItems() { return this.items; }
    public Map<String,String> getRequiredPlaceholders() { return this.requiredPlaceholders; }
    public String getRequiredPlaceholdersMessage() { return this.requiredPlaceholdersMessage; }

    // utility for config messages
    private String getMessage(String path, FileConfiguration file) {
        if (file.contains(path) && !file.getStringList(path).isEmpty()) {
            return StringUtils.toString(file.getStringList(path));
        }
        return file.getString(path, "");
    }
}

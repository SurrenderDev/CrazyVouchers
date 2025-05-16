package com.badbones69.crazyvouchers;

import com.badbones69.crazyvouchers.api.CrazyManager;
import com.badbones69.crazyvouchers.api.InventoryManager;
import com.badbones69.crazyvouchers.api.builders.types.VoucherMenu;
import com.badbones69.crazyvouchers.api.enums.FileSystem;
import com.badbones69.crazyvouchers.commands.features.CommandHandler;
import com.badbones69.crazyvouchers.config.ConfigManager;
import com.badbones69.crazyvouchers.config.types.ConfigKeys;
import com.badbones69.crazyvouchers.listeners.FireworkDamageListener;
import com.badbones69.crazyvouchers.listeners.VoucherCraftListener;
import com.badbones69.crazyvouchers.listeners.VoucherMiscListener;
import com.badbones69.crazyvouchers.support.MetricsWrapper;
import com.ryderbelserion.fusion.core.managers.files.FileType;
import com.ryderbelserion.fusion.paper.FusionPaper;
import com.ryderbelserion.fusion.paper.files.LegacyFileManager;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

import com.badbones69.crazyvouchers.utils.VoucherAntiDupeManager;
import com.badbones69.crazyvouchers.listeners.VoucherClickListener;
import com.badbones69.crazyvouchers.listeners.VoucherGiveListener;

public class CrazyVouchers extends JavaPlugin {

    public @NotNull static CrazyVouchers get() {
        return JavaPlugin.getPlugin(CrazyVouchers.class);
    }

    private final long startTime;

    public CrazyVouchers() {
        this.startTime = System.nanoTime();
    }

    private InventoryManager inventoryManager;
    private CrazyManager crazyManager;
    private FusionPaper api;
    private LegacyFileManager fileManager;

    // Anti-dupe manager & webhook config
    private VoucherAntiDupeManager antiDupeManager;

    @Override
    public void onEnable() {
        // Ensure our plugin.yml config is loaded (for webhook URL, messages, etc.)
        saveDefaultConfig();

        // Read Discord webhook URL from config.yml
        String webhookUrl = getConfig().getString("discord-webhook-url", "");

        // Initialize anti-dupe manager
        antiDupeManager = new VoucherAntiDupeManager(this);
        antiDupeManager.load();

        // Set up Fusion & legacy data files
        this.api = new FusionPaper(getComponentLogger(), getDataPath());
        this.api.enable(this);

        this.fileManager = this.api.getLegacyFileManager();

        ConfigManager.load(getDataFolder());

        final FileSystem system = ConfigManager.getConfig().getProperty(ConfigKeys.file_system);

        // default data files
        this.fileManager
            .addFile("users.yml", FileType.YAML)
            .addFile("data.yml", FileType.YAML);

        switch (system) {
            case MULTIPLE -> this.fileManager
                .addFolder("codes", FileType.YAML)
                .addFolder("vouchers", FileType.YAML);
            case SINGLE -> this.fileManager
                .addFile("codes.yml", FileType.YAML)
                .addFile("vouchers.yml", FileType.YAML);
        }

        new MetricsWrapper(4536).start();

        // Load vouchers & inventory
        this.crazyManager = new CrazyManager();
        this.crazyManager.load();

        this.inventoryManager = new InventoryManager();

        Methods.janitor();

        // Register commands & listeners
        PluginManager pluginManager = getServer().getPluginManager();

        new CommandHandler(); // registers commands internally

        // Core event listeners
        pluginManager.registerEvents(new FireworkDamageListener(), this);
        pluginManager.registerEvents(new VoucherClickListener(this, antiDupeManager), this);
        pluginManager.registerEvents(new VoucherCraftListener(), this);
        pluginManager.registerEvents(new VoucherMiscListener(), this);
        pluginManager.registerEvents(new VoucherGiveListener(this, antiDupeManager), this);
        pluginManager.registerEvents(new VoucherMenu(), this);

        if (getFusion().isVerbose()) {
            double elapsed = (System.nanoTime() - this.startTime) / 1.0E9D;
            getComponentLogger().info("Done ({})!", String.format(Locale.ROOT, "%.3fs", elapsed));
        }
    }

    @Override
    public void onDisable() {
        // Save anti-dupe data
        if (antiDupeManager != null) {
            antiDupeManager.save();
        }
    }

    public final InventoryManager getInventoryManager() {
        return this.inventoryManager;
    }

    public final CrazyManager getCrazyManager() {
        return this.crazyManager;
    }

    public final LegacyFileManager getFileManager() {
        return this.fileManager;
    }

    public final FusionPaper getFusion() {
        return this.api;
    }

    public VoucherAntiDupeManager getAntiDupeManager() {
        return this.antiDupeManager;
    }
}

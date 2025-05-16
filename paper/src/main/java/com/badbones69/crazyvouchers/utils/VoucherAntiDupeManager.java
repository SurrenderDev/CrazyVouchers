package com.badbones69.crazyvouchers.utils;

import com.badbones69.crazyvouchers.CrazyVouchers;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class VoucherAntiDupeManager {

    private final CrazyVouchers plugin;
    private final File file;
    private final Gson gson;
    private Map<String, VoucherRecord> voucherMap;

    public VoucherAntiDupeManager(CrazyVouchers plugin) {
        this.plugin     = plugin;
        this.gson       = new GsonBuilder().setPrettyPrinting().create();
        this.voucherMap = new HashMap<>();
        // ensure data folder exists
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "vouchers_antidupe.json");
    }

    public void load() {
        if (!file.exists()) {
            save(); // create empty JSON if missing
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, VoucherRecord>>() {}.getType();
            voucherMap = gson.fromJson(reader, type);
            if (voucherMap == null) voucherMap = new HashMap<>();
        } catch (IOException e) {
            plugin.getLogger().severe("Failed loading vouchers_antidupe.json: " + e.getMessage());
            voucherMap = new HashMap<>();
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(voucherMap, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed saving vouchers_antidupe.json: " + e.getMessage());
        }
    }

    /** Called when a voucher is first created/given. */
    public void addVoucher(String id, String originalPlayer, String date, String time) {
        voucherMap.put(id, new VoucherRecord(originalPlayer, date, time, false));
        save();
    }

    /** Has this voucher already been redeemed? */
    public boolean isRedeemed(String id) {
        VoucherRecord rec = voucherMap.get(id);
        return rec != null && rec.redeemed;
    }

    /** Mark a voucher as redeemed. */
    public void markRedeemed(String id) {
        VoucherRecord rec = voucherMap.get(id);
        if (rec != null) {
            rec.redeemed = true;
            save();
        }
    }

    /** Retrieve who originally received this voucher. */
    public String getOriginalPlayer(String id) {
        VoucherRecord rec = voucherMap.get(id);
        return (rec != null) ? rec.originalPlayer : "None";
    }

    /** JSON record type. */
    public static class VoucherRecord {
        public String originalPlayer;
        public String creationDate;
        public String creationTime;
        public boolean redeemed;
        public VoucherRecord(String originalPlayer, String creationDate, String creationTime, boolean redeemed) {
            this.originalPlayer = originalPlayer;
            this.creationDate   = creationDate;
            this.creationTime   = creationTime;
            this.redeemed       = redeemed;
        }
    }
}

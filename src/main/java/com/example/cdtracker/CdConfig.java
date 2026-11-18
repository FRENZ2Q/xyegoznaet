package com.example.cdtracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CdConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("cdtracker.json");
    private static CdConfig instance = new CdConfig();

    public boolean enabled = true;
    public int hudX = 6;
    public int hudY = 40;
    public List<Rule> rules = defaults();

    /**
     * Правило кулдауна.
     * item    - id предмета (например minecraft:nether_star)
     * name    - часть названия кастомного предмета (пусто = любой предмет этого типа)
     * seconds - длительность кулдауна
     * onSwing - ловить использование по взмаху руки (для предметов, которые нажимаются правой кнопкой без анимации)
     */
    public static class Rule {
        public String item = "minecraft:ender_pearl";
        public String name = "";
        public int seconds = 10;
        public boolean onSwing = false;

        public Rule() {}

        public Rule(String item, String name, int seconds, boolean onSwing) {
            this.item = item;
            this.name = name;
            this.seconds = seconds;
            this.onSwing = onSwing;
        }
    }

    private static List<Rule> defaults() {
        List<Rule> l = new ArrayList<>();
        l.add(new Rule("minecraft:ender_pearl", "", 15, false));
        l.add(new Rule("minecraft:enchanted_golden_apple", "", 30, false));
        l.add(new Rule("minecraft:chorus_fruit", "", 10, false));
        return l;
    }

    public static CdConfig get() {
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                CdConfig loaded = GSON.fromJson(Files.readString(FILE), CdConfig.class);
                if (loaded != null) {
                    if (loaded.rules == null) loaded.rules = defaults();
                    instance = loaded;
                }
            } else {
                save();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(instance));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------ работа с предметами

    /** Название кастомного предмета без цветов; пустая строка, если предмет обычный. */
    public static String customName(ItemStack stack) {
        Text t = stack.get(DataComponentTypes.CUSTOM_NAME);
        if (t == null) {
            Text n = stack.get(DataComponentTypes.ITEM_NAME);
            if (n != null && !n.getString().equals(stack.getItem().getName().getString())) t = n;
        }
        if (t == null) return "";
        String s = Formatting.strip(t.getString());
        return s == null ? "" : s.trim();
    }

    public static String itemId(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    /** Находит правило для предмета: сначала с подходящим названием, потом общее для типа. */
    public Rule find(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        String id = itemId(stack);
        String name = customName(stack).toLowerCase(Locale.ROOT);
        Rule generic = null;
        for (Rule r : rules) {
            if (!id.equals(r.item)) continue;
            String rn = r.name == null ? "" : r.name.trim().toLowerCase(Locale.ROOT);
            if (rn.isEmpty()) {
                if (generic == null) generic = r;
            } else if (name.contains(rn)) {
                return r;
            }
        }
        return generic;
    }

    public boolean has(String item, String name) {
        String n = name == null ? "" : name.trim();
        for (Rule r : rules) {
            if (r.item.equals(item) && (r.name == null ? "" : r.name.trim()).equalsIgnoreCase(n)) return true;
        }
        return false;
    }
}

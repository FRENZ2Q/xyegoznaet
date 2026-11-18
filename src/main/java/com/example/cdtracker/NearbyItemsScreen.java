package com.example.cdtracker;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Показывает предметы, которые игроки рядом (и ты сам) держат в руках, вместе с их названиями.
 * Клик по предмету добавляет правило - так легко добавить кастомные предметы сервера:
 * название берётся прямо из предмета.
 */
public class NearbyItemsScreen extends Screen {
    private static final int COLS = 10;
    private static final int CELL = 26;
    private static final int MAX = 60;

    private record Held(ItemStack stack, String owner) {}

    private final Screen parent;
    private final List<Held> held = new ArrayList<>();

    public NearbyItemsScreen(Screen parent) {
        super(Text.literal("Предметы в руках игроков рядом"));
        this.parent = parent;
        rescan();
    }

    private void rescan() {
        held.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        Set<String> seen = new HashSet<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p.squaredDistanceTo(mc.player) > 64.0 * 64.0) continue;
            for (Hand hand : Hand.values()) {
                ItemStack s = p.getStackInHand(hand);
                if (s.isEmpty()) continue;
                String key = CdConfig.itemId(s) + "|" + CdConfig.customName(s);
                if (seen.add(key) && held.size() < MAX) {
                    held.add(new Held(s.copy(), p == mc.player ? "ты" : p.getName().getString()));
                }
            }
        }
    }

    private int gridLeft() { return width / 2 - COLS * CELL / 2; }
    private int gridTop() { return 50; }

    @Override
    protected void init() {
        int by = gridTop() + 6 * CELL + 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Обновить"), b -> rescan())
            .dimensions(width / 2 - 125, by, 120, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), b -> close())
            .dimensions(width / 2 + 5, by, 120, 20).build());
    }

    private Held heldAt(double mx, double my) {
        int gx = (int) ((mx - gridLeft()) / CELL);
        int gy = (int) ((my - gridTop()) / CELL);
        if (mx < gridLeft() || my < gridTop() || gx < 0 || gx >= COLS || gy < 0) return null;
        int idx = gy * COLS + gx;
        return idx < held.size() ? held.get(idx) : null;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Нажми на предмет, чтобы добавить его в список кулдаунов"), width / 2, 30, 0xAAAAAA);

        Held hovered = heldAt(mouseX, mouseY);
        for (int i = 0; i < held.size(); i++) {
            int x = gridLeft() + (i % COLS) * CELL;
            int y = gridTop() + (i / COLS) * CELL;
            if (held.get(i) == hovered) ctx.fill(x, y, x + CELL, y + CELL, 0x66FFFFFF);
            ctx.drawItem(held.get(i).stack(), x + 5, y + 5);
        }

        if (held.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Рядом никто ничего не держит. Попроси игрока взять предмет в руку и нажми «Обновить»"),
                width / 2, gridTop() + 20, 0xFFFFFF);
        }

        if (hovered != null) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Предмет у: " + hovered.owner()),
                width / 2, gridTop() + 6 * CELL + 8, 0xFFFF55);
            ctx.drawItemTooltip(textRenderer, hovered.stack(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        Held h = heldAt(mouseX, mouseY);
        if (h != null && button == 0) {
            CdConfig c = CdConfig.get();
            String id = CdConfig.itemId(h.stack());
            String name = CdConfig.customName(h.stack());
            if (!c.has(id, name)) {
                c.rules.add(new CdConfig.Rule(id, name, 10, !name.isEmpty()));
                CdConfig.save();
            }
            client.setScreen(parent);
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}

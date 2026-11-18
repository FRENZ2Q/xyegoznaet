package com.example.cdtracker;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Выбор любого предмета Майнкрафта. Слева сверху - поиск, справа - "название кастомного предмета"
 * (необязательно: если заполнить, правило сработает только для предмета с таким названием).
 */
public class ItemPickerScreen extends Screen {
    private static final int COLS = 12;
    private static final int ROWS = 6;
    private static final int CELL = 20;

    private final Screen parent;
    private final List<Item> all = new ArrayList<>();
    private List<Item> filtered = new ArrayList<>();
    private String query = "";
    private String customName = "";
    private int page = 0;

    public ItemPickerScreen(Screen parent) {
        super(Text.literal("Выбери предмет"));
        this.parent = parent;
        Registries.ITEM.stream().filter(i -> i != Items.AIR).forEach(all::add);
        refilter();
    }

    private void refilter() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Item> res = new ArrayList<>();
        for (Item item : all) {
            if (q.isEmpty()
                || item.getName().getString().toLowerCase(Locale.ROOT).contains(q)
                || Registries.ITEM.getId(item).getPath().contains(q)) {
                res.add(item);
            }
        }
        filtered = res;
        if (page >= pages()) page = pages() - 1;
    }

    private int pages() {
        return Math.max(1, (filtered.size() + COLS * ROWS - 1) / (COLS * ROWS));
    }

    private int gridLeft() { return width / 2 - COLS * CELL / 2; }
    private int gridTop() { return 58; }

    @Override
    protected void init() {
        TextFieldWidget search = new TextFieldWidget(textRenderer, width / 2 - 120, 30, 118, 18, Text.literal("Поиск"));
        search.setPlaceholder(Text.literal("Поиск предмета..."));
        search.setText(query);
        search.setChangedListener(s -> {
            query = s;
            page = 0;
            refilter();
        });
        addDrawableChild(search);
        setInitialFocus(search);

        TextFieldWidget nameField = new TextFieldWidget(textRenderer, width / 2 + 2, 30, 118, 18, Text.literal("Название"));
        nameField.setPlaceholder(Text.literal("Название кастомки"));
        nameField.setMaxLength(64);
        nameField.setText(customName);
        nameField.setChangedListener(s -> customName = s);
        addDrawableChild(nameField);

        int by = gridTop() + ROWS * CELL + 8;
        addDrawableChild(ButtonWidget.builder(Text.literal("◀"), b -> { if (page > 0) page--; })
            .dimensions(width / 2 - 100, by, 30, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("▶"), b -> { if (page < pages() - 1) page++; })
            .dimensions(width / 2 + 70, by, 30, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), b -> close())
            .dimensions(width / 2 - 60, by + 24, 120, 20).build());
    }

    private Item itemAt(double mx, double my) {
        int gx = (int) ((mx - gridLeft()) / CELL);
        int gy = (int) ((my - gridTop()) / CELL);
        if (mx < gridLeft() || my < gridTop() || gx < 0 || gx >= COLS || gy < 0 || gy >= ROWS) return null;
        int idx = page * COLS * ROWS + gy * COLS + gx;
        return idx < filtered.size() ? filtered.get(idx) : null;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFF);

        int gl = gridLeft();
        int gt = gridTop();
        int start = page * COLS * ROWS;
        Item hovered = itemAt(mouseX, mouseY);

        for (int i = 0; i < COLS * ROWS; i++) {
            int idx = start + i;
            if (idx >= filtered.size()) break;
            int x = gl + (i % COLS) * CELL;
            int y = gt + (i / COLS) * CELL;
            Item item = filtered.get(idx);
            if (item == hovered) ctx.fill(x, y, x + CELL, y + CELL, 0x66FFFFFF);
            ctx.drawItem(new ItemStack(item), x + 2, y + 2);
        }

        String info = (page + 1) + " / " + pages() + "   (найдено: " + filtered.size() + ")";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(info), width / 2, gt + ROWS * CELL + 14, 0xAAAAAA);

        if (hovered != null) {
            ctx.drawItemTooltip(textRenderer, new ItemStack(hovered), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        Item item = itemAt(mouseX, mouseY);
        if (item != null && button == 0) {
            CdConfig c = CdConfig.get();
            String id = Registries.ITEM.getId(item).toString();
            String name = customName.trim();
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

package com.example.cdtracker;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class CdScreen extends Screen {
    private static final int ROWS = 7;
    private static final int ROW_H = 22;
    private static final int TOP = 50;

    private final Screen parent;
    private int page = 0;
    private final List<CdConfig.Rule> visible = new ArrayList<>();

    public CdScreen(Screen parent) {
        super(Text.literal("Кулдауны предметов у противников"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        CdConfig c = CdConfig.get();
        visible.clear();

        int pages = Math.max(1, (c.rules.size() + ROWS - 1) / ROWS);
        if (page >= pages) page = pages - 1;

        int left = width / 2 - 170;

        for (int i = 0; i < ROWS; i++) {
            int idx = page * ROWS + i;
            if (idx >= c.rules.size()) break;
            CdConfig.Rule r = c.rules.get(idx);
            visible.add(r);
            int y = TOP + i * ROW_H;

            small(left + 114, y, 24, "-5", () -> adjust(r, -5));
            small(left + 140, y, 24, "-1", () -> adjust(r, -1));
            small(left + 198, y, 24, "+1", () -> adjust(r, 1));
            small(left + 224, y, 24, "+5", () -> adjust(r, 5));

            addDrawableChild(ButtonWidget.builder(swingLabel(r.onSwing), btn -> {
                r.onSwing = !r.onSwing;
                btn.setMessage(swingLabel(r.onSwing));
                CdConfig.save();
            }).dimensions(left + 252, y, 62, 20).build());

            small(left + 316, y, 24, "✕", () -> {
                c.rules.remove(r);
                CdConfig.save();
                clearAndInit();
            });
        }

        int by = TOP + ROWS * ROW_H + 6;
        small(left, by, 30, "◀", () -> { if (page > 0) { page--; clearAndInit(); } });
        small(left + 310, by, 30, "▶", () -> { if (page < pages - 1) { page++; clearAndInit(); } });
        small(left + 34, by, 130, "Из списка предметов", () -> client.setScreen(new ItemPickerScreen(this)));
        small(left + 168, by, 138, "С игроков рядом", () -> client.setScreen(new NearbyItemsScreen(this)));

        addDrawableChild(ButtonWidget.builder(trackerLabel(c.enabled), btn -> {
            c.enabled = !c.enabled;
            btn.setMessage(trackerLabel(c.enabled));
            CdConfig.save();
        }).dimensions(left, by + 24, 165, 20).build());

        small(left + 175, by + 24, 165, "Готово", this::close);
    }

    private static Text trackerLabel(boolean on) {
        return Text.literal("Трекер: ")
            .append(Text.literal(on ? "ВКЛ" : "ВЫКЛ").formatted(on ? Formatting.GREEN : Formatting.RED));
    }

    private static Text swingLabel(boolean on) {
        return Text.literal("Взмах: ")
            .append(Text.literal(on ? "да" : "нет").formatted(on ? Formatting.GREEN : Formatting.GRAY));
    }

    private void small(int x, int y, int w, String label, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), btn -> action.run())
            .dimensions(x, y, w, 20).build());
    }

    private void adjust(CdConfig.Rule r, int delta) {
        r.seconds = Math.max(1, Math.min(3600, r.seconds + delta));
        CdConfig.save();
        clearAndInit();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int left = width / 2 - 170;

        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Время кулдауна в секундах после использования предмета противником"),
            width / 2, 26, 0xAAAAAA);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("«Взмах» — для кастомных предметов, которые нажимаются без анимации"),
            width / 2, 37, 0xAAAAAA);

        if (visible.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Список пуст — добавь предмет кнопками ниже"),
                width / 2, TOP + 20, 0xFFFFFF);
        }

        for (int i = 0; i < visible.size(); i++) {
            CdConfig.Rule r = visible.get(i);
            Identifier ident = Identifier.tryParse(r.item);
            if (ident == null) continue;
            Item item = Registries.ITEM.get(ident);
            int y = TOP + i * ROW_H;

            boolean custom = r.name != null && !r.name.isBlank();
            String label = custom ? r.name : item.getName().getString();

            ctx.drawItem(new ItemStack(item), left, y + 2);
            ctx.drawText(textRenderer, textRenderer.trimToWidth(label, 88), left + 22, y + 6,
                custom ? 0xFFAA00 : 0xFFFFFF, true);

            String sec = r.seconds + " с";
            ctx.drawText(textRenderer, sec, left + 166 + (30 - textRenderer.getWidth(sec)) / 2, y + 6, 0xFFFF55, true);
        }
    }

    @Override
    public void close() {
        CdConfig.save();
        client.setScreen(parent);
    }
}

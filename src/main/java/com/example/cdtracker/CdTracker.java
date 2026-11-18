package com.example.cdtracker;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Следит за другими игроками и запускает кулдаун, когда видит использование предмета:
 *  - предмет съеден/выпит
 *  - предмет брошен (жемчуг, снежок, зелья...)
 *  - взмах руки с предметом (только для правил с включённым "Взмах" - кастомные предметы)
 */
public final class CdTracker {
    private static final class Entry {
        final UUID player;
        final String playerName;
        final String label;
        final ItemStack icon;
        final CdConfig.Rule rule;
        final int startTick;
        final int endTick;

        Entry(UUID player, String playerName, String label, ItemStack icon, CdConfig.Rule rule, int startTick, int endTick) {
            this.player = player;
            this.playerName = playerName;
            this.label = label;
            this.icon = icon;
            this.rule = rule;
            this.startTick = startTick;
            this.endTick = endTick;
        }
    }

    private static final List<Entry> ACTIVE = new ArrayList<>();
    private static final Map<UUID, Integer> LAST_USE_LEFT = new HashMap<>();
    private static final Map<UUID, ItemStack> LAST_USE_STACK = new HashMap<>();
    private static final Set<UUID> SWINGING = new HashSet<>();
    private static Set<Integer> seenProjectiles = new HashSet<>();
    private static int tick = 0;

    private CdTracker() {}

    public static void tick(MinecraftClient mc) {
        tick++;
        CdConfig c = CdConfig.get();

        if (mc.world == null || mc.player == null) {
            ACTIVE.clear();
            LAST_USE_LEFT.clear();
            LAST_USE_STACK.clear();
            SWINGING.clear();
            seenProjectiles.clear();
            return;
        }
        if (!c.enabled) return;

        ACTIVE.removeIf(e -> e.endTick <= tick);

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            trackUse(p, c);
            trackSwing(p, c);
        }
        trackProjectiles(mc, c);
    }

    private static void trackUse(PlayerEntity p, CdConfig c) {
        UUID id = p.getUuid();
        if (p.isUsingItem()) {
            LAST_USE_STACK.put(id, p.getActiveItem().copy());
            LAST_USE_LEFT.put(id, p.getItemUseTimeLeft());
        } else {
            ItemStack stack = LAST_USE_STACK.remove(id);
            Integer left = LAST_USE_LEFT.remove(id);
            // использование закончилось почти в самом конце -> съедено/выпито, а не отменено
            if (stack != null && left != null && left <= 3) {
                CdConfig.Rule r = c.find(stack);
                if (r != null) start(p, stack, r, false);
            }
        }
    }

    private static void trackSwing(PlayerEntity p, CdConfig c) {
        UUID id = p.getUuid();
        boolean swinging = p.handSwinging;
        boolean was = SWINGING.contains(id);
        if (swinging && !was) {
            for (Hand hand : Hand.values()) {
                ItemStack stack = p.getStackInHand(hand);
                CdConfig.Rule r = c.find(stack);
                if (r != null && r.onSwing) {
                    start(p, stack, r, true);
                    break;
                }
            }
        }
        if (swinging) SWINGING.add(id); else SWINGING.remove(id);
    }

    private static void trackProjectiles(MinecraftClient mc, CdConfig c) {
        Set<Integer> current = new HashSet<>();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ThrownItemEntity thrown)) continue;
            current.add(e.getId());
            if (seenProjectiles.contains(e.getId())) continue;
            if (thrown.age > 2) continue; // только что брошенные
            Entity owner = thrown.getOwner();
            if (owner instanceof PlayerEntity p && p != mc.player) {
                ItemStack stack = thrown.getStack();
                CdConfig.Rule r = c.find(stack);
                if (r != null) start(p, stack, r, false);
            }
        }
        seenProjectiles = current;
    }

    private static void start(PlayerEntity p, ItemStack stack, CdConfig.Rule rule, boolean fromSwing) {
        if (rule.seconds <= 0) return;
        UUID uuid = p.getUuid();

        // взмахи во время уже идущего кулдауна игнорируем (спам кликов не перезапускает полоску)
        if (fromSwing) {
            for (Entry e : ACTIVE) {
                if (e.player.equals(uuid) && e.rule == rule) return;
            }
        }
        ACTIVE.removeIf(e -> e.player.equals(uuid) && e.rule == rule);

        String custom = CdConfig.customName(stack);
        String label = !custom.isEmpty() ? custom : stack.getItem().getName().getString();
        ACTIVE.add(new Entry(uuid, p.getName().getString(), label, stack.copy(), rule, tick, tick + rule.seconds * 20));
    }

    // ------------------------------------------------------------ HUD

    public static void render(DrawContext ctx, RenderTickCounter counter) {
        CdConfig c = CdConfig.get();
        if (!c.enabled || ACTIVE.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.textRenderer == null) return;

        TextRenderer tr = mc.textRenderer;
        float pt = counter.getTickDelta(false);
        int x = c.hudX;
        int y = c.hudY;
        int w = 170;
        int shown = 0;

        for (Entry e : new ArrayList<>(ACTIVE)) {
            if (shown >= 8) break;
            float remaining = e.endTick - (tick + pt);
            if (remaining <= 0f) continue;
            float total = Math.max(1f, e.endTick - e.startTick);
            float ratio = Math.min(1f, remaining / total);

            ctx.fill(x, y, x + w, y + 24, 0x99000000);
            ctx.drawItem(e.icon, x + 3, y + 4);

            String t = String.format(Locale.ROOT, "%.1f с", remaining / 20f);
            int tw = tr.getWidth(t);
            String head = tr.trimToWidth(e.playerName + " · " + e.label, w - 24 - tw - 10);
            ctx.drawText(tr, head, x + 24, y + 3, 0xFFFFFFFF, true);
            ctx.drawText(tr, t, x + w - 4 - tw, y + 3, 0xFFFFFF55, true);

            int bx1 = x + 24;
            int bx2 = x + w - 4;
            int by = y + 15;
            ctx.fill(bx1, by, bx2, by + 5, 0xFF333333);
            ctx.fill(bx1, by, bx1 + (int) ((bx2 - bx1) * ratio), by + 5, 0xFF3DA5FF);

            y += 26;
            shown++;
        }
    }
}

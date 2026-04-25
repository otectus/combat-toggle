package com.runecraft.combattoggle.client;

import com.runecraft.combattoggle.CombatToggle;
import com.runecraft.combattoggle.config.CTConfig;
import com.runecraft.combattoggle.util.TextUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;

import static com.runecraft.combattoggle.CombatToggle.MODID;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CombatHudOverlay {
    private static final ResourceLocation PEACE_TEX = new ResourceLocation(CombatToggle.MODID, "textures/gui/peace.png");
    private static final ResourceLocation COMBAT_TEX = new ResourceLocation(CombatToggle.MODID, "textures/gui/combat.png");

    private static final int WIDTH = 51;
    private static final int HEIGHT = 19;

    // Cache the formatted "M:SS" strings between renders. The HUD redraws every frame, often >60 FPS,
    // but the displayed value only changes once per second; rebuilding the string each frame is wasted work.
    private static long lastTagSecond = Long.MIN_VALUE;
    private static String tagTextCached = "";
    private static long lastCdSecond = Long.MIN_VALUE;
    private static String cdTextCached = "";

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("combat_toggle", CombatHudOverlay::render);
    }

    private static void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        if (!CTConfig.showHud.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.options.hideGui) return;
        if (mc.screen != null) return;

        int x = switch (CTConfig.hudAnchor.get()) {
            case LEFT -> 2;
            case RIGHT -> w - WIDTH - 2;
            case CENTER -> (w / 2) - (WIDTH / 2);
        };
        int y = CTConfig.hudYOffset.get();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        ResourceLocation texture = ClientCombatState.isEnabled() ? COMBAT_TEX : PEACE_TEX;
        g.blit(texture, x, y, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);

        if (CTConfig.showHudTimers.get()) {
            renderTimers(mc, g, x, y);
        }

        RenderSystem.disableBlend();
    }

    private static void renderTimers(Minecraft mc, GuiGraphics g, int hudX, int hudY) {
        long now = Util.getMillis();
        long tagUntil = ClientCombatState.getCombatTagUntilMs();
        long cdUntil = ClientCombatState.getCooldownUntilMs();

        boolean tagActive = tagUntil > now;
        boolean cdActive = cdUntil > now;

        int textCenterX = hudX + (WIDTH / 2);

        if (tagActive) {
            long tagSec = (tagUntil - now + 999L) / 1000L;
            if (tagSec != lastTagSecond) {
                lastTagSecond = tagSec;
                tagTextCached = "Tag: " + TextUtil.formatRemaining(tagSec * 1000L);
            }
            int tx = textCenterX - (mc.font.width(tagTextCached) / 2);
            g.drawString(mc.font, tagTextCached, tx, hudY + HEIGHT + 2, 0xFFFF5555, true);
        }

        if (cdActive) {
            long cdSec = (cdUntil - now + 999L) / 1000L;
            if (cdSec != lastCdSecond) {
                lastCdSecond = cdSec;
                cdTextCached = "CD: " + TextUtil.formatRemaining(cdSec * 1000L);
            }
            int cx = textCenterX - (mc.font.width(cdTextCached) / 2);
            int cy = tagActive ? hudY + HEIGHT + 14 : hudY + HEIGHT + 2;
            g.drawString(mc.font, cdTextCached, cx, cy, 0xFFFFAA00, true);
        }
    }
}

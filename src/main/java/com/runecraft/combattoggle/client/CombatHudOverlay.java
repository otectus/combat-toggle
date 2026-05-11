package com.runecraft.combattoggle.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.runecraft.combattoggle.CombatToggle;
import com.runecraft.combattoggle.common.CTConfig;
import com.runecraft.combattoggle.common.HudAnchor;
import com.runecraft.combattoggle.server.TextHelper;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.runecraft.combattoggle.CombatToggle.MODID;

/**
 * Anchor-aware HUD indicator. Registered as a Forge GUI overlay; only loads on the physical client.
 *
 * <p>1.19.2 uses {@link PoseStack} directly for HUD render (the 1.20+ {@code GuiGraphics} wrapper
 * does not exist here). Texture blitting goes through {@link GuiComponent#blit} and text rendering
 * through {@code Minecraft.getInstance().font.drawShadow(PoseStack, ...)}.
 */
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CombatHudOverlay {
    private static final ResourceLocation PEACE_TEX = new ResourceLocation(CombatToggle.MODID, "textures/gui/peace.png");
    private static final ResourceLocation COMBAT_TEX = new ResourceLocation(CombatToggle.MODID, "textures/gui/combat.png");

    private static final int WIDTH = 51;
    private static final int HEIGHT = 19;

    // Per-second string caching so the HUD doesn't rebuild "M:SS" every frame.
    private static long lastTagSecond = Long.MIN_VALUE;
    private static String tagTextCached = "";
    private static long lastCdSecond = Long.MIN_VALUE;
    private static String cdTextCached = "";

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("combat_toggle", CombatHudOverlay::render);
    }

    private static void render(ForgeGui gui, PoseStack ps, float partialTick, int screenW, int screenH) {
        if (!CTConfig.hudEnabled.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.options.hideGui) return;
        if (mc.screen != null) return;

        boolean inCombat = ClientCombatState.isEnabled();
        if (inCombat && !CTConfig.hudShowInCombatMode.get()) return;
        if (!inCombat && !CTConfig.hudShowInPeaceMode.get()) return;

        float scale = (float) Math.max(0.5, Math.min(3.0, CTConfig.hudScale.get()));
        int scaledW = (int) (WIDTH * scale);
        int scaledH = (int) (HEIGHT * scale);
        int[] xy = HudAnchor.computeXY(CTConfig.hudAnchor.get(),
                CTConfig.hudOffsetX.get(),
                CTConfig.hudOffsetY.get(),
                screenW, screenH, scaledW, scaledH);
        int x = xy[0];
        int y = xy[1];

        ps.pushPose();
        ps.translate(x, y, 0);
        ps.scale(scale, scale, 1.0f);

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, inCombat ? COMBAT_TEX : PEACE_TEX);
        GuiComponent.blit(ps, 0, 0, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);

        ps.popPose();
        RenderSystem.disableBlend();

        // Timers render after the popped scale so the text uses the screen's natural font scale,
        // positioned just below the (already-scaled) icon.
        if (CTConfig.hudShowTimers.get()) {
            renderTimers(mc, ps, x, y, scaledW, scaledH);
        }
    }

    private static void renderTimers(Minecraft mc, PoseStack ps, int hudX, int hudY, int hudW, int hudH) {
        long now = Util.getMillis();
        long tagUntil = ClientCombatState.getCombatTagUntilMs();
        long cdUntil = ClientCombatState.getCooldownUntilMs();

        boolean tagActive = tagUntil > now;
        boolean cdActive = cdUntil > now;
        if (!tagActive && !cdActive) return;

        int textCenterX = hudX + (hudW / 2);

        if (tagActive) {
            long tagSec = (tagUntil - now + 999L) / 1000L;
            if (tagSec != lastTagSecond) {
                lastTagSecond = tagSec;
                tagTextCached = "Tag: " + TextHelper.formatRemaining(tagSec * 1000L);
            }
            int tx = textCenterX - (mc.font.width(tagTextCached) / 2);
            mc.font.drawShadow(ps, tagTextCached, tx, hudY + hudH + 2, 0xFFFF5555);
        }

        if (cdActive) {
            long cdSec = (cdUntil - now + 999L) / 1000L;
            if (cdSec != lastCdSecond) {
                lastCdSecond = cdSec;
                cdTextCached = "CD: " + TextHelper.formatRemaining(cdSec * 1000L);
            }
            int cx = textCenterX - (mc.font.width(cdTextCached) / 2);
            int cy = tagActive ? hudY + hudH + 14 : hudY + hudH + 2;
            mc.font.drawShadow(ps, cdTextCached, cx, cy, 0xFFFFAA00);
        }
    }
}

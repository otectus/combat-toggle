package com.runecraft.combattoggle.client;

import com.runecraft.combattoggle.CombatToggle;
import com.runecraft.combattoggle.config.CTConfig;
import com.runecraft.combattoggle.util.TextUtil;
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
    private static final int Y = 6;

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("combat_toggle", CombatHudOverlay::render);
    }

    private static void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        if (!CTConfig.showHud.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.options.hideGui) return;

        int x = (w / 2) - (WIDTH / 2);

        ResourceLocation texture = ClientCombatState.isEnabled() ? COMBAT_TEX : PEACE_TEX;
        g.blit(texture, x, Y, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);

        // Render combat tag timer
        long now = System.currentTimeMillis();
        long tagUntil = ClientCombatState.getCombatTagUntilMs();
        if (tagUntil > now) {
            String tagText = "Tag: " + TextUtil.formatRemaining(tagUntil - now);
            int textX = (w / 2) - (mc.font.width(tagText) / 2);
            g.drawString(mc.font, tagText, textX, Y + HEIGHT + 2, 0xFFFF5555, true);
        }

        // Render cooldown timer
        long cdUntil = ClientCombatState.getCooldownUntilMs();
        if (cdUntil > now) {
            String cdText = "CD: " + TextUtil.formatRemaining(cdUntil - now);
            int textX = (w / 2) - (mc.font.width(cdText) / 2);
            int yOff = (tagUntil > now) ? Y + HEIGHT + 14 : Y + HEIGHT + 2;
            g.drawString(mc.font, cdText, textX, yOff, 0xFFFFAA00, true);
        }
    }
}

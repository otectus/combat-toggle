package com.runecraft.combattoggle.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.runecraft.combattoggle.CombatToggle;
import com.runecraft.combattoggle.config.CTConfig;
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

    private static final int TEXTURE_WIDTH = 450;
    private static final int TEXTURE_HEIGHT = 101;
    private static final int DISPLAY_WIDTH = 120; // Display width on screen (optimized size)
    private static final int DISPLAY_HEIGHT = 27; // Display height on screen (maintains aspect ratio)
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

        int x = (w / 2) - (DISPLAY_WIDTH / 2);

        boolean combat = ClientCombatState.isEnabled();

        // Select the appropriate texture based on combat state
        ResourceLocation texture = combat ? COMBAT_TEX : PEACE_TEX;

        // Use pose stack to scale the rendering
        var pose = g.pose();
        pose.pushPose();
        
        // Calculate scale factor to fit texture into desired display size
        float scaleX = (float) DISPLAY_WIDTH / TEXTURE_WIDTH;
        float scaleY = (float) DISPLAY_HEIGHT / TEXTURE_HEIGHT;
        
        // Translate to position, then scale
        pose.translate(x, Y, 0);
        pose.scale(scaleX, scaleY, 1.0f);
        
        // Render the full texture at original size (will be scaled by the matrix)
        g.blit(texture, 0, 0, 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        
        pose.popPose();
    }
}

package com.runecraft.combattoggle.client;

import com.runecraft.combattoggle.common.network.C2SRequestTogglePacket;
import com.runecraft.combattoggle.common.network.PacketHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import static com.runecraft.combattoggle.CombatToggle.MODID;

/** Registers the V keybind for toggle requests; mod bus only loads on physical client. */
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientKeybinds {
    public static KeyMapping TOGGLE;

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        TOGGLE = new KeyMapping("key.combattoggle.toggle", GLFW.GLFW_KEY_V, "key.categories.gameplay");
        event.register(TOGGLE);
    }
}

/** Forge-bus tick subscriber that drains keybind clicks into C2S toggle packets. */
@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
final class ClientKeybindTick {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (ClientKeybinds.TOGGLE == null) return;
        // Don't fire while typing in chat / inventory / advancement screen.
        if (Minecraft.getInstance().screen != null) return;

        while (ClientKeybinds.TOGGLE.consumeClick()) {
            PacketHandler.sendToServer(new C2SRequestTogglePacket());
        }
    }
}

package com.runecraft.combattoggle;

import com.mojang.logging.LogUtils;
import com.runecraft.combattoggle.config.CTConfig;
import com.runecraft.combattoggle.network.PacketHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CombatToggle.MODID)
public class CombatToggle {
    public static final String MODID = "combattoggle";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CombatToggle() {
        CTConfig.register();
        PacketHandler.register();

        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // Client-only init is done via @Mod.EventBusSubscriber on the client package
    }
}

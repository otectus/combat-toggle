package com.runecraft.combattoggle;

import com.mojang.logging.LogUtils;
import com.runecraft.combattoggle.common.CTConfig;
import com.runecraft.combattoggle.common.network.PacketHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CombatToggle.MODID)
public class CombatToggle {
    public static final String MODID = "combattoggle";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CombatToggle() {
        CTConfig.register(FMLJavaModLoadingContext.get().getModEventBus());
        PacketHandler.register();
        // No client-class imports here. Client subscribers self-register via
        // @Mod.EventBusSubscriber(value = Dist.CLIENT) so the dedicated server never classloads them.
    }
}

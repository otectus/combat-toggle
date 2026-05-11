package com.runecraft.combattoggle.common.data;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.runecraft.combattoggle.CombatToggle.MODID;

/**
 * Forge capability that hosts {@link CombatToggleData} on every {@link Player} entity. The capability
 * is attached at entity-construction time and survives logout/login automatically (vanilla persists
 * capability NBT in {@code playerdata/<uuid>.dat}). On respawn, {@link PlayerEvent.Clone} fires; we
 * copy the old player's data into the new one so death does not wipe Combat Toggle state.
 */
public final class CombatToggleCapability {

    public static final Capability<CombatToggleData> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {});

    private static final ResourceLocation ID = new ResourceLocation(MODID, "state");

    private CombatToggleCapability() {}

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(CombatToggleData.class);
        }
    }

    @Mod.EventBusSubscriber(modid = MODID)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void onAttach(AttachCapabilitiesEvent<net.minecraft.world.entity.Entity> event) {
            if (!(event.getObject() instanceof Player)) return;
            event.addCapability(ID, new Provider());
        }

        @SubscribeEvent
        public static void onClone(PlayerEvent.Clone event) {
            Player oldPlayer = event.getOriginal();
            Player newPlayer = event.getEntity();
            // Caps on the original are invalidated immediately after death; revive briefly to read them.
            oldPlayer.reviveCaps();
            try {
                oldPlayer.getCapability(CAPABILITY).ifPresent(oldData ->
                        newPlayer.getCapability(CAPABILITY).ifPresent(newData ->
                                newData.copyFrom(oldData)));
            } finally {
                oldPlayer.invalidateCaps();
            }
        }
    }

    private static final class Provider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
        private final CombatToggleData instance = new CombatToggleData();
        private final LazyOptional<CombatToggleData> handle = LazyOptional.of(() -> instance);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return CAPABILITY.orEmpty(cap, handle);
        }

        @Override
        public CompoundTag serializeNBT() {
            return instance.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            instance.deserializeNBT(tag);
        }
    }
}

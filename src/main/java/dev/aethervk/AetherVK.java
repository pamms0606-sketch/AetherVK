package dev.aethervk;

import com.mojang.logging.LogUtils;
import dev.aethervk.vulkan.VulkanBootstrap;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(value = AetherVK.MOD_ID, dist = Dist.CLIENT)
public final class AetherVK {
    public static final String MOD_ID = "aethervk";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final long MIN_FRAME_NANOS = 16_000_000L;

    private boolean initialized = false;
    private long lastDrawNanos = 0L;

    public AetherVK(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onRenderFrame);
        LOGGER.info("AetherVK {} loaded. Waiting for the Minecraft window.", "0.1.0");
    }

    private void onClientTick(ClientTickEvent.Post event) {
        if (initialized) {
            return;
        }

        if (Minecraft.getInstance().getWindow().getWindow() == 0L) {
            return;
        }

        initialized = true;
        VulkanBootstrap.initialize(LOGGER);
    }

    private void onRenderFrame(RenderFrameEvent.Post event) {
        if (!initialized) {
            return;
        }

        long now = System.nanoTime();
        if (now - lastDrawNanos < MIN_FRAME_NANOS) {
            return;
        }
        lastDrawNanos = now;
        VulkanBootstrap.drawFrame(LOGGER);
    }
}

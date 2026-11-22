package dev.aethervk.standalone;

import dev.aethervk.vulkan.VulkanBootstrap;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Minimal standalone launcher: the same cube as the mod, without Minecraft. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        Logger logger = LoggerFactory.getLogger("AetherVK");
        GLFWErrorCallback errorCallback = GLFWErrorCallback.createPrint(System.err);
        GLFW.glfwSetErrorCallback(errorCallback);
        if (!GLFW.glfwInit()) {
            logger.error("[AetherVK] glfwInit failed.");
            errorCallback.free();
            System.exit(1);
        }
        try {
            VulkanBootstrap.initialize(logger);
            while (VulkanBootstrap.isRunning()) {
                GLFW.glfwPollEvents();
                VulkanBootstrap.drawFrame(logger);
            }
        } finally {
            VulkanBootstrap.shutdown(logger);
            GLFW.glfwTerminate();
            errorCallback.free();
        }
    }
}

package dev.aethervk.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;
import net.minecraft.client.Minecraft;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkComponentMapping;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRPortabilityEnumeration;
import org.lwjgl.vulkan.VK;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Milestone 0.1: load the Vulkan loader, create an instance, select a graphics-capable
 * physical device, and create a logical device. No swapchain or Minecraft surface yet.
 */
public final class VulkanBootstrap {
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static volatile VkInstance instance;
    private static volatile VkDevice logicalDevice;
    private static volatile VkPhysicalDevice physicalDevice;
    private static volatile VkQueue graphicsQueue;
    private static volatile int graphicsQueueFamily = -1;
    private static volatile long surface;
    private static volatile long swapchain;
    private static volatile long[] swapchainImages = new long[0];
    private static volatile long[] imageViews = new long[0];
    private static volatile long commandPool;
    private static volatile VkCommandBuffer[] commandBuffers = new VkCommandBuffer[0];
    private static volatile long imageAvailableSemaphore;
    private static volatile long renderFinishedSemaphore;
    private static volatile long renderFence;
    private static volatile int swapchainFormat;
    private static volatile int swapchainWidth;
    private static volatile int swapchainHeight;
    private static volatile long renderPass;
    private static volatile long[] framebuffers = new long[0];
    private static volatile long testWindow;

    private VulkanBootstrap() {}

    public static void initialize(Logger logger) {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }

        try {
            configureMacLoader(logger);
            try {
    VK.getFunctionProvider();
    logger.info("[AetherVK] LWJGL Vulkan binding was already initialized.");
} catch (IllegalStateException notInitialized) {
    VK.create();
}
            logger.info("[AetherVK] LWJGL Vulkan binding loaded.");

            try (MemoryStack stack = MemoryStack.stackPush()) {
                Set<String> instanceExtensions = enumerateInstanceExtensions(stack);
                boolean portabilityEnumeration = instanceExtensions.contains(
                        KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME);
                boolean getPhysProps2 = instanceExtensions.contains(
        org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME);
                VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                        .sType$Default()
                        .pApplicationName(stack.UTF8("AetherVK"))
                        .applicationVersion(VK_MAKE_VERSION(0, 1, 0))
                        .pEngineName(stack.UTF8("AetherVK Vulkan Backend"))
                        .engineVersion(VK_MAKE_VERSION(0, 1, 0))
                        .apiVersion(VK_API_VERSION_1_0);

                VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                        .sType$Default()
                        .pApplicationInfo(appInfo);

         if (!GLFWVulkan.glfwVulkanSupported()) {
    throw new IllegalStateException("GLFW reports that Vulkan is not supported.");
}

PointerBuffer glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();

if (glfwExtensions == null) {
    throw new IllegalStateException("GLFW returned no required Vulkan extensions.");
}

int extensionCount = glfwExtensions.remaining()
        + (portabilityEnumeration ? 1 : 0)
        + (getPhysProps2 ? 1 : 0);

PointerBuffer enabledExtensions = stack.mallocPointer(extensionCount);

for (int i = 0; i < glfwExtensions.remaining(); i++) {
    enabledExtensions.put(glfwExtensions.get(glfwExtensions.position() + i));
}

if (portabilityEnumeration) {
    enabledExtensions.put(stack.UTF8(
            KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME));

    createInfo.flags(
            KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR);

    logger.info("[AetherVK] Enabled VK_KHR_portability_enumeration.");
}
if (getPhysProps2) {
    enabledExtensions.put(stack.UTF8(
            org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME));
    logger.info("[AetherVK] Enabled VK_KHR_get_physical_device_properties2.");
}
enabledExtensions.flip();
createInfo.ppEnabledExtensionNames(enabledExtensions);

logger.info("[AetherVK] GLFW Vulkan surface extensions enabled.");

                PointerBuffer instanceOut = stack.mallocPointer(1);
                int createResult = vkCreateInstance(createInfo, null, instanceOut);
                checkVk(createResult, "vkCreateInstance");
                instance = new VkInstance(instanceOut.get(0), createInfo);
                GLFW.glfwDefaultWindowHints();
                GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
                GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_TRUE);

                testWindow = GLFW.glfwCreateWindow(
                        640, 480, "AetherVK Vulkan Test", 0L, 0L
                );

                if (testWindow == 0L) {
                    throw new IllegalStateException(
                            "Could not create the GLFW Vulkan test window."
                    );
                }

                logger.info("[AetherVK] Visible GLFW_NO_API test window created.");

                LongBuffer surfaceOut = stack.mallocLong(1);
                int surfaceResult = GLFWVulkan.glfwCreateWindowSurface(
                        instance, testWindow, null, surfaceOut
                );

                checkVk(surfaceResult, "glfwCreateWindowSurface");
                surface = surfaceOut.get(0);
                logger.info("[AetherVK] Vulkan test surface created.");
                logger.info("[AetherVK] Vulkan instance created.");

                createLogicalDevice(logger, stack);
                createSwapchain(logger, stack);
                createImageViews(logger, stack);
                createRenderPassAndFramebuffers(logger, stack);
                TrianglePipeline.create(logger, logicalDevice, physicalDevice, renderPass, swapchainWidth, swapchainHeight);
                createCommandPoolAndBuffers(logger, stack);
                renderAndPresentTestFrame(logger, stack);
            }
        } catch (Throwable error) {
            logger.error("[AetherVK] Vulkan bootstrap failed. Minecraft will continue using its normal renderer.", error);
            destroyQuietly(logger);
        }
    }

    private static void configureMacLoader(Logger logger) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("mac")) {
            return;
        }

        // Prefer the Vulkan SDK loader when VULKAN_SDK is configured. On Apple Silicon,
        // a current SDK can expose the KosmicKrisp Vulkan-on-Metal driver.
        String sdk = System.getenv("VULKAN_SDK");
        if (sdk != null && !sdk.isBlank()) {
            Path loader = Path.of(sdk, "lib", "libvulkan.1.dylib");
            if (Files.isRegularFile(loader)) {
                Configuration.VULKAN_LIBRARY_NAME.set(loader.toAbsolutePath().toString());
                logger.info("[AetherVK] Using Vulkan SDK loader at {}", loader);
                return;
            }
        }

        String override = System.getProperty("aethervk.vulkanLibrary");
        if (override != null && !override.isBlank()) {
            Configuration.VULKAN_LIBRARY_NAME.set(override);
            logger.info("[AetherVK] Using configured Vulkan loader: {}", override);
        } else {
            logger.warn("[AetherVK] VULKAN_SDK is not configured. LWJGL will use its default macOS Vulkan native provider.");
            logger.warn("[AetherVK] For Apple Silicon development, install the current Vulkan SDK and use its KosmicKrisp driver when available.");
        }
    }

    private static Set<String> enumerateInstanceExtensions(MemoryStack stack) {
        IntBuffer count = stack.ints(0);
        checkVk(vkEnumerateInstanceExtensionProperties((ByteBuffer) null, count, null),
                "vkEnumerateInstanceExtensionProperties(count)");
        VkExtensionProperties.Buffer properties = VkExtensionProperties.malloc(count.get(0), stack);
        checkVk(vkEnumerateInstanceExtensionProperties((ByteBuffer) null, count, properties),
                "vkEnumerateInstanceExtensionProperties(data)");

        Set<String> names = new HashSet<>();
        for (int i = 0; i < properties.capacity(); i++) {
            names.add(properties.get(i).extensionNameString());
        }
        return names;
    }

    private static void createLogicalDevice(Logger logger, MemoryStack stack) {
        IntBuffer physicalCount = stack.ints(0);
        checkVk(vkEnumeratePhysicalDevices(instance, physicalCount, null),
                "vkEnumeratePhysicalDevices(count)");
        if (physicalCount.get(0) == 0) {
            throw new IllegalStateException("Vulkan loader reported zero physical devices.");
        }

        PointerBuffer physicalHandles = stack.mallocPointer(physicalCount.get(0));
        checkVk(vkEnumeratePhysicalDevices(instance, physicalCount, physicalHandles),
                "vkEnumeratePhysicalDevices(data)");

        List<DeviceCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < physicalHandles.capacity(); i++) {
            VkPhysicalDevice physicalDevice = new VkPhysicalDevice(physicalHandles.get(i), instance);
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.malloc(stack);
            vkGetPhysicalDeviceProperties(physicalDevice, properties);

            int graphicsQueue = findPresentGraphicsQueue(physicalDevice, stack);
            String name = properties.deviceNameString();
            logger.info("[AetherVK] Vulkan device: {} (type={}, graphicsQueue={})",
                    name, deviceTypeName(properties.deviceType()), graphicsQueue);
            if (graphicsQueue >= 0) {
                candidates.add(new DeviceCandidate(physicalDevice, name, graphicsQueue));
            }
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException("No Vulkan physical device exposes a graphics queue.");
        }

        DeviceCandidate selected = candidates.get(0);
        Set<String> deviceExtensions = enumerateDeviceExtensions(selected.device(), stack);
        List<String> enabledNames = new ArrayList<>();
        if (!deviceExtensions.contains("VK_KHR_swapchain")) {
            throw new IllegalStateException("Selected Vulkan device does not support VK_KHR_swapchain.");
        }
        enabledNames.add("VK_KHR_swapchain");
        logger.info("[AetherVK] Enabled VK_KHR_swapchain.");

        // Vulkan portability implementations require this extension to be enabled when advertised.
        if (deviceExtensions.contains("VK_KHR_portability_subset")) {
            enabledNames.add("VK_KHR_portability_subset");
            logger.info("[AetherVK] Enabled VK_KHR_portability_subset.");
        }

        VkDeviceQueueCreateInfo.Buffer queueInfos = VkDeviceQueueCreateInfo.calloc(1, stack);
        queueInfos.get(0)
                .sType$Default()
                .queueFamilyIndex(selected.graphicsQueueFamily())
                .pQueuePriorities(stack.floats(1.0f));

        VkDeviceCreateInfo deviceInfo = VkDeviceCreateInfo.calloc(stack)
                .sType$Default()
                .pQueueCreateInfos(queueInfos);

        if (!enabledNames.isEmpty()) {
            PointerBuffer extensionPointers = stack.mallocPointer(enabledNames.size());
            for (String name : enabledNames) {
                extensionPointers.put(stack.UTF8(name));
            }
            extensionPointers.flip();
            deviceInfo.ppEnabledExtensionNames(extensionPointers);
        }

        PointerBuffer deviceOut = stack.mallocPointer(1);
        checkVk(vkCreateDevice(selected.device(), deviceInfo, null, deviceOut), "vkCreateDevice");
        logicalDevice = new VkDevice(deviceOut.get(0), selected.device(), deviceInfo);
        physicalDevice = selected.device();
        graphicsQueueFamily = selected.graphicsQueueFamily();
        PointerBuffer queueOut = stack.mallocPointer(1);
        vkGetDeviceQueue(logicalDevice, graphicsQueueFamily, 0, queueOut);
        graphicsQueue = new VkQueue(queueOut.get(0), logicalDevice);
        logger.info("[AetherVK] Graphics/present queue retrieved successfully.");

        logger.info("[AetherVK] Logical device created successfully on '{}'.", selected.name());
        logger.info("[AetherVK] Bootstrap milestone 0.1 complete. Swapchain, rendering bridge and shader packs are not implemented yet.");
    }


    private static void createSwapchain(Logger logger, MemoryStack stack) {
        VkSurfaceCapabilitiesKHR capabilities =
                VkSurfaceCapabilitiesKHR.malloc(stack);

        checkVk(
                KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
                        physicalDevice, surface, capabilities),
                "vkGetPhysicalDeviceSurfaceCapabilitiesKHR"
        );

        if ((capabilities.supportedUsageFlags() & VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) == 0) {
            throw new IllegalStateException(
                    "Surface does not support color-attachment swapchain images."
            );
        }
        logger.info("[AetherVK] DEBUG supportedUsageFlags=0x{}",
        Integer.toHexString(capabilities.supportedUsageFlags()));
        IntBuffer formatCount = stack.ints(0);
        checkVk(
                KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(
                        physicalDevice, surface, formatCount, null),
                "vkGetPhysicalDeviceSurfaceFormatsKHR(count)"
        );

        if (formatCount.get(0) == 0) {
            throw new IllegalStateException("Surface exposes no swapchain formats.");
        }

        VkSurfaceFormatKHR.Buffer formats =
                VkSurfaceFormatKHR.malloc(formatCount.get(0), stack);

        checkVk(
                KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(
                        physicalDevice, surface, formatCount, formats),
                "vkGetPhysicalDeviceSurfaceFormatsKHR(data)"
        );

        VkSurfaceFormatKHR selectedFormat = formats.get(0);
        for (int i = 0; i < formats.capacity(); i++) {
            VkSurfaceFormatKHR f = formats.get(i);
            logger.info("[AetherVK] Available surface format[{}]: format={}, colorSpace={}", i, f.format(), f.colorSpace());
        }
        for (int i = 0; i < formats.capacity(); i++) {
            VkSurfaceFormatKHR candidate = formats.get(i);
            if (candidate.format() == VK_FORMAT_B8G8R8A8_SRGB
                    && candidate.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                selectedFormat = candidate;
                break;
            }
        }

        int imageCount = capabilities.minImageCount() + 1;
        if (capabilities.maxImageCount() > 0
                && imageCount > capabilities.maxImageCount()) {
            imageCount = capabilities.maxImageCount();
        }

        VkExtent2D extent = VkExtent2D.malloc(stack);
        int currentWidth = capabilities.currentExtent().width();
        int currentHeight = capabilities.currentExtent().height();

        if (currentWidth != -1 && currentHeight != -1) {
            extent.set(currentWidth, currentHeight);
        } else {
            IntBuffer width = stack.ints(0);
            IntBuffer height = stack.ints(0);
            GLFW.glfwGetFramebufferSize(testWindow, width, height);

            int actualWidth = Math.max(
                    capabilities.minImageExtent().width(),
                    Math.min(capabilities.maxImageExtent().width(), width.get(0))
            );
            int actualHeight = Math.max(
                    capabilities.minImageExtent().height(),
                    Math.min(capabilities.maxImageExtent().height(), height.get(0))
            );
            extent.set(actualWidth, actualHeight);
        }

        if (extent.width() <= 0 || extent.height() <= 0) {
            throw new IllegalStateException("Swapchain extent is zero-sized.");
        }

        int supportedAlpha = capabilities.supportedCompositeAlpha();
        logger.info("[AetherVK] DEBUG supportedCompositeAlpha=0x{}", Integer.toHexString(supportedAlpha));
        int compositeAlpha = KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
        int[] alphaOptions = {
                KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR,
                KHRSurface.VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,
                KHRSurface.VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR,
                KHRSurface.VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR
        };

        boolean alphaFound = false;
        for (int option : alphaOptions) {
            if ((supportedAlpha & option) != 0) {
                compositeAlpha = option;
                alphaFound = true;
                break;
            }
        }

        logger.info("[AetherVK] DEBUG selected compositeAlpha=0x{}", Integer.toHexString(compositeAlpha));

        if (!alphaFound) {
            throw new IllegalStateException("Surface exposes no supported composite alpha mode.");
        }

        VkSwapchainCreateInfoKHR createInfo =
                VkSwapchainCreateInfoKHR.calloc(stack)
                        .sType$Default()
                        .surface(surface)
                        .minImageCount(imageCount)
                        .imageFormat(selectedFormat.format())
                        .imageColorSpace(selectedFormat.colorSpace())
                        .imageExtent(extent)
                        .imageArrayLayers(1)
                        .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) 
                        .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                        .preTransform(capabilities.currentTransform())
                        .compositeAlpha(compositeAlpha)
                        .presentMode(KHRSurface.VK_PRESENT_MODE_FIFO_KHR)
                        .clipped(true)
                        .oldSwapchain(VK_NULL_HANDLE);

        LongBuffer swapchainOut = stack.mallocLong(1);
        checkVk(
                org.lwjgl.vulkan.KHRSwapchain.vkCreateSwapchainKHR(
                        logicalDevice, createInfo, null, swapchainOut),
                "vkCreateSwapchainKHR"
        );
        swapchain = swapchainOut.get(0);
        swapchainFormat = selectedFormat.format();
        swapchainWidth = extent.width();
        swapchainHeight = extent.height();

logger.info("[AetherVK] DEBUG selected swapchain format={}", selectedFormat.format());
logger.info("[AetherVK] DEBUG selected color space={}", selectedFormat.colorSpace());
        IntBuffer actualImageCount = stack.ints(0);
        checkVk(
                org.lwjgl.vulkan.KHRSwapchain.vkGetSwapchainImagesKHR(
                        logicalDevice, swapchain, actualImageCount, null),
                "vkGetSwapchainImagesKHR(count)"
        );

        LongBuffer images = stack.mallocLong(actualImageCount.get(0));
        checkVk(
                org.lwjgl.vulkan.KHRSwapchain.vkGetSwapchainImagesKHR(
                        logicalDevice, swapchain, actualImageCount, images),
                "vkGetSwapchainImagesKHR(data)"
        );

        swapchainImages = new long[actualImageCount.get(0)];
        for (int i = 0; i < swapchainImages.length; i++) {
            swapchainImages[i] = images.get(i);
        }

        logger.info(
                "[AetherVK] Swapchain created: {}x{}, format={}, images={}.",
                extent.width(), extent.height(),
                selectedFormat.format(), swapchainImages.length
        );
    }


    private static void createImageViews(Logger logger, MemoryStack stack) {
        if (swapchainImages.length == 0) {
            throw new IllegalStateException("Cannot create image views: swapchain has no images.");
        }

        imageViews = new long[swapchainImages.length];

        for (int i = 0; i < swapchainImages.length; i++) {
            VkImageViewCreateInfo viewInfo =
                    VkImageViewCreateInfo.calloc(stack)
                            .sType$Default()
                            .image(swapchainImages[i])
                            .viewType(VK_IMAGE_VIEW_TYPE_2D)
                            .format(swapchainFormat)
                            .components(components -> components.set(
                                    VK_COMPONENT_SWIZZLE_IDENTITY,
                                    VK_COMPONENT_SWIZZLE_IDENTITY,
                                    VK_COMPONENT_SWIZZLE_IDENTITY,
                                    VK_COMPONENT_SWIZZLE_IDENTITY
                            ))
                            .subresourceRange(range -> range
                                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                    .baseMipLevel(0)
                                    .levelCount(1)
                                    .baseArrayLayer(0)
                                    .layerCount(1)
                            );

            LongBuffer viewOut = stack.mallocLong(1);
            checkVk(
                    vkCreateImageView(logicalDevice, viewInfo, null, viewOut),
                    "vkCreateImageView[" + i + "]"
            );

            imageViews[i] = viewOut.get(0);
        }

        logger.info("[AetherVK] Created {} swapchain image views successfully.", imageViews.length);
    }

    private static void createRenderPassAndFramebuffers(Logger logger, MemoryStack stack) {
        if (imageViews.length == 0) {
            throw new IllegalStateException(
                    "Cannot create render pass: no swapchain image views exist."
            );
        }

        VkAttachmentDescription.Buffer colorAttachment =
                VkAttachmentDescription.calloc(1, stack);

        colorAttachment.get(0)
                .format(swapchainFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

        VkAttachmentReference.Buffer colorReference =
                VkAttachmentReference.calloc(1, stack);

        colorReference.get(0)
                .attachment(0)
                .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

        VkSubpassDescription.Buffer subpasses =
                VkSubpassDescription.calloc(1, stack);

        subpasses.get(0)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(1)
                .pColorAttachments(colorReference);
        org.lwjgl.vulkan.VkSubpassDependency.Buffer dependency =
        org.lwjgl.vulkan.VkSubpassDependency.calloc(1, stack);
dependency.get(0)
        .srcSubpass(VK_SUBPASS_EXTERNAL)
        .dstSubpass(0)
        .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
        .srcAccessMask(0)
        .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
        .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
        VkRenderPassCreateInfo renderPassInfo =
                VkRenderPassCreateInfo.calloc(stack)
                        .sType$Default()
                        .pAttachments(colorAttachment)
                        .pSubpasses(subpasses)
                        .pDependencies(dependency);
        LongBuffer renderPassOut = stack.mallocLong(1);

        checkVk(
                vkCreateRenderPass(logicalDevice, renderPassInfo, null, renderPassOut),
                "vkCreateRenderPass"
        );

        renderPass = renderPassOut.get(0);

        framebuffers = new long[imageViews.length];

        for (int i = 0; i < imageViews.length; i++) {
            VkFramebufferCreateInfo framebufferInfo =
                    VkFramebufferCreateInfo.calloc(stack)
                            .sType$Default()
                            .renderPass(renderPass)
                            .pAttachments(stack.longs(imageViews[i]))
                            .width(swapchainWidth)
                            .height(swapchainHeight)
                            .layers(1);

            LongBuffer framebufferOut = stack.mallocLong(1);

            checkVk(
                    vkCreateFramebuffer(
                            logicalDevice, framebufferInfo, null, framebufferOut
                    ),
                    "vkCreateFramebuffer[" + i + "]"
            );

            framebuffers[i] = framebufferOut.get(0);
        }

        logger.info(
                "[AetherVK] Render pass created successfully for format {}.",
                swapchainFormat
        );
        logger.info(
                "[AetherVK] Created {} framebuffers at {}x{}.",
                framebuffers.length, swapchainWidth, swapchainHeight
        );
    }

    private static void createCommandPoolAndBuffers(Logger logger, MemoryStack stack) {
        if (swapchainImages.length == 0) {
            throw new IllegalStateException("Cannot allocate command buffers: swapchain has no images.");
        }

        VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                .sType$Default()
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .queueFamilyIndex(graphicsQueueFamily);

        LongBuffer poolOut = stack.mallocLong(1);
        checkVk(
                vkCreateCommandPool(logicalDevice, poolInfo, null, poolOut),
                "vkCreateCommandPool"
        );
        commandPool = poolOut.get(0);

        VkCommandBufferAllocateInfo allocationInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType$Default()
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(swapchainImages.length);

        PointerBuffer commandBufferOut = stack.mallocPointer(swapchainImages.length);
        checkVk(
                vkAllocateCommandBuffers(logicalDevice, allocationInfo, commandBufferOut),
                "vkAllocateCommandBuffers"
        );

        commandBuffers = new VkCommandBuffer[swapchainImages.length];
        for (int i = 0; i < commandBuffers.length; i++) {
            commandBuffers[i] = new VkCommandBuffer(commandBufferOut.get(i), logicalDevice);
        }

        logger.info(
                "[AetherVK] Command pool created and {} primary command buffers allocated.",
                commandBuffers.length
        );
    }


    private static void renderAndPresentTestFrame(Logger logger, MemoryStack stack) {

        if (commandBuffers.length == 0 || graphicsQueue == null ||
                swapchain == 0L || renderPass == 0L) {
            throw new IllegalStateException("Vulkan rendering resources unavailable.");
        }

        VkSemaphoreCreateInfo semaphoreInfo =
                VkSemaphoreCreateInfo.calloc(stack).sType$Default();

        LongBuffer semaphoreOut = stack.mallocLong(1);

        checkVk(
                vkCreateSemaphore(logicalDevice, semaphoreInfo, null, semaphoreOut),
                "vkCreateSemaphore(image available)"
        );
        imageAvailableSemaphore = semaphoreOut.get(0);

        checkVk(
                vkCreateSemaphore(logicalDevice, semaphoreInfo, null, semaphoreOut),
                "vkCreateSemaphore(render finished)"
        );
        renderFinishedSemaphore = semaphoreOut.get(0);

        VkFenceCreateInfo fenceInfo =
                VkFenceCreateInfo.calloc(stack).sType$Default();

        LongBuffer fenceOut = stack.mallocLong(1);

        checkVk(
                vkCreateFence(logicalDevice, fenceInfo, null, fenceOut),
                "vkCreateFence(render)"
        );
        renderFence = fenceOut.get(0);

        IntBuffer imageIndex = stack.mallocInt(1);

        int acquireResult = org.lwjgl.vulkan.KHRSwapchain.vkAcquireNextImageKHR(
                logicalDevice,
                swapchain,
                -1L,
                imageAvailableSemaphore,
                0L,
                imageIndex
        );

        if (acquireResult != VK_SUCCESS &&
                acquireResult != org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR) {
            throw new IllegalStateException(
                    "vkAcquireNextImageKHR failed with VkResult=" + acquireResult
            );
        }

        int index = imageIndex.get(0);

        if (index < 0 || index >= commandBuffers.length ||
                index >= framebuffers.length) {
            throw new IllegalStateException("Acquired swapchain image index is invalid.");
        }

        VkCommandBuffer commandBuffer = commandBuffers[index];

        VkCommandBufferBeginInfo beginInfo =
                VkCommandBufferBeginInfo.calloc(stack).sType$Default();

        checkVk(
                vkBeginCommandBuffer(commandBuffer, beginInfo),
                "vkBeginCommandBuffer(render)"
        );

        org.lwjgl.vulkan.VkClearValue.Buffer clearValues =
                org.lwjgl.vulkan.VkClearValue.calloc(1, stack);

        clearValues.get(0).color()
                .float32(0, 1.0f)
                .float32(1, 0.0f)
                .float32(2, 0.0f)
                .float32(3, 1.0f);
                logger.info("[AetherVK] DEBUG clearValueCount={}", clearValues.remaining());
                logger.info("[AetherVK] DEBUG clear R={}", clearValues.get(0).color().float32(0));
                logger.info("[AetherVK] DEBUG clear G={}", clearValues.get(0).color().float32(1));
                logger.info("[AetherVK] DEBUG clear B={}", clearValues.get(0).color().float32(2)); 
        org.lwjgl.vulkan.VkRect2D renderArea =
                org.lwjgl.vulkan.VkRect2D.calloc(stack)
                        .offset(org.lwjgl.vulkan.VkOffset2D.calloc(stack).set(0, 0))
                        .extent(org.lwjgl.vulkan.VkExtent2D.calloc(stack)
                                .set(swapchainWidth, swapchainHeight));

        org.lwjgl.vulkan.VkRenderPassBeginInfo renderBeginInfo =
                org.lwjgl.vulkan.VkRenderPassBeginInfo.calloc(stack)
                        .sType$Default()
                        .renderPass(renderPass)
                        .framebuffer(framebuffers[index])
                        .renderArea(renderArea)
                        .pClearValues(clearValues);

        vkCmdBeginRenderPass(
                commandBuffer,
                renderBeginInfo,
                VK_SUBPASS_CONTENTS_INLINE
        );
        TrianglePipeline.record(commandBuffer);
        vkCmdEndRenderPass(commandBuffer);
        checkVk(
                vkEndCommandBuffer(commandBuffer),
                "vkEndCommandBuffer(render)"
        );

        PointerBuffer commandBufferPointers = stack.mallocPointer(1);
        commandBufferPointers.put(commandBuffer.address()).flip();

        VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                .sType$Default()
                .waitSemaphoreCount(1)
                .pWaitSemaphores(stack.longs(imageAvailableSemaphore))
                .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                .pCommandBuffers(commandBufferPointers)
                .pSignalSemaphores(stack.longs(renderFinishedSemaphore));

        logger.info("[AetherVK] DEBUG waitSemaphoreCount={}", submitInfo.waitSemaphoreCount());
        logger.info("[AetherVK] DEBUG imageAvailableSemaphore=0x{}", Long.toHexString(imageAvailableSemaphore));
        logger.info("[AetherVK] DEBUG renderFinishedSemaphore=0x{}", Long.toHexString(renderFinishedSemaphore));
        logger.info("[AetherVK] DEBUG waitStageMask={}", submitInfo.pWaitDstStageMask().get(0));
        if (submitInfo.waitSemaphoreCount() > 0) {
            logger.info("[AetherVK] DEBUG submittedWaitSemaphore=0x{}",
                    Long.toHexString(submitInfo.pWaitSemaphores().get(0)));
        }

        checkVk(
                vkQueueSubmit(graphicsQueue, submitInfo, renderFence),
                "vkQueueSubmit(render)"
        );
        checkVk(vkQueueWaitIdle(graphicsQueue), "vkQueueWaitIdle(before present)");
        org.lwjgl.vulkan.VkPresentInfoKHR presentInfo =
                org.lwjgl.vulkan.VkPresentInfoKHR.calloc(stack)
                        .sType$Default()
                        .pWaitSemaphores(stack.longs(renderFinishedSemaphore))
                        .swapchainCount(1)
                        .pSwapchains(stack.longs(swapchain))
                        .pImageIndices(stack.ints(index));

        int presentResult =
                org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR(
                        graphicsQueue,
                        presentInfo
                );
                  logger.info("[AetherVK] DEBUG presentResult={}", presentResult);
        if (presentResult != VK_SUCCESS &&
                presentResult != org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR) {
            throw new IllegalStateException(
                    "vkQueuePresentKHR failed with VkResult=" + presentResult
            );
        }

        checkVk(
                vkWaitForFences(
                        logicalDevice,
                        stack.longs(renderFence),
                        true,
                        -1L
                ),
                "vkWaitForFences(render)"
        );

        checkVk(vkQueueWaitIdle(graphicsQueue), "vkQueueWaitIdle(present)");

        logger.info("[AetherVK] First Vulkan clear frame rendered and presented successfully.");
    }

    private static int findPresentGraphicsQueue(VkPhysicalDevice device, MemoryStack stack) {
        IntBuffer count = stack.ints(0);
        vkGetPhysicalDeviceQueueFamilyProperties(device, count, null);
        if (count.get(0) == 0) return -1;

        VkQueueFamilyProperties.Buffer families =
                VkQueueFamilyProperties.malloc(count.get(0), stack);
        vkGetPhysicalDeviceQueueFamilyProperties(device, count, families);

        for (int i = 0; i < families.capacity(); i++) {
            if ((families.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) == 0) {
                continue;
            }

            IntBuffer presentationSupport = stack.ints(VK_FALSE);
            checkVk(
                    KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(
                            device, i, surface, presentationSupport),
                    "vkGetPhysicalDeviceSurfaceSupportKHR"
            );

            if (presentationSupport.get(0) == VK_TRUE) {
                return i;
            }
        }
        return -1;
    }

    private static Set<String> enumerateDeviceExtensions(VkPhysicalDevice device, MemoryStack stack) {
        IntBuffer count = stack.ints(0);
        checkVk(vkEnumerateDeviceExtensionProperties(device, (ByteBuffer) null, count, null),
                "vkEnumerateDeviceExtensionProperties(count)");
        VkExtensionProperties.Buffer properties = VkExtensionProperties.malloc(count.get(0), stack);
        checkVk(vkEnumerateDeviceExtensionProperties(device, (ByteBuffer) null, count, properties),
                "vkEnumerateDeviceExtensionProperties(data)");
        Set<String> names = new HashSet<>();
        for (int i = 0; i < properties.capacity(); i++) {
            names.add(properties.get(i).extensionNameString());
        }
        return names;
    }

    private static String deviceTypeName(int type) {
        return switch (type) {
            case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU -> "discrete GPU";
            case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU -> "integrated GPU";
            case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU -> "virtual GPU";
            case VK_PHYSICAL_DEVICE_TYPE_CPU -> "CPU renderer";
            default -> "other";
        };
    }

    private static void checkVk(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with VkResult=" + result);
        }
    }

    private static void destroyQuietly(Logger logger) {
        try {
            if (logicalDevice != null) {
                vkDeviceWaitIdle(logicalDevice);

                if (renderFence != 0L) {
                    vkDestroyFence(logicalDevice, renderFence, null);
                    renderFence = 0L;
                }

                if (imageAvailableSemaphore != 0L) {
                    vkDestroySemaphore(logicalDevice, imageAvailableSemaphore, null);
                    imageAvailableSemaphore = 0L;
                }

                if (renderFinishedSemaphore != 0L) {
                    vkDestroySemaphore(logicalDevice, renderFinishedSemaphore, null);
                    renderFinishedSemaphore = 0L;
                }

                for (int i = 0; i < framebuffers.length; i++) {
                    if (framebuffers[i] != 0L) {
                        vkDestroyFramebuffer(logicalDevice, framebuffers[i], null);
                    }
                }
                framebuffers = new long[0];

                if (renderPass != 0L) {
                    vkDestroyRenderPass(logicalDevice, renderPass, null);
                    renderPass = 0L;
                }

                if (commandPool != 0L) {
                    vkDestroyCommandPool(logicalDevice, commandPool, null);
                    commandPool = 0L;
                    commandBuffers = new VkCommandBuffer[0];
                }

                for (int i = 0; i < imageViews.length; i++) {
                    if (imageViews[i] != 0L) {
                        vkDestroyImageView(logicalDevice, imageViews[i], null);
                    }
                }
                imageViews = new long[0];

                if (swapchain != 0L) {
                    org.lwjgl.vulkan.KHRSwapchain.vkDestroySwapchainKHR(
                            logicalDevice, swapchain, null
                    );
                    swapchain = 0L;
                }
                swapchainImages = new long[0];

                vkDestroyDevice(logicalDevice, null);
                logicalDevice = null;
            }
            if (surface != 0L && instance != null) {
                KHRSurface.vkDestroySurfaceKHR(instance, surface, null);
                surface = 0L;
            }
            if (testWindow != 0L) {
                GLFW.glfwDestroyWindow(testWindow);
                testWindow = 0L;
            }
            if (instance != null) {
                vkDestroyInstance(instance, null);
                instance = null;
            }
        } catch (Throwable cleanupError) {
            logger.warn("[AetherVK] Error while cleaning up Vulkan bootstrap resources.", cleanupError);
        }
    }

    private static long frameCounter = 0L;
    private static boolean drawFailed = false;

    public static void drawFrame(Logger logger) {
        if (drawFailed || logicalDevice == null || graphicsQueue == null || swapchain == 0L
                || renderPass == 0L || commandBuffers.length == 0 || renderFence == 0L
                || imageAvailableSemaphore == 0L || renderFinishedSemaphore == 0L) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer imageIndex = stack.mallocInt(1);
            int acquire = org.lwjgl.vulkan.KHRSwapchain.vkAcquireNextImageKHR(
                    logicalDevice, swapchain, 20_000_000L,
                    imageAvailableSemaphore, 0L, imageIndex);

            if (acquire == VK_TIMEOUT || acquire == VK_NOT_READY) {
                return;
            }
            if (acquire != VK_SUCCESS
                    && acquire != org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                logger.warn("[AetherVK] drawFrame stopped: vkAcquireNextImageKHR returned {} (swapchain recreation is not implemented yet).", acquire);
                drawFailed = true;
                return;
            }

            int index = imageIndex.get(0);
            if (index < 0 || index >= commandBuffers.length || index >= framebuffers.length) {
                logger.warn("[AetherVK] drawFrame stopped: invalid image index {}.", index);
                drawFailed = true;
                return;
            }

            VkCommandBuffer cb = commandBuffers[index];
            checkVk(vkResetCommandBuffer(cb, 0), "vkResetCommandBuffer(frame)");

            VkCommandBufferBeginInfo beginInfo =
                    VkCommandBufferBeginInfo.calloc(stack).sType$Default();
            checkVk(vkBeginCommandBuffer(cb, beginInfo), "vkBeginCommandBuffer(frame)");

            double phase = (System.nanoTime() % 2_000_000_000L) / 2_000_000_000.0;
            float red = 0.5f + 0.5f * (float) Math.sin(phase * 2.0 * Math.PI);

            org.lwjgl.vulkan.VkClearValue.Buffer clearValues =
                    org.lwjgl.vulkan.VkClearValue.calloc(1, stack);
            clearValues.get(0).color()
                    .float32(0, red)
                    .float32(1, 0.0f)
                    .float32(2, 0.0f)
                    .float32(3, 1.0f);

            org.lwjgl.vulkan.VkRect2D renderArea =
                    org.lwjgl.vulkan.VkRect2D.calloc(stack)
                            .offset(org.lwjgl.vulkan.VkOffset2D.calloc(stack).set(0, 0))
                            .extent(org.lwjgl.vulkan.VkExtent2D.calloc(stack)
                                    .set(swapchainWidth, swapchainHeight));

            org.lwjgl.vulkan.VkRenderPassBeginInfo renderBeginInfo =
                    org.lwjgl.vulkan.VkRenderPassBeginInfo.calloc(stack)
                            .sType$Default()
                            .renderPass(renderPass)
                            .framebuffer(framebuffers[index])
                            .renderArea(renderArea)
                            .pClearValues(clearValues);

            vkCmdBeginRenderPass(cb, renderBeginInfo, VK_SUBPASS_CONTENTS_INLINE);
            TrianglePipeline.record(cb, (float) ((System.nanoTime() % 6_000_000_000L) / 6_000_000_000.0 * 2.0 * Math.PI));
            vkCmdEndRenderPass(cb);
            checkVk(vkEndCommandBuffer(cb), "vkEndCommandBuffer(frame)");

            PointerBuffer cbs = stack.mallocPointer(1);
            cbs.put(cb.address()).flip();

            VkSubmitInfo submit = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(stack.longs(imageAvailableSemaphore))
                    .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                    .pCommandBuffers(cbs)
                    .pSignalSemaphores(stack.longs(renderFinishedSemaphore));

            checkVk(vkResetFences(logicalDevice, stack.longs(renderFence)), "vkResetFences(frame)");
            checkVk(vkQueueSubmit(graphicsQueue, submit, renderFence), "vkQueueSubmit(frame)");

            org.lwjgl.vulkan.VkPresentInfoKHR present =
                    org.lwjgl.vulkan.VkPresentInfoKHR.calloc(stack)
                            .sType$Default()
                            .pWaitSemaphores(stack.longs(renderFinishedSemaphore))
                            .swapchainCount(1)
                            .pSwapchains(stack.longs(swapchain))
                            .pImageIndices(stack.ints(index));

            int presentResult =
                    org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR(graphicsQueue, present);
            if (presentResult != VK_SUCCESS
                    && presentResult != org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                logger.warn("[AetherVK] drawFrame stopped: vkQueuePresentKHR returned {}.", presentResult);
                drawFailed = true;
                return;
            }

            checkVk(vkQueueWaitIdle(graphicsQueue), "vkQueueWaitIdle(frame)");

            frameCounter++;
            if (frameCounter == 1 || frameCounter % 100 == 0) {
                logger.info("[AetherVK] Frame {} drawn.", frameCounter);
            }
        } catch (Throwable error) {
            logger.warn("[AetherVK] drawFrame stopped after an error.", error);
            drawFailed = true;
        }
    }


    private record DeviceCandidate(VkPhysicalDevice device, String name, int graphicsQueueFamily) {}
}

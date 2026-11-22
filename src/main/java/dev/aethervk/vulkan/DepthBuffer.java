package dev.aethervk.vulkan;

import java.nio.LongBuffer;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

final class DepthBuffer {
    static final int FORMAT = VK_FORMAT_D32_SFLOAT;
    private static long[] images = new long[0];
    private static long[] memories = new long[0];
    private static long[] views = new long[0];

    private DepthBuffer() {}

    static long view(int index) {
        return views[index];
    }

    static int count() {
        return views.length;
    }

    private static void check(int result, String what) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed: VkResult=" + result);
        }
    }

    static void create(VkDevice device, VkPhysicalDevice physical, int width, int height, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Depth image count must be positive.");
        }
        images = new long[count];
        memories = new long[count];
        views = new long[count];

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties fp = VkFormatProperties.malloc(stack);
            vkGetPhysicalDeviceFormatProperties(physical, FORMAT, fp);
            if ((fp.optimalTilingFeatures() & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) == 0) {
                throw new IllegalStateException("D32_SFLOAT is not supported as a depth attachment.");
            }

            VkImageCreateInfo info = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(FORMAT)
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            info.extent().set(width, height, 1);
            VkPhysicalDeviceMemoryProperties props = VkPhysicalDeviceMemoryProperties.malloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physical, props);

            LongBuffer out = stack.mallocLong(1);
            for (int depthIndex = 0; depthIndex < count; depthIndex++) {
                check(vkCreateImage(device, info, null, out), "vkCreateImage(depth)");
                images[depthIndex] = out.get(0);

                VkMemoryRequirements req = VkMemoryRequirements.malloc(stack);
                vkGetImageMemoryRequirements(device, images[depthIndex], req);

                int typeIndex = -1;
                for (int pass = 0; pass < 2 && typeIndex < 0; pass++) {
                    int wanted = pass == 0 ? VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT : 0;
                    for (int i = 0; i < props.memoryTypeCount(); i++) {
                        if ((req.memoryTypeBits() & (1 << i)) != 0
                                && (props.memoryTypes(i).propertyFlags() & wanted) == wanted) {
                            typeIndex = i;
                            break;
                        }
                    }
                }
                if (typeIndex < 0) {
                    throw new IllegalStateException("No memory type found for the depth image.");
                }

                VkMemoryAllocateInfo alloc = VkMemoryAllocateInfo.calloc(stack)
                        .sType$Default()
                        .allocationSize(req.size())
                        .memoryTypeIndex(typeIndex);
                check(vkAllocateMemory(device, alloc, null, out), "vkAllocateMemory(depth)");
                memories[depthIndex] = out.get(0);
                check(vkBindImageMemory(device, images[depthIndex], memories[depthIndex], 0L),
                        "vkBindImageMemory(depth)");

                VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                        .sType$Default()
                        .image(images[depthIndex])
                        .viewType(VK_IMAGE_VIEW_TYPE_2D)
                        .format(FORMAT);
                viewInfo.subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
                        .baseMipLevel(0).levelCount(1)
                        .baseArrayLayer(0).layerCount(1);
                check(vkCreateImageView(device, viewInfo, null, out), "vkCreateImageView(depth)");
                views[depthIndex] = out.get(0);
            }
        }
    }

    static void destroy(VkDevice device) {
        if (device == null) {
            return;
        }
        for (int i = 0; i < views.length; i++) {
            if (views[i] != 0L) {
                vkDestroyImageView(device, views[i], null);
                views[i] = 0L;
            }
            if (images[i] != 0L) {
                vkDestroyImage(device, images[i], null);
                images[i] = 0L;
            }
            if (memories[i] != 0L) {
                vkFreeMemory(device, memories[i], null);
                memories[i] = 0L;
            }
        }
        views = new long[0];
        images = new long[0];
        memories = new long[0];
    }
}

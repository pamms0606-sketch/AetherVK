package dev.aethervk.vulkan;

import java.nio.LongBuffer;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

final class DepthBuffer {
    static final int FORMAT = VK_FORMAT_D32_SFLOAT;
    private static long image = 0L;
    private static long memory = 0L;
    private static long view = 0L;

    private DepthBuffer() {}

    static long view() {
        return view;
    }

    private static void check(int result, String what) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed: VkResult=" + result);
        }
    }

    static void create(VkDevice device, VkPhysicalDevice physical, int width, int height) {
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
            LongBuffer out = stack.mallocLong(1);
            check(vkCreateImage(device, info, null, out), "vkCreateImage(depth)");
            image = out.get(0);

            VkMemoryRequirements req = VkMemoryRequirements.malloc(stack);
            vkGetImageMemoryRequirements(device, image, req);
            VkPhysicalDeviceMemoryProperties props = VkPhysicalDeviceMemoryProperties.malloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physical, props);

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
            memory = out.get(0);
            check(vkBindImageMemory(device, image, memory, 0L), "vkBindImageMemory(depth)");

            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(FORMAT);
            viewInfo.subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
                    .baseMipLevel(0).levelCount(1)
                    .baseArrayLayer(0).layerCount(1);
            check(vkCreateImageView(device, viewInfo, null, out), "vkCreateImageView(depth)");
            view = out.get(0);
        }
    }

    static void destroy(VkDevice device) {
        if (device == null) {
            return;
        }
        if (view != 0L) {
            vkDestroyImageView(device, view, null);
            view = 0L;
        }
        if (image != 0L) {
            vkDestroyImage(device, image, null);
            image = 0L;
        }
        if (memory != 0L) {
            vkFreeMemory(device, memory, null);
            memory = 0L;
        }
    }
}

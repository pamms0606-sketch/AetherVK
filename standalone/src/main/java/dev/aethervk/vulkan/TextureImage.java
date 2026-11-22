package dev.aethervk.vulkan;

import java.nio.LongBuffer;
import java.util.Random;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

final class TextureImage {
    private static final int SIZE = 16;
    private static final int FORMAT = VK_FORMAT_R8G8B8A8_SRGB;

    private static long image = 0L;
    private static long memory = 0L;
    private static long view = 0L;
    private static long sampler = 0L;

    private TextureImage() {}

    static long view() {
        return view;
    }

    static long sampler() {
        return sampler;
    }

    private static void check(int result, String what) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed: VkResult=" + result);
        }
    }

    private static int findMemoryType(VkPhysicalDeviceMemoryProperties props, int typeBits, int wanted) {
        for (int i = 0; i < props.memoryTypeCount(); i++) {
            if ((typeBits & (1 << i)) != 0 && (props.memoryTypes(i).propertyFlags() & wanted) == wanted) {
                return i;
            }
        }
        return -1;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static byte[] generatePixels() {
        byte[] px = new byte[SIZE * SIZE * 4];
        Random rnd = new Random(42);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int base = 90 + rnd.nextInt(40);
                int r = base + 30;
                int g = base;
                int b = base - 40;
                if (x == 0 || y == 0 || x == SIZE - 1 || y == SIZE - 1) {
                    r = r * 6 / 10;
                    g = g * 6 / 10;
                    b = b * 6 / 10;
                }
                if (x < 4 && y < 4) {
                    r = 255;
                    g = 0;
                    b = 0;
                }
                int i = (y * SIZE + x) * 4;
                px[i] = (byte) clamp(r);
                px[i + 1] = (byte) clamp(g);
                px[i + 2] = (byte) clamp(b);
                px[i + 3] = (byte) 255;
            }
        }
        return px;
    }

    static void create(VkDevice device, VkPhysicalDevice physical, VkQueue queue, int queueFamily) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            byte[] pixels = generatePixels();
            long size = pixels.length;
            LongBuffer out = stack.mallocLong(1);

            VkPhysicalDeviceMemoryProperties props = VkPhysicalDeviceMemoryProperties.malloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physical, props);

            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            check(vkCreateBuffer(device, bufferInfo, null, out), "vkCreateBuffer(staging)");
            long stagingBuffer = out.get(0);

            VkMemoryRequirements bufReq = VkMemoryRequirements.malloc(stack);
            vkGetBufferMemoryRequirements(device, stagingBuffer, bufReq);
            int stagingType = findMemoryType(props, bufReq.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            if (stagingType < 0) {
                throw new IllegalStateException("No host-visible memory type for the staging buffer.");
            }
            VkMemoryAllocateInfo stagingAlloc = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(bufReq.size())
                    .memoryTypeIndex(stagingType);
            check(vkAllocateMemory(device, stagingAlloc, null, out), "vkAllocateMemory(staging)");
            long stagingMemory = out.get(0);
            check(vkBindBufferMemory(device, stagingBuffer, stagingMemory, 0L), "vkBindBufferMemory(staging)");

            PointerBuffer data = stack.mallocPointer(1);
            check(vkMapMemory(device, stagingMemory, 0L, size, 0, data), "vkMapMemory(staging)");
            MemoryUtil.memByteBuffer(data.get(0), (int) size).put(pixels);
            vkUnmapMemory(device, stagingMemory);

            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(FORMAT)
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.extent().set(SIZE, SIZE, 1);
            check(vkCreateImage(device, imageInfo, null, out), "vkCreateImage(texture)");
            image = out.get(0);

            VkMemoryRequirements imgReq = VkMemoryRequirements.malloc(stack);
            vkGetImageMemoryRequirements(device, image, imgReq);
            int imageType = findMemoryType(props, imgReq.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            if (imageType < 0) {
                imageType = findMemoryType(props, imgReq.memoryTypeBits(), 0);
            }
            if (imageType < 0) {
                throw new IllegalStateException("No memory type for the texture image.");
            }
            VkMemoryAllocateInfo imageAlloc = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(imgReq.size())
                    .memoryTypeIndex(imageType);
            check(vkAllocateMemory(device, imageAlloc, null, out), "vkAllocateMemory(texture)");
            memory = out.get(0);
            check(vkBindImageMemory(device, image, memory, 0L), "vkBindImageMemory(texture)");

            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_COMMAND_POOL_CREATE_TRANSIENT_BIT)
                    .queueFamilyIndex(queueFamily);
            check(vkCreateCommandPool(device, poolInfo, null, out), "vkCreateCommandPool(upload)");
            long pool = out.get(0);

            VkCommandBufferAllocateInfo cbInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(pool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            PointerBuffer cbOut = stack.mallocPointer(1);
            check(vkAllocateCommandBuffers(device, cbInfo, cbOut), "vkAllocateCommandBuffers(upload)");
            VkCommandBuffer cb = new VkCommandBuffer(cbOut.get(0), device);

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            check(vkBeginCommandBuffer(cb, beginInfo), "vkBeginCommandBuffer(upload)");

            VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0)
                    .sType$Default()
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image)
                    .srcAccessMask(0)
                    .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            barrier.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(1)
                    .baseArrayLayer(0).layerCount(1);
            vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, null, barrier);

            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
            region.get(0).bufferOffset(0L).bufferRowLength(0).bufferImageHeight(0);
            region.get(0).imageSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0).baseArrayLayer(0).layerCount(1);
            region.get(0).imageOffset().set(0, 0, 0);
            region.get(0).imageExtent().set(SIZE, SIZE, 1);
            vkCmdCopyBufferToImage(cb, stagingBuffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);

            barrier.get(0)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
            vkCmdPipelineBarrier(cb, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0, null, null, barrier);

            check(vkEndCommandBuffer(cb), "vkEndCommandBuffer(upload)");

            VkSubmitInfo submit = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .pCommandBuffers(stack.pointers(cb));
            check(vkQueueSubmit(queue, submit, VK_NULL_HANDLE), "vkQueueSubmit(upload)");
            check(vkQueueWaitIdle(queue), "vkQueueWaitIdle(upload)");

            vkDestroyCommandPool(device, pool, null);
            vkDestroyBuffer(device, stagingBuffer, null);
            vkFreeMemory(device, stagingMemory, null);

            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(FORMAT);
            viewInfo.subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(1)
                    .baseArrayLayer(0).layerCount(1);
            check(vkCreateImageView(device, viewInfo, null, out), "vkCreateImageView(texture)");
            view = out.get(0);

            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(VK_FILTER_NEAREST)
                    .minFilter(VK_FILTER_NEAREST)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .anisotropyEnable(false)
                    .maxAnisotropy(1.0f)
                    .compareEnable(false)
                    .compareOp(VK_COMPARE_OP_ALWAYS)
                    .minLod(0.0f)
                    .maxLod(0.0f)
                    .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                    .unnormalizedCoordinates(false);
            check(vkCreateSampler(device, samplerInfo, null, out), "vkCreateSampler");
            sampler = out.get(0);
        }
    }

    static void destroy(VkDevice device) {
        if (device == null) {
            return;
        }
        if (sampler != 0L) {
            vkDestroySampler(device, sampler, null);
            sampler = 0L;
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

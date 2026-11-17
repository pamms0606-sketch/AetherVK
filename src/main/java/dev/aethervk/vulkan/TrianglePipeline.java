package dev.aethervk.vulkan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;

import static org.lwjgl.vulkan.VK10.*;

final class TrianglePipeline {
    private static final int FLOATS_PER_VERTEX = 5;
    private static final int STRIDE = FLOATS_PER_VERTEX * 4;

    private static final float[] VERTICES = {
             0.0f, -0.5f,  1.0f, 0.0f, 0.0f,
             0.5f,  0.5f,  0.0f, 1.0f, 0.0f,
            -0.5f,  0.5f,  0.0f, 0.0f, 1.0f
    };

    private static long pipelineLayout = 0L;
    private static long pipeline = 0L;
    private static long vertexBuffer = 0L;
    private static long vertexMemory = 0L;

    private static float aspect = 1f;
    private static int extentW = 1;
    private static int extentH = 1;
    private TrianglePipeline() {}

    private static void check(int result, String what) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed: VkResult=" + result);
        }
    }

    private static long createShaderModule(VkDevice device, MemoryStack stack, String resource) {
        byte[] bytes;
        try (InputStream in = TrianglePipeline.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Shader resource not found: " + resource);
            }
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read shader " + resource, e);
        }

        ByteBuffer code = stack.malloc(4, bytes.length);
        code.put(bytes).flip();

        VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack)
                .sType$Default()
                .pCode(code);

        LongBuffer out = stack.mallocLong(1);
        check(vkCreateShaderModule(device, info, null, out), "vkCreateShaderModule(" + resource + ")");
        return out.get(0);
    }

    private static void createVertexBuffer(VkDevice device, VkPhysicalDevice physicalDevice, MemoryStack stack) {
        long size = (long) VERTICES.length * 4L;

        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                .sType$Default()
                .size(size)
                .usage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

        LongBuffer out = stack.mallocLong(1);
        check(vkCreateBuffer(device, bufferInfo, null, out), "vkCreateBuffer(vertex)");
        vertexBuffer = out.get(0);

        VkMemoryRequirements req = VkMemoryRequirements.malloc(stack);
        vkGetBufferMemoryRequirements(device, vertexBuffer, req);

        VkPhysicalDeviceMemoryProperties props = VkPhysicalDeviceMemoryProperties.malloc(stack);
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, props);

        int wanted = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
        int typeIndex = -1;
        for (int i = 0; i < props.memoryTypeCount(); i++) {
            if ((req.memoryTypeBits() & (1 << i)) != 0
                    && (props.memoryTypes(i).propertyFlags() & wanted) == wanted) {
                typeIndex = i;
                break;
            }
        }
        if (typeIndex < 0) {
            throw new IllegalStateException("No host-visible memory type found for the vertex buffer.");
        }

        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType$Default()
                .allocationSize(req.size())
                .memoryTypeIndex(typeIndex);
        check(vkAllocateMemory(device, allocInfo, null, out), "vkAllocateMemory(vertex)");
        vertexMemory = out.get(0);
        check(vkBindBufferMemory(device, vertexBuffer, vertexMemory, 0L), "vkBindBufferMemory(vertex)");

        PointerBuffer data = stack.mallocPointer(1);
        check(vkMapMemory(device, vertexMemory, 0L, size, 0, data), "vkMapMemory(vertex)");
        ByteBuffer bytes = MemoryUtil.memByteBuffer(data.get(0), (int) size).order(ByteOrder.nativeOrder());
        bytes.asFloatBuffer().put(VERTICES);
        vkUnmapMemory(device, vertexMemory);
    }

    static void create(Logger logger, VkDevice device, VkPhysicalDevice physicalDevice,
                       long renderPass, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            extentW = width;
            extentH = height;
            aspect = (float) width / (float) height;
            createVertexBuffer(device, physicalDevice, stack);
            logger.info("[AetherVK] Vertex buffer created ({} vertices).", VERTICES.length / FLOATS_PER_VERTEX);

            long vert = createShaderModule(device, stack, "/assets/aethervk/shaders/triangle.vert.spv");
            long frag = createShaderModule(device, stack, "/assets/aethervk/shaders/triangle.frag.spv");

            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0).sType$Default()
                    .stage(VK_SHADER_STAGE_VERTEX_BIT).module(vert).pName(stack.UTF8("main"));
            stages.get(1).sType$Default()
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(frag).pName(stack.UTF8("main"));

            VkVertexInputBindingDescription.Buffer bindings = VkVertexInputBindingDescription.calloc(1, stack);
            bindings.get(0).binding(0).stride(STRIDE).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            VkVertexInputAttributeDescription.Buffer attributes = VkVertexInputAttributeDescription.calloc(2, stack);
            attributes.get(0).binding(0).location(0).format(VK_FORMAT_R32G32_SFLOAT).offset(0);
            attributes.get(1).binding(0).location(1).format(VK_FORMAT_R32G32B32_SFLOAT).offset(8);

            VkPipelineVertexInputStateCreateInfo vertexInput =
                    VkPipelineVertexInputStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .pVertexBindingDescriptions(bindings)
                            .pVertexAttributeDescriptions(attributes);

            VkPipelineInputAssemblyStateCreateInfo inputAssembly =
                    VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                            .primitiveRestartEnable(false);

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
            viewport.get(0).x(0f).y(0f).width((float) width).height((float) height)
                    .minDepth(0f).maxDepth(1f);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.get(0).offset().set(0, 0);
            scissor.get(0).extent().set(width, height);

            VkPipelineViewportStateCreateInfo viewportState =
                    VkPipelineViewportStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .viewportCount(1).pViewports(viewport)
                            .scissorCount(1).pScissors(scissor);

            VkPipelineRasterizationStateCreateInfo raster =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .depthClampEnable(false)
                            .rasterizerDiscardEnable(false)
                            .polygonMode(VK_POLYGON_MODE_FILL)
                            .lineWidth(1.0f)
                            .cullMode(VK_CULL_MODE_NONE)
                            .frontFace(VK_FRONT_FACE_CLOCKWISE)
                            .depthBiasEnable(false);

            VkPipelineMultisampleStateCreateInfo multisample =
                    VkPipelineMultisampleStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
                            .sampleShadingEnable(false);

            VkPipelineColorBlendAttachmentState.Buffer blendAttachment =
                    VkPipelineColorBlendAttachmentState.calloc(1, stack);
            blendAttachment.get(0)
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT
                            | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                    .blendEnable(false);

            VkPipelineColorBlendStateCreateInfo colorBlend =
                    VkPipelineColorBlendStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .logicOpEnable(false)
                            .pAttachments(blendAttachment);

            VkPushConstantRange.Buffer pushRange = VkPushConstantRange.calloc(1, stack);
            pushRange.get(0).stageFlags(VK_SHADER_STAGE_VERTEX_BIT).offset(0).size(8);
            org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo dynamicState =
                    org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo.calloc(stack)
                            .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                            .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            VkPipelineLayoutCreateInfo layoutInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack).sType$Default().pPushConstantRanges(pushRange);
            LongBuffer out = stack.mallocLong(1);
            check(vkCreatePipelineLayout(device, layoutInfo, null, out), "vkCreatePipelineLayout");
            pipelineLayout = out.get(0);

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pipelineInfo.get(0)
                    .sType$Default()
                    .pStages(stages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(raster)
                    .pMultisampleState(multisample)
                    .pColorBlendState(colorBlend)
                    .pDynamicState(dynamicState)
                    .layout(pipelineLayout)
                    .renderPass(renderPass)
                    .subpass(0)
                    .basePipelineHandle(VK_NULL_HANDLE)
                    .basePipelineIndex(-1);

            check(vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, out),
                    "vkCreateGraphicsPipelines");
            pipeline = out.get(0);

            vkDestroyShaderModule(device, vert, null);
            vkDestroyShaderModule(device, frag, null);
            logger.info("[AetherVK] Triangle graphics pipeline created.");
        }
    }

    static void record(VkCommandBuffer commandBuffer) {
        record(commandBuffer, 0.0f);
    }

    static void destroy(org.lwjgl.vulkan.VkDevice device) {
        if (device == null) {
            return;
        }
        if (pipeline != 0L) {
            org.lwjgl.vulkan.VK10.vkDestroyPipeline(device, pipeline, null);
            pipeline = 0L;
        }
        if (pipelineLayout != 0L) {
            org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout(device, pipelineLayout, null);
            pipelineLayout = 0L;
        }
        if (vertexBuffer != 0L) {
            org.lwjgl.vulkan.VK10.vkDestroyBuffer(device, vertexBuffer, null);
            vertexBuffer = 0L;
        }
        if (vertexMemory != 0L) {
            org.lwjgl.vulkan.VK10.vkFreeMemory(device, vertexMemory, null);
            vertexMemory = 0L;
        }
    }

    static void setExtent(int w, int h) {
        extentW = w;
        extentH = h;
        aspect = (float) w / (float) h;
    }

    static void record(VkCommandBuffer commandBuffer, float angle) {
        if (pipeline == 0L || vertexBuffer == 0L) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
            VkViewport.Buffer vp = VkViewport.calloc(1, stack);
            vp.get(0).x(0f).y(0f).width((float) extentW).height((float) extentH).minDepth(0f).maxDepth(1f);
            vkCmdSetViewport(commandBuffer, 0, vp);
            VkRect2D.Buffer sc = VkRect2D.calloc(1, stack);
            sc.get(0).offset().set(0, 0);
            sc.get(0).extent().set(extentW, extentH);
            vkCmdSetScissor(commandBuffer, 0, sc);
            vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(vertexBuffer), stack.longs(0L));
            vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, stack.floats(angle, aspect));
            vkCmdDraw(commandBuffer, 3, 1, 0, 0);
        }
    }
}

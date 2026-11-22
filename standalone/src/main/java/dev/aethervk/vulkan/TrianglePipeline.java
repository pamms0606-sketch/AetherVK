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
    private static final int FLOATS_PER_VERTEX = 8;
    private static final int STRIDE = FLOATS_PER_VERTEX * 4;

    private static final float[] VERTICES = buildCube();

    private static float[] buildCube() {
        float h = 0.5f;
        float[][][] corners = {
            {{-h, -h,  h}, { h, -h,  h}, { h,  h,  h}, {-h,  h,  h}},
            {{ h, -h, -h}, {-h, -h, -h}, {-h,  h, -h}, { h,  h, -h}},
            {{ h, -h,  h}, { h, -h, -h}, { h,  h, -h}, { h,  h,  h}},
            {{-h, -h, -h}, {-h, -h,  h}, {-h,  h,  h}, {-h,  h, -h}},
            {{-h,  h,  h}, { h,  h,  h}, { h,  h, -h}, {-h,  h, -h}},
            {{ h, -h,  h}, {-h, -h,  h}, {-h, -h, -h}, { h, -h, -h}}
        };
        float[] shade = {0.8f, 0.8f, 0.6f, 0.6f, 1.0f, 0.5f};
        float[][] uvs = {{0f, 1f}, {1f, 1f}, {1f, 0f}, {0f, 0f}};
        float[] v = new float[24 * 8];
        int n = 0;
        for (int face = 0; face < 6; face++) {
            for (int k = 0; k < 4; k++) {
                float[] c = corners[face][k];
                v[n++] = c[0];
                v[n++] = c[1];
                v[n++] = c[2];
                v[n++] = shade[face];
                v[n++] = shade[face];
                v[n++] = shade[face];
                v[n++] = uvs[k][0];
                v[n++] = uvs[k][1];
            }
        }
        return v;
    }

    private static final short[] INDICES = buildIndices();
    private static final int INDEX_COUNT = INDICES.length;
    private static long indexBuffer = 0L;
    private static long indexMemory = 0L;

    private static short[] buildIndices() {
        short[] idx = new short[36];
        int n = 0;
        for (int face = 0; face < 6; face++) {
            int b = face * 4;
            idx[n++] = (short) b;
            idx[n++] = (short) (b + 1);
            idx[n++] = (short) (b + 2);
            idx[n++] = (short) b;
            idx[n++] = (short) (b + 2);
            idx[n++] = (short) (b + 3);
        }
        return idx;
    }

    private static long pipelineLayout = 0L;
    private static long descriptorSetLayout = 0L;
    private static long descriptorPool = 0L;
    static final int FRAMES = 2;
    private static final long[] descriptorSets = new long[FRAMES];
    private static final long[] uniformBuffers = new long[FRAMES];
    private static final long[] uniformMemories = new long[FRAMES];
    private static final long[] uniformMappeds = new long[FRAMES];
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

    private static void createUniformBuffers(VkDevice device, VkPhysicalDevice physicalDevice, MemoryStack stack) {
        for (int f = 0; f < FRAMES; f++) {
            createUniformBuffer(device, physicalDevice, stack, f);
        }
    }

    private static void createUniformBuffer(VkDevice device, VkPhysicalDevice physicalDevice, MemoryStack stack, int f) {
        long size = 64L;

        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                .sType$Default()
                .size(size)
                .usage(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

        LongBuffer out = stack.mallocLong(1);
        check(vkCreateBuffer(device, bufferInfo, null, out), "vkCreateBuffer(uniform)");
        uniformBuffers[f] = out.get(0);

        VkMemoryRequirements req = VkMemoryRequirements.malloc(stack);
        vkGetBufferMemoryRequirements(device, uniformBuffers[f], req);

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
            throw new IllegalStateException("No host-visible memory type found for the uniform buffer.");
        }

        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType$Default()
                .allocationSize(req.size())
                .memoryTypeIndex(typeIndex);
        check(vkAllocateMemory(device, allocInfo, null, out), "vkAllocateMemory(uniform)");
        uniformMemories[f] = out.get(0);
        check(vkBindBufferMemory(device, uniformBuffers[f], uniformMemories[f], 0L), "vkBindBufferMemory(uniform)");

        PointerBuffer data = stack.mallocPointer(1);
        check(vkMapMemory(device, uniformMemories[f], 0L, size, 0, data), "vkMapMemory(uniform)");
        uniformMappeds[f] = data.get(0);
    }

    private static void createDescriptors(VkDevice device, MemoryStack stack) {
        VkDescriptorSetLayoutBinding.Buffer binding = VkDescriptorSetLayoutBinding.calloc(2, stack);
        binding.get(0)
                .binding(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
        binding.get(1)
                .binding(1)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

        VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType$Default()
                .pBindings(binding);
        LongBuffer out = stack.mallocLong(1);
        check(vkCreateDescriptorSetLayout(device, layoutInfo, null, out), "vkCreateDescriptorSetLayout");
        descriptorSetLayout = out.get(0);

        VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(2, stack);
        poolSize.get(0).type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(FRAMES);
        poolSize.get(1).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(FRAMES);
        VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType$Default()
                .pPoolSizes(poolSize)
                .maxSets(FRAMES);
        check(vkCreateDescriptorPool(device, poolInfo, null, out), "vkCreateDescriptorPool");
        descriptorPool = out.get(0);

        LongBuffer layouts = stack.mallocLong(FRAMES);
        for (int f = 0; f < FRAMES; f++) {
            layouts.put(f, descriptorSetLayout);
        }
        VkDescriptorSetAllocateInfo setInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType$Default()
                .descriptorPool(descriptorPool)
                .pSetLayouts(layouts);
        LongBuffer setsOut = stack.mallocLong(FRAMES);
        check(vkAllocateDescriptorSets(device, setInfo, setsOut), "vkAllocateDescriptorSets");

        for (int f = 0; f < FRAMES; f++) {
            descriptorSets[f] = setsOut.get(f);

            VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack);
            bufferInfo.get(0).buffer(uniformBuffers[f]).offset(0L).range(64L);
            VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(2, stack);
            write.get(0)
                    .sType$Default()
                    .dstSet(descriptorSets[f])
                    .dstBinding(0)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(bufferInfo);
            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
            imageInfo.get(0)
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .imageView(TextureImage.view())
                    .sampler(TextureImage.sampler());
            write.get(1)
                    .sType$Default()
                    .dstSet(descriptorSets[f])
                    .dstBinding(1)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(imageInfo);
            vkUpdateDescriptorSets(device, write, null);
        }
    }

    private static void createIndexBuffer(VkDevice device, VkPhysicalDevice physicalDevice, MemoryStack stack) {
        long size = (long) INDICES.length * 2L;

        VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                .sType$Default()
                .size(size)
                .usage(VK_BUFFER_USAGE_INDEX_BUFFER_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

        LongBuffer out = stack.mallocLong(1);
        check(vkCreateBuffer(device, bufferInfo, null, out), "vkCreateBuffer(index)");
        indexBuffer = out.get(0);

        VkMemoryRequirements req = VkMemoryRequirements.malloc(stack);
        vkGetBufferMemoryRequirements(device, indexBuffer, req);

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
            throw new IllegalStateException("No host-visible memory type found for the index buffer.");
        }

        VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType$Default()
                .allocationSize(req.size())
                .memoryTypeIndex(typeIndex);
        check(vkAllocateMemory(device, allocInfo, null, out), "vkAllocateMemory(index)");
        indexMemory = out.get(0);
        check(vkBindBufferMemory(device, indexBuffer, indexMemory, 0L), "vkBindBufferMemory(index)");

        PointerBuffer data = stack.mallocPointer(1);
        check(vkMapMemory(device, indexMemory, 0L, size, 0, data), "vkMapMemory(index)");
        ByteBuffer bytes = MemoryUtil.memByteBuffer(data.get(0), (int) size).order(ByteOrder.nativeOrder());
        bytes.asShortBuffer().put(INDICES);
        vkUnmapMemory(device, indexMemory);
    }

    static void create(Logger logger, VkDevice device, VkPhysicalDevice physicalDevice,
                       long renderPass, int width, int height, VkQueue queue, int queueFamily) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            extentW = width;
            extentH = height;
            aspect = (float) width / (float) height;
            createVertexBuffer(device, physicalDevice, stack);
            createIndexBuffer(device, physicalDevice, stack);
            logger.info("[AetherVK] Index buffer created ({} indices).", INDICES.length);
            TextureImage.create(device, physicalDevice, queue, queueFamily);
            logger.info("[AetherVK] Texture created and uploaded (16x16).");
            createUniformBuffers(device, physicalDevice, stack);
            createDescriptors(device, stack);
            logger.info("[AetherVK] {} uniform buffers and descriptor sets created (one per frame in flight).", FRAMES);
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

            VkVertexInputAttributeDescription.Buffer attributes = VkVertexInputAttributeDescription.calloc(3, stack);
            attributes.get(0).binding(0).location(0).format(VK_FORMAT_R32G32B32_SFLOAT).offset(0);
            attributes.get(1).binding(0).location(1).format(VK_FORMAT_R32G32B32_SFLOAT).offset(12);
            attributes.get(2).binding(0).location(2).format(VK_FORMAT_R32G32_SFLOAT).offset(24);

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
                            .cullMode(VK_CULL_MODE_BACK_BIT)
                            .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
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

            
            
            VkPipelineDepthStencilStateCreateInfo depthState =
                    VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                            .sType$Default()
                            .depthTestEnable(true)
                            .depthWriteEnable(true)
                            .depthCompareOp(VK_COMPARE_OP_LESS)
                            .depthBoundsTestEnable(false)
                            .stencilTestEnable(false);
            org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo dynamicState =
                    org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo.calloc(stack)
                            .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                            .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            VkPipelineLayoutCreateInfo layoutInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack).sType$Default().pSetLayouts(stack.longs(descriptorSetLayout));
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
                    .pDepthStencilState(depthState)
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
        if (descriptorPool != 0L) {
            vkDestroyDescriptorPool(device, descriptorPool, null);
            descriptorPool = 0L;
            java.util.Arrays.fill(descriptorSets, 0L);
        }
        TextureImage.destroy(device);
        if (descriptorSetLayout != 0L) {
            vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
            descriptorSetLayout = 0L;
        }
        for (int f = 0; f < FRAMES; f++) {
            if (uniformMemories[f] != 0L) {
                vkUnmapMemory(device, uniformMemories[f]);
                uniformMappeds[f] = 0L;
            }
            if (uniformBuffers[f] != 0L) {
                vkDestroyBuffer(device, uniformBuffers[f], null);
                uniformBuffers[f] = 0L;
            }
            if (uniformMemories[f] != 0L) {
                vkFreeMemory(device, uniformMemories[f], null);
                uniformMemories[f] = 0L;
            }
        }
        if (indexBuffer != 0L) {
            org.lwjgl.vulkan.VK10.vkDestroyBuffer(device, indexBuffer, null);
            indexBuffer = 0L;
        }
        if (indexMemory != 0L) {
            org.lwjgl.vulkan.VK10.vkFreeMemory(device, indexMemory, null);
            indexMemory = 0L;
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
        record(commandBuffer, angle, 0);
    }

    static void record(VkCommandBuffer commandBuffer, float angle, int frame) {
        if (pipeline == 0L || vertexBuffer == 0L || frame < 0 || frame >= FRAMES) {
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
            org.joml.Matrix4f mvp = new org.joml.Matrix4f()
                    .scale(1.0f, -1.0f, 1.0f)
                    .perspective((float) Math.toRadians(60.0), aspect, 0.1f, 100.0f, true)
                    .translate(0.0f, 0.0f, -2.5f)
                    .rotateY(angle)
                    .rotateX(angle * 0.5f);
            ByteBuffer uniformData = MemoryUtil.memByteBuffer(uniformMappeds[frame], 64).order(ByteOrder.nativeOrder());
            mvp.get(uniformData);
            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout, 0, stack.longs(descriptorSets[frame]), null);
            vkCmdBindIndexBuffer(commandBuffer, indexBuffer, 0L, VK_INDEX_TYPE_UINT16);
            vkCmdDrawIndexed(commandBuffer, INDEX_COUNT, 1, 0, 0, 0);
        }
    }
}

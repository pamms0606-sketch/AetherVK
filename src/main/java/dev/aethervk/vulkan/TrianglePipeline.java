package dev.aethervk.vulkan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;

import static org.lwjgl.vulkan.VK10.*;

final class TrianglePipeline {
    private static long pipelineLayout = 0L;
    private static long pipeline = 0L;

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

    static void create(Logger logger, VkDevice device, long renderPass, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long vert = createShaderModule(device, stack, "/assets/aethervk/shaders/triangle.vert.spv");
            long frag = createShaderModule(device, stack, "/assets/aethervk/shaders/triangle.frag.spv");

            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0).sType$Default()
                    .stage(VK_SHADER_STAGE_VERTEX_BIT).module(vert).pName(stack.UTF8("main"));
            stages.get(1).sType$Default()
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(frag).pName(stack.UTF8("main"));

            VkPipelineVertexInputStateCreateInfo vertexInput =
                    VkPipelineVertexInputStateCreateInfo.calloc(stack).sType$Default();

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
                            .attachmentCount(1)
                            .pAttachments(blendAttachment);

            VkPipelineLayoutCreateInfo layoutInfo =
                    VkPipelineLayoutCreateInfo.calloc(stack).sType$Default();
            LongBuffer out = stack.mallocLong(1);
            check(vkCreatePipelineLayout(device, layoutInfo, null, out), "vkCreatePipelineLayout");
            pipelineLayout = out.get(0);

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pipelineInfo.get(0)
                    .sType$Default()
                    .stageCount(2)
                    .pStages(stages)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(raster)
                    .pMultisampleState(multisample)
                    .pColorBlendState(colorBlend)
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
        if (pipeline == 0L) {
            return;
        }
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        vkCmdDraw(commandBuffer, 3, 1, 0, 0);
    }
}

# AetherVK 0.1.0 — macOS Apple Silicon bootstrap

Experimental client-side Vulkan backend prototype for **Minecraft 1.21.1 + NeoForge**.

> This is a development milestone, not a finished shader mod. It does **not** replace Minecraft's OpenGL renderer and does not draw the world. Minecraft keeps rendering through its normal path. AetherVK currently opens a separate GLFW Vulkan test window, clears it to a solid color and draws a single triangle. It does not parse Iris shader packs or run Bliss/BSL/Complementary yet. If Vulkan initialization fails, Minecraft keeps its normal renderer.

## Current milestone

Standalone Vulkan prototype, running in a separate test window next to Minecraft:

- NeoForge client-only mod entry point.
- LWJGL Vulkan binding aligned to Minecraft 1.21.1's LWJGL 3.3.3 line.
- macOS Apple Silicon native classifier selected automatically when building on an ARM Mac.
- Optional Vulkan SDK loader selection through `VULKAN_SDK` or `-Daethervk.vulkanLibrary=...`.
- Vulkan instance creation, portability-enumeration support, physical-device enumeration, graphics queue-family selection and logical-device creation.
- Vulkan surface for a separate GLFW window (`GLFW_NO_API`) and a swapchain.
- Swapchain image views, render pass, framebuffers, command pool and command buffers, semaphores and a fence.
- One frame that clears the swapchain image to solid red, draws a colored triangle with a graphics pipeline and GLSL shaders (compiled to SPIR-V), and presents it.
- Graceful failure logging and cleanup.

Not done yet: vertex buffers, a continuous render loop, swapchain recreation on resize, freeing Vulkan objects on shutdown, and any integration with Minecraft's own rendering.

## Tested on

- MacBook Air M4 (Apple Silicon), macOS 26.6.2
- Vulkan SDK 1.4.363.1 with the KosmicKrisp driver (26.2.99)
- Zulu OpenJDK 21.0.11, LWJGL 3.3.3, Gradle 9.9.1
- Minecraft 1.21.1, NeoForge 21.1.172

Other setups have not been tested.

## Requirements

- macOS on Apple Silicon for the first target.
- JDK 21.
- Gradle (developed with 9.9.1).
- Minecraft 1.21.1 and NeoForge 21.1.172 for testing.
- Current macOS Vulkan SDK recommended. On Apple Silicon, the current Khronos documentation recommends the KosmicKrisp Vulkan-on-Metal driver through the SDK. Driver availability and feature coverage depend on the installed SDK and macOS version.

The project does **not** bundle a GPU driver. Install the Vulkan SDK from the official LunarG site and follow its macOS setup guide. If `VULKAN_SDK` points to the SDK root, AetherVK attempts to use `$VULKAN_SDK/lib/libvulkan.1.dylib`. Otherwise LWJGL uses its default macOS Vulkan native provider.

## Build

From this directory:

```bash
gradle --version
gradle build
```

The resulting mod JAR will be in `build/libs/`.

If you use IntelliJ IDEA, open this directory as a Gradle project and let it import the Gradle model. A Gradle wrapper is not included in this repository yet, so use a locally installed Gradle.

## Run the development client

```bash
gradle runClient
```

Use a clean NeoForge 1.21.1 profile for initial testing. Read `latest.log` and search for `[AetherVK]`. A separate window titled "AetherVK Vulkan Test" should appear next to the Minecraft window, with a red background and a colored triangle.

## Shaders

The triangle shaders live in `src/main/resources/assets/aethervk/shaders/`. The compiled `.spv` files are committed. To rebuild them after editing the GLSL, install the Vulkan SDK and run `glslc triangle.vert -o triangle.vert.spv` (and the same for `triangle.frag`).

## macOS SDK configuration

If the Vulkan SDK installer does not configure `VULKAN_SDK` in the shell that launches your IDE, set it in that environment. You can also explicitly select the loader:

```text
-Daethervk.vulkanLibrary=/absolute/path/to/libvulkan.1.dylib
```

Do not copy random Vulkan `.dylib` files into the Minecraft installation. Use the SDK's documented loader/driver setup.

## Roadmap

1. **0.1 — Vulkan bootstrap:** instance + physical/logical device. Done.
2. **0.2 to 0.9 — Standalone presentation test:** surface, swapchain, image views, command buffers, synchronization, render pass, framebuffers, a clear-and-present test and a first triangle (graphics pipeline and GLSL shaders) in a separate window. Done.
3. **Next — Render bridge prototype:** prove a small Vulkan pass can coexist with Minecraft's lifecycle, then design a controlled replacement path for the world renderer. This includes vertex buffers, swapchain recreation on resize and integration with Minecraft's window.
4. **Shader-pack reader:** safely inspect ZIP packs, parse `shaders.properties`, discover programs and options.
5. **Iris compatibility layer:** implement program/pass scheduling, uniforms, samplers, image formats, shadow passes and G-buffer semantics.
6. **Shader coverage:** test simple packs first, then progressively test BSL, Complementary and Bliss. Compatibility will be feature-by-feature, not guaranteed for every pack.
7. **Later — Windows/Linux:** platform-specific native selection, surface extensions and driver testing.

## Important architecture note

A Vulkan instance/device alone cannot render Minecraft. The difficult part is the renderer bridge: Minecraft 1.21.1 is built around its existing OpenGL renderer and window/context lifecycle. AetherVK must eventually own a compatible presentation path and recreate Minecraft's required world-rendering behavior (terrain, entities, particles, transparency, depth, shadows, post-processing, resource reloads, etc.). Iris shader packs are a substantial runtime contract, not just GLSL files. This project deliberately validates Vulkan first before attempting that larger rewrite.

## License

The starter source is MIT licensed. Third-party shader packs retain their own licenses; AetherVK will load user-provided packs and will not redistribute them.

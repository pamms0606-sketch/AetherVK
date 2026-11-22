# AetherVK standalone

El mismo cubo del mod, sin Minecraft. Copias sin refactorizar de las clases de `src/main/java/dev/aethervk/vulkan/`
(único cambio: se quitó un import de Minecraft sin uso y se añadió `isRunning()` en `VulkanBootstrap`).

    bash run.sh --driver kosmickrisp
    bash run.sh --driver moltenvk
    bash run.sh --driver lwjgl          # sin VULKAN_SDK: usa el loader que trae LWJGL
    bash run.sh --driver kosmickrisp --sync   # con validacion de sincronizacion

Cada corrida guarda su log en `logs/` con el hash de git y si el arbol tenia cambios locales.

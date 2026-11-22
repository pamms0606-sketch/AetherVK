#!/bin/bash
# Uso: bash run.sh --driver kosmickrisp|moltenvk|lwjgl [--sync]
# Compila los shaders, abre el cubo con el driver elegido y guarda el log en logs/.
cd "$(dirname "$0")"
SDK="${AETHERVK_SDK:-/Users/montachana/VulkanSDK/1.4.363.1/macOS}"
MODE=""
SYNC=0
while [ $# -gt 0 ]; do
    case "$1" in
        --driver) MODE="$2"; shift 2 ;;
        --sync) SYNC=1; shift ;;
        *) echo "Argumento desconocido: $1"; exit 2 ;;
    esac
done

case "$MODE" in
    kosmickrisp)
        export VULKAN_SDK="$SDK"
        export VK_DRIVER_FILES="$SDK/share/vulkan/icd.d/libkosmickrisp_icd.json" ;;
    moltenvk)
        export VULKAN_SDK="$SDK"
        export VK_DRIVER_FILES="$SDK/share/vulkan/icd.d/MoltenVK_icd.json" ;;
    lwjgl)
        unset VULKAN_SDK VK_DRIVER_FILES VK_ICD_FILENAMES ;;
    *)
        echo "Uso: bash run.sh --driver kosmickrisp|moltenvk|lwjgl [--sync]"; exit 2 ;;
esac

export VK_LOADER_LAYERS_ENABLE='*validation'
if [ "$SYNC" = 1 ]; then
    export VK_LAYER_ENABLES=VK_VALIDATION_FEATURE_ENABLE_SYNCHRONIZATION_VALIDATION_EXT
fi

SH=src/main/resources/assets/aethervk/shaders
echo "Compilando shaders..."
for s in vert frag; do
    if ! glslc $SH/triangle.$s -o $SH/triangle.$s.spv; then
        echo "FALLO EL SHADER ($s). Dile a Claude 'listo' y lo lee."
        exit 1
    fi
done

mkdir -p logs
STAMP="$(date +%Y%m%d-%H%M%S)"
LOG="logs/$STAMP-$MODE$([ "$SYNC" = 1 ] && echo -sync).txt"
REPO="$(git rev-parse --show-toplevel 2>/dev/null)"
{
    echo "== AetherVK standalone =="
    echo "fecha: $(date)"
    echo "driver pedido: $MODE  sync: $SYNC"
    echo "macOS: $(sw_vers -productVersion 2>/dev/null)"
    echo "git HEAD: $(git -C "$REPO" rev-parse HEAD 2>/dev/null)"
    DIRTY="$(git -C "$REPO" status --porcelain -- standalone src 2>/dev/null | grep -v -E ' standalone/(logs|build|\.gradle)/?')"
    if [ -n "$DIRTY" ]; then
        # Hash del diff de archivos seguidos + contenido de los no seguidos (sin logs ni build).
        H="$( { git -C "$REPO" diff HEAD -- standalone src 2>/dev/null; \
                git -C "$REPO" ls-files -o --exclude-standard -- standalone src 2>/dev/null \
                  | grep -v -E '^standalone/(logs|build|\.gradle)/' \
                  | while read -r f; do shasum "$REPO/$f"; done; } | shasum | cut -c1-12 )"
        echo "arbol: CON cambios locales (hash: $H)"
    else
        echo "arbol: limpio"
    fi
    echo "VULKAN_SDK=${VULKAN_SDK:-<no definida>}"
    echo "VK_DRIVER_FILES=${VK_DRIVER_FILES:-<no definida>}"
    echo "======================="
} | tee "$LOG"

echo "Abriendo el cubo (cierra la ventana cuando termines)..."
gradle run --console=plain 2>&1 | tee -a "$LOG"
echo "Terminado. Log: standalone/$LOG. Dile a Claude 'listo'."

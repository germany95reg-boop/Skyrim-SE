// Skyrim SE Mobile v0.6: interactive Vulkan perspective geometry benchmark.
// This renderer executes no Skyrim game code, and loads no Windows binaries.
#define VK_USE_PLATFORM_ANDROID_KHR 1
#include <jni.h>
#include <android/asset_manager_jni.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <vulkan/vulkan.h>
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstring>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

namespace {
const char* TAG = "SkyrimSESceneV14";
void ck(VkResult result, const char* step) {
    if (result != VK_SUCCESS) {
        throw std::runtime_error(std::string(step) + " VkResult=" + std::to_string(result));
    }
}
std::string esc(const std::string& src) {
    std::string dst;
    for (unsigned char c : src) {
        if (c == '\\' || c == '"') { dst += '\\'; dst += static_cast<char>(c); }
        else if (c == '\n') dst += "\\n";
        else if (c >= 32) dst += static_cast<char>(c);
    }
    return dst;
}

std::atomic<bool> stopRequested{true};
// Thread-safe controller values updated from Android UI thread.
std::atomic<float> cameraYaw{0.0f};
std::atomic<float> cameraPitch{0.42f};
std::atomic<float> cameraDistance{4.4f};
std::atomic<float> cameraViewportOffset{0.0f};
std::atomic<bool> autoRotate{true};
std::atomic<int> maxFps{60};
std::mutex geometryMutex;
std::vector<float> selectedGeometry;
std::vector<float> selectedTriangles;
std::vector<float> selectedUv;
std::vector<float> selectedMaterialSlots; // one material tile per triangle vertex
int selectedDiffuseAtlasGrid=0;
std::vector<uint8_t> selectedPixels;
uint32_t selectedTextureWidth=0,selectedTextureHeight=0;
bool selectedNormalAtlas=false; // first half diffuse, second half normal
std::atomic<bool> useTexture{false};
std::atomic<bool> useNormalMap{false};
std::atomic<int> previewLightMode{0};
std::atomic<float> previewReliefStrength{1.0f};
std::atomic<float> previewSpecularStrength{0.0f}; // OFF preserves proven v2.3 image
std::atomic<int> previewSpecularSize{1};
std::atomic<bool> solidMode{false};
uint32_t geometryTriangleCount = 0;
std::string geometryLabel = "Куб (встроенный)";
uint32_t geometryEdgeCount = 12;

std::vector<float> defaultCube() {
    constexpr float xyz[8][3] = {
        {-1,-1,-1},{1,-1,-1},{1,1,-1},{-1,1,-1},
        {-1,-1,1},{1,-1,1},{1,1,1},{-1,1,1}
    };
    constexpr int edges[][2] = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
    std::vector<float> vertices;vertices.reserve(12*6);
    for(const auto & edge:edges)for(int index:edge)for(float x:xyz[index])vertices.push_back(x);
    return vertices;
}

std::mutex statsMutex;
std::string stage = "Ожидание запуска";
std::string gpuName;
uint64_t renderedFrames = 0;
double measuredFps = 0;
uint32_t frameWidth = 0, frameHeight = 0;
bool rendering = false;
bool failed = false;
std::string activeTextureMode = "off";

void setStats(const std::string& msg, bool error = false) {
    std::lock_guard<std::mutex> guard(statsMutex);
    stage = msg;
    failed = error;
}

std::vector<uint32_t> loadSpirv(AAssetManager* assets, const char* name) {
    if (!assets) throw std::runtime_error("Android asset manager is null");
    AAsset* asset = AAssetManager_open(assets, name, AASSET_MODE_STREAMING);
    if (!asset) throw std::runtime_error(std::string("Missing SPIR-V asset: ") + name);
    off64_t length = AAsset_getLength64(asset);
    if (length <= 0 || length % 4 || length > 1024 * 1024) {
        AAsset_close(asset);
        throw std::runtime_error("SPIR-V asset size is invalid");
    }
    std::vector<uint32_t> words(static_cast<size_t>(length) / 4);
    auto* dst = reinterpret_cast<unsigned char*>(words.data());
    size_t offset = 0;
    while (offset < static_cast<size_t>(length)) {
        int n = AAsset_read(asset, dst + offset, static_cast<size_t>(length) - offset);
        if (n <= 0) { AAsset_close(asset); throw std::runtime_error("Incomplete SPIR-V asset read"); }
        offset += static_cast<size_t>(n);
    }
    AAsset_close(asset);
    if (words[0] != 0x07230203) throw std::runtime_error("Invalid SPIR-V magic");
    return words;
}

struct Renderer {
    ANativeWindow* window = nullptr;
    VkInstance instance = VK_NULL_HANDLE;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkPhysicalDevice physical = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    uint32_t family = UINT32_MAX;
    VkQueue queue = VK_NULL_HANDLE;
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    VkExtent2D size{};
    VkFormat format = VK_FORMAT_UNDEFINED;
    VkRenderPass pass = VK_NULL_HANDLE;
    VkPipelineLayout layout = VK_NULL_HANDLE;
    VkPipeline pipeline = VK_NULL_HANDLE;
    VkPipeline solidPipeline = VK_NULL_HANDLE;
    VkPipeline texturePipeline=VK_NULL_HANDLE;
    VkPipelineLayout textureLayout=VK_NULL_HANDLE;
    VkDescriptorSetLayout textureSetLayout=VK_NULL_HANDLE;
    VkDescriptorPool textureDescriptorPool=VK_NULL_HANDLE;
    VkDescriptorSet textureDescriptor=VK_NULL_HANDLE;
    std::string textureMode="off";
    std::string textureCompatibility;
    bool hasNormalAtlas=false;
    int diffuseAtlasGrid=0; // persistent renderer state, accessed by frame()
    VkImage sampledImage=VK_NULL_HANDLE;
    VkDeviceMemory sampledMemory=VK_NULL_HANDLE;
    VkImageView sampledView=VK_NULL_HANDLE;
    VkSampler sampledSampler=VK_NULL_HANDLE;
    VkBuffer texturedBuffer=VK_NULL_HANDLE;
    VkDeviceMemory texturedMemory=VK_NULL_HANDLE;
    VkBuffer stagingBuffer=VK_NULL_HANDLE;
    VkDeviceMemory stagingMemory=VK_NULL_HANDLE;
    VkImage depthImage = VK_NULL_HANDLE;
    VkDeviceMemory depthMemory = VK_NULL_HANDLE;
    VkImageView depthView = VK_NULL_HANDLE;
    VkFormat depthFormat = VK_FORMAT_UNDEFINED;
    VkShaderModule vertexModule = VK_NULL_HANDLE, fragmentModule = VK_NULL_HANDLE;
    VkShaderModule solidFragmentModule = VK_NULL_HANDLE;
    VkShaderModule textureVertexModule=VK_NULL_HANDLE,textureFragmentModule=VK_NULL_HANDLE;
    VkCommandPool pool = VK_NULL_HANDLE;
    VkCommandBuffer cmd = VK_NULL_HANDLE;
    VkSemaphore acquireSem = VK_NULL_HANDLE, presentSem = VK_NULL_HANDLE;
    VkBuffer vertexBuffer = VK_NULL_HANDLE;
    VkDeviceMemory vertexMemory = VK_NULL_HANDLE;
    VkBuffer triangleBuffer = VK_NULL_HANDLE;
    VkDeviceMemory triangleMemory = VK_NULL_HANDLE;
    uint32_t triangleVertexCount = 0;
    uint32_t vertexCount = 0;
    std::vector<VkImage> images;
    std::vector<VkImageView> views;
    std::vector<VkFramebuffer> buffers;
    std::string gpu;

    ~Renderer() {
        if (device) {
            vkDeviceWaitIdle(device);
            if (acquireSem) vkDestroySemaphore(device, acquireSem, nullptr);
            if (presentSem) vkDestroySemaphore(device, presentSem, nullptr);
            if (pool) vkDestroyCommandPool(device, pool, nullptr);
            if (vertexBuffer) vkDestroyBuffer(device, vertexBuffer, nullptr);
            if (vertexMemory) vkFreeMemory(device, vertexMemory, nullptr);
            if (triangleBuffer) vkDestroyBuffer(device, triangleBuffer, nullptr);
            if (triangleMemory) vkFreeMemory(device, triangleMemory, nullptr);
            if (texturePipeline) vkDestroyPipeline(device, texturePipeline, nullptr);
            if (textureLayout) vkDestroyPipelineLayout(device,textureLayout,nullptr);
            if (textureDescriptorPool) vkDestroyDescriptorPool(device,textureDescriptorPool,nullptr);
            if (sampledSampler) vkDestroySampler(device,sampledSampler,nullptr);
            if (sampledView) vkDestroyImageView(device,sampledView,nullptr);
            if (sampledImage) vkDestroyImage(device,sampledImage,nullptr);
            if (sampledMemory) vkFreeMemory(device,sampledMemory,nullptr);
            if (stagingBuffer) vkDestroyBuffer(device,stagingBuffer,nullptr);
            if (stagingMemory) vkFreeMemory(device,stagingMemory,nullptr);
            if (texturedBuffer) vkDestroyBuffer(device,texturedBuffer,nullptr);
            if (texturedMemory) vkFreeMemory(device,texturedMemory,nullptr);
            if (textureSetLayout) vkDestroyDescriptorSetLayout(device,textureSetLayout,nullptr);
            if (solidPipeline) vkDestroyPipeline(device, solidPipeline, nullptr);
            if (pipeline) vkDestroyPipeline(device, pipeline, nullptr);
            if (layout) vkDestroyPipelineLayout(device, layout, nullptr);
            if (vertexModule) vkDestroyShaderModule(device, vertexModule, nullptr);
            if (fragmentModule) vkDestroyShaderModule(device, fragmentModule, nullptr);
            if (solidFragmentModule) vkDestroyShaderModule(device, solidFragmentModule, nullptr);
            if (textureVertexModule) vkDestroyShaderModule(device,textureVertexModule,nullptr);
            if (textureFragmentModule) vkDestroyShaderModule(device,textureFragmentModule,nullptr);
            for (auto f : buffers) vkDestroyFramebuffer(device, f, nullptr);
            if (depthView) vkDestroyImageView(device, depthView, nullptr);
            if (depthImage) vkDestroyImage(device, depthImage, nullptr);
            if (depthMemory) vkFreeMemory(device, depthMemory, nullptr);
            if (pass) vkDestroyRenderPass(device, pass, nullptr);
            for (auto v : views) vkDestroyImageView(device, v, nullptr);
            if (swapchain) vkDestroySwapchainKHR(device, swapchain, nullptr);
            vkDestroyDevice(device, nullptr);
        }
        if (surface && instance) vkDestroySurfaceKHR(instance, surface, nullptr);
        if (instance) vkDestroyInstance(instance, nullptr);
        if (window) ANativeWindow_release(window);
    }
    Renderer() = default;
    Renderer(const Renderer&) = delete;
    Renderer& operator=(const Renderer&) = delete;

    void init(JNIEnv* env, jobject surfaceObject, AAssetManager* assets) {
        setStats("Инициализация Vulkan 3D…");
        if (!surfaceObject) throw std::runtime_error("Android Surface is null");
        window = ANativeWindow_fromSurface(env, surfaceObject);
        if (!window) throw std::runtime_error("Unable to obtain ANativeWindow");
        VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
        app.pApplicationName = "Skyrim SE Mobile 3D Test";
        app.applicationVersion = VK_MAKE_VERSION(0, 6, 0);
        app.pEngineName = "Independent Vulkan OBJ Wireframe Test";
        app.apiVersion = VK_API_VERSION_1_0;
        const char* instanceExtensions[] = {VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};
        VkInstanceCreateInfo instanceInfo{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
        instanceInfo.pApplicationInfo = &app;
        instanceInfo.enabledExtensionCount = 2;
        instanceInfo.ppEnabledExtensionNames = instanceExtensions;
        ck(vkCreateInstance(&instanceInfo, nullptr, &instance), "vkCreateInstance");
        VkAndroidSurfaceCreateInfoKHR surfaceInfo{VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR};
        surfaceInfo.window = window;
        ck(vkCreateAndroidSurfaceKHR(instance, &surfaceInfo, nullptr, &surface), "vkCreateAndroidSurfaceKHR");
        uint32_t deviceCount = 0;
        ck(vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr), "Enumerate device count");
        if (deviceCount == 0) throw std::runtime_error("No Vulkan GPU");
        std::vector<VkPhysicalDevice> candidates(deviceCount);
        ck(vkEnumeratePhysicalDevices(instance, &deviceCount, candidates.data()), "Enumerate GPUs");
        for (VkPhysicalDevice candidate : candidates) {
            uint32_t familiesCount = 0;
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, &familiesCount, nullptr);
            std::vector<VkQueueFamilyProperties> properties(familiesCount);
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, &familiesCount, properties.data());
            uint32_t extCount = 0;
            ck(vkEnumerateDeviceExtensionProperties(candidate, nullptr, &extCount, nullptr), "Enumerate extensions count");
            std::vector<VkExtensionProperties> ext(extCount);
            ck(vkEnumerateDeviceExtensionProperties(candidate, nullptr, &extCount, ext.data()), "Enumerate extensions");
            bool swapSupported = false;
            for (const auto& item : ext)
                if (std::strcmp(item.extensionName, VK_KHR_SWAPCHAIN_EXTENSION_NAME) == 0) swapSupported = true;
            if (!swapSupported) continue;
            for (uint32_t i = 0; i < familiesCount; ++i) {
                VkBool32 present = VK_FALSE;
                if (vkGetPhysicalDeviceSurfaceSupportKHR(candidate, i, surface, &present) != VK_SUCCESS) continue;
                if (present && (properties[i].queueFlags & VK_QUEUE_GRAPHICS_BIT)) {
                    physical = candidate;
                    family = i;
                    VkPhysicalDeviceProperties p{};
                    vkGetPhysicalDeviceProperties(candidate, &p);
                    gpu = p.deviceName;
                    break;
                }
            }
            if (physical) break;
        }
        if (!physical) throw std::runtime_error("No graphics+present queue with swapchain extension");
        float priority = 1.0f;
        VkDeviceQueueCreateInfo queueCreate{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
        queueCreate.queueFamilyIndex = family;
        queueCreate.queueCount = 1;
        queueCreate.pQueuePriorities = &priority;
        const char* extensions[] = {VK_KHR_SWAPCHAIN_EXTENSION_NAME};
        VkDeviceCreateInfo deviceCreate{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
        deviceCreate.queueCreateInfoCount = 1;
        deviceCreate.pQueueCreateInfos = &queueCreate;
        deviceCreate.enabledExtensionCount = 1;
        deviceCreate.ppEnabledExtensionNames = extensions;
        ck(vkCreateDevice(physical, &deviceCreate, nullptr, &device), "vkCreateDevice");
        vkGetDeviceQueue(device, family, 0, &queue);
        // Vertex geometry is copied from the Android document reader under a lock.
        std::vector<float> vertexData;
        std::vector<float> triangleData,uvData,tileData;
        std::vector<uint8_t> rgbaData;
        uint32_t texWidth=0,texHeight=0;
        bool normalAtlas=false;
        {
            std::lock_guard<std::mutex> lock(geometryMutex);
            vertexData = selectedGeometry.empty() ? defaultCube() : selectedGeometry;
            triangleData = selectedTriangles;
            uvData=selectedUv;tileData=selectedMaterialSlots;
            rgbaData=selectedPixels;
            texWidth=selectedTextureWidth;texHeight=selectedTextureHeight;
            normalAtlas=selectedNormalAtlas;
            diffuseAtlasGrid=selectedDiffuseAtlasGrid;
            geometryEdgeCount = static_cast<uint32_t>(vertexData.size()/6);
        }
        bool textureReady=!triangleData.empty()&&uvData.size()==(triangleData.size()/3)*2&&
            texWidth>0&&texHeight>0&&texWidth<=2048&&texHeight<=2048&&
            rgbaData.size()==static_cast<size_t>(texWidth)*texHeight*4&&
            (tileData.empty()||tileData.size()==triangleData.size()/3)&&
            (tileData.empty()?diffuseAtlasGrid==0:(diffuseAtlasGrid>=1&&diffuseAtlasGrid<=4));
        hasNormalAtlas=textureReady&&normalAtlas;
        if(textureReady){
            VkFormatProperties supported{};
            vkGetPhysicalDeviceFormatProperties(physical,VK_FORMAT_R8G8B8A8_UNORM,&supported);
            textureReady=(supported.optimalTilingFeatures&VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT)!=0;
        }
        hasNormalAtlas=hasNormalAtlas&&textureReady;
        if(vertexData.empty() || vertexData.size()%3!=0) throw std::runtime_error("Invalid OBJ vertex buffer");
        vertexCount = static_cast<uint32_t>(vertexData.size()/3);
        VkPhysicalDeviceMemoryProperties memoryProperties{};
        vkGetPhysicalDeviceMemoryProperties(physical, &memoryProperties);
        // Bounded geometry is immutable for each Surface lifecycle. Reuse the
        // validated JNI payload without changing the already proven line path.
        auto uploadBuffer=[&](const std::vector<float>& data,VkBuffer& buffer,VkDeviceMemory& memory){
            VkBufferCreateInfo vb{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
            vb.size=data.size()*sizeof(float);
            vb.usage=VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
            vb.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
            ck(vkCreateBuffer(device,&vb,nullptr,&buffer),"vkCreateBuffer(vertices)");
            VkMemoryRequirements req{};
            vkGetBufferMemoryRequirements(device,buffer,&req);
            uint32_t index=UINT32_MAX;
            for(uint32_t i=0;i<memoryProperties.memoryTypeCount;i++){
                const auto flags=memoryProperties.memoryTypes[i].propertyFlags;
                if((req.memoryTypeBits & (1u<<i)) &&
                    (flags & (VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)) ==
                    (VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)){
                    index=i;break;
                }
            }
            if(index==UINT32_MAX)throw std::runtime_error("No coherent host-visible geometry memory");
            VkMemoryAllocateInfo alloc{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
            alloc.allocationSize=req.size;alloc.memoryTypeIndex=index;
            ck(vkAllocateMemory(device,&alloc,nullptr,&memory),"vkAllocateMemory(vertices)");
            ck(vkBindBufferMemory(device,buffer,memory,0),"vkBindBufferMemory(vertices)");
            void* mapped=nullptr;
            ck(vkMapMemory(device,memory,0,vb.size,0,&mapped),"vkMapMemory(vertices)");
            std::memcpy(mapped,data.data(),static_cast<size_t>(vb.size));
            vkUnmapMemory(device,memory);
        };
        uploadBuffer(vertexData,vertexBuffer,vertexMemory);
        if(!triangleData.empty()){
            if(triangleData.size()%9!=0)throw std::runtime_error("Invalid triangle payload");
            triangleVertexCount=static_cast<uint32_t>(triangleData.size()/3);
            uploadBuffer(triangleData,triangleBuffer,triangleMemory);
            if(textureReady){
                std::vector<float> interleaved;
                interleaved.reserve(static_cast<size_t>(triangleVertexCount)*6);
                for(size_t i=0;i<triangleVertexCount;i++){
                    interleaved.insert(interleaved.end(),{triangleData[i*3],triangleData[i*3+1],
                        triangleData[i*3+2],uvData[i*2],uvData[i*2+1],
                        tileData.empty()?0.0f:tileData[i]});
                }
                uploadBuffer(interleaved,texturedBuffer,texturedMemory);
            }
        }
        VkSurfaceCapabilitiesKHR caps{};
        ck(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physical, surface, &caps), "Surface capabilities");
        if (!(caps.supportedUsageFlags & VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
            throw std::runtime_error("Surface does not support color attachment");
        uint32_t fmtCount = 0;
        ck(vkGetPhysicalDeviceSurfaceFormatsKHR(physical, surface, &fmtCount, nullptr), "Surface format count");
        if (fmtCount == 0) throw std::runtime_error("No swapchain formats");
        std::vector<VkSurfaceFormatKHR> formats(fmtCount);
        ck(vkGetPhysicalDeviceSurfaceFormatsKHR(physical, surface, &fmtCount, formats.data()), "Surface formats");
        VkSurfaceFormatKHR chosen = formats[0];
        if (chosen.format == VK_FORMAT_UNDEFINED) chosen.format = VK_FORMAT_R8G8B8A8_UNORM;
        for (auto const& entry : formats) {
            if (entry.format == VK_FORMAT_R8G8B8A8_UNORM || entry.format == VK_FORMAT_B8G8R8A8_UNORM) {
                chosen = entry; break;
            }
        }
        format = chosen.format;
        size = caps.currentExtent;
        if (size.width == UINT32_MAX) {
            auto w = static_cast<uint32_t>(std::max(1, ANativeWindow_getWidth(window)));
            auto h = static_cast<uint32_t>(std::max(1, ANativeWindow_getHeight(window)));
            size.width = std::clamp(w, caps.minImageExtent.width, caps.maxImageExtent.width);
            size.height = std::clamp(h, caps.minImageExtent.height, caps.maxImageExtent.height);
        }
        if (size.width == 0 || size.height == 0) throw std::runtime_error("Surface extent is zero");
        VkCompositeAlphaFlagBitsKHR alpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
        if (!(caps.supportedCompositeAlpha & alpha)) {
            for (VkCompositeAlphaFlagBitsKHR flag : {VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,
                 VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR, VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR}) {
                if (caps.supportedCompositeAlpha & flag) { alpha = flag; break; }
            }
        }
        uint32_t wantedImages = caps.minImageCount + 1;
        if (caps.maxImageCount && wantedImages > caps.maxImageCount) wantedImages = caps.maxImageCount;
        VkSwapchainCreateInfoKHR swapInfo{VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR};
        swapInfo.surface = surface;
        swapInfo.minImageCount = wantedImages;
        swapInfo.imageFormat = format;
        swapInfo.imageColorSpace = chosen.colorSpace;
        swapInfo.imageExtent = size;
        swapInfo.imageArrayLayers = 1;
        swapInfo.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
        swapInfo.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
        swapInfo.preTransform = caps.currentTransform;
        swapInfo.compositeAlpha = alpha;
        swapInfo.presentMode = VK_PRESENT_MODE_FIFO_KHR;
        swapInfo.clipped = VK_TRUE;
        ck(vkCreateSwapchainKHR(device, &swapInfo, nullptr, &swapchain), "vkCreateSwapchainKHR");
        uint32_t imageCount = 0;
        ck(vkGetSwapchainImagesKHR(device, swapchain, &imageCount, nullptr), "Swapchain image count");
        if (!imageCount) throw std::runtime_error("Empty swapchain");
        images.resize(imageCount);
        ck(vkGetSwapchainImagesKHR(device, swapchain, &imageCount, images.data()), "Swapchain images");
        // Depth testing is required for filled triangles; otherwise faces draw
        // in archive order and obscure one another incorrectly.
        for(VkFormat candidate:{VK_FORMAT_D16_UNORM,VK_FORMAT_D32_SFLOAT}){
            VkFormatProperties supported{};
            vkGetPhysicalDeviceFormatProperties(physical,candidate,&supported);
            if(supported.optimalTilingFeatures & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT){
                depthFormat=candidate;break;
            }
        }
        if(depthFormat==VK_FORMAT_UNDEFINED)throw std::runtime_error("No supported Vulkan depth format");
        VkImageCreateInfo imageInfo{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
        imageInfo.imageType=VK_IMAGE_TYPE_2D;
        imageInfo.format=depthFormat;
        imageInfo.extent={size.width,size.height,1};
        imageInfo.mipLevels=1;imageInfo.arrayLayers=1;
        imageInfo.samples=VK_SAMPLE_COUNT_1_BIT;
        imageInfo.tiling=VK_IMAGE_TILING_OPTIMAL;
        imageInfo.usage=VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
        imageInfo.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
        imageInfo.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
        ck(vkCreateImage(device,&imageInfo,nullptr,&depthImage),"vkCreateImage(depth)");
        VkMemoryRequirements depthRequirements{};
        vkGetImageMemoryRequirements(device,depthImage,&depthRequirements);
        uint32_t depthIndex=UINT32_MAX;
        for(uint32_t i=0;i<memoryProperties.memoryTypeCount;i++){
            if((depthRequirements.memoryTypeBits & (1u<<i)) &&
                (memoryProperties.memoryTypes[i].propertyFlags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)){
                depthIndex=i;break;
            }
        }
        if(depthIndex==UINT32_MAX)throw std::runtime_error("No device-local depth memory");
        VkMemoryAllocateInfo depthAlloc{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
        depthAlloc.allocationSize=depthRequirements.size;
        depthAlloc.memoryTypeIndex=depthIndex;
        ck(vkAllocateMemory(device,&depthAlloc,nullptr,&depthMemory),"vkAllocateMemory(depth)");
        ck(vkBindImageMemory(device,depthImage,depthMemory,0),"vkBindImageMemory(depth)");
        VkImageViewCreateInfo depthViewInfo{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
        depthViewInfo.image=depthImage;
        depthViewInfo.viewType=VK_IMAGE_VIEW_TYPE_2D;
        depthViewInfo.format=depthFormat;
        depthViewInfo.subresourceRange.aspectMask=VK_IMAGE_ASPECT_DEPTH_BIT;
        depthViewInfo.subresourceRange.levelCount=1;
        depthViewInfo.subresourceRange.layerCount=1;
        ck(vkCreateImageView(device,&depthViewInfo,nullptr,&depthView),"vkCreateImageView(depth)");
        setStats("Создание графических pipeline…");
        VkAttachmentDescription attachment{};
        attachment.format = format;
        attachment.samples = VK_SAMPLE_COUNT_1_BIT;
        attachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
        attachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        attachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        attachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        attachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        attachment.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        VkAttachmentDescription depthAttachment{};
        depthAttachment.format=depthFormat;
        depthAttachment.samples=VK_SAMPLE_COUNT_1_BIT;
        depthAttachment.loadOp=VK_ATTACHMENT_LOAD_OP_CLEAR;
        depthAttachment.storeOp=VK_ATTACHMENT_STORE_OP_DONT_CARE;
        depthAttachment.stencilLoadOp=VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        depthAttachment.stencilStoreOp=VK_ATTACHMENT_STORE_OP_DONT_CARE;
        depthAttachment.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
        depthAttachment.finalLayout=VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
        std::array<VkAttachmentDescription,2> attachments{attachment,depthAttachment};
        VkAttachmentReference attachmentRef{0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
        VkAttachmentReference depthRef{1,VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL};
        VkSubpassDescription subpass{};
        subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
        subpass.colorAttachmentCount = 1;
        subpass.pColorAttachments = &attachmentRef;
        subpass.pDepthStencilAttachment=&depthRef;
        VkSubpassDependency dep{};
        dep.srcSubpass = VK_SUBPASS_EXTERNAL;
        dep.dstSubpass = 0;
        dep.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dep.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT |
                           VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
        dep.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT |
                            VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        VkRenderPassCreateInfo passInfo{VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};
        passInfo.attachmentCount = static_cast<uint32_t>(attachments.size());
        passInfo.pAttachments = attachments.data();
        passInfo.subpassCount = 1;
        passInfo.pSubpasses = &subpass;
        passInfo.dependencyCount = 1;
        passInfo.pDependencies = &dep;
        ck(vkCreateRenderPass(device, &passInfo, nullptr, &pass), "vkCreateRenderPass");
        views.reserve(images.size());
        buffers.reserve(images.size());
        for (auto image : images) {
            VkImageViewCreateInfo viewInfo{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
            viewInfo.image = image;
            viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
            viewInfo.format = format;
            viewInfo.components = {VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY,
                                    VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY};
            viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
            viewInfo.subresourceRange.levelCount = 1;
            viewInfo.subresourceRange.layerCount = 1;
            VkImageView view = VK_NULL_HANDLE;
            ck(vkCreateImageView(device, &viewInfo, nullptr, &view), "vkCreateImageView");
            views.push_back(view);
        }
        for (auto view : views) {
            VkImageView fbAttachments[2]={view,depthView};
            VkFramebufferCreateInfo fb{VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};
            fb.renderPass = pass;
            fb.attachmentCount = 2;
            fb.pAttachments = fbAttachments;
            fb.width = size.width;
            fb.height = size.height;
            fb.layers = 1;
            VkFramebuffer buffer = VK_NULL_HANDLE;
            ck(vkCreateFramebuffer(device, &fb, nullptr, &buffer), "vkCreateFramebuffer");
            buffers.push_back(buffer);
        }
        auto vertexBytes = loadSpirv(assets, "cube.vert.spv");
        auto fragmentBytes = loadSpirv(assets, "cube.frag.spv");
        auto solidFragmentBytes = triangleVertexCount>0 ? loadSpirv(assets, "solid.frag.spv") : std::vector<uint32_t>{};
        VkShaderModuleCreateInfo shaderInfo{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
        shaderInfo.codeSize = vertexBytes.size() * sizeof(uint32_t);
        shaderInfo.pCode = vertexBytes.data();
        ck(vkCreateShaderModule(device, &shaderInfo, nullptr, &vertexModule), "vertex shader module");
        shaderInfo.codeSize = fragmentBytes.size() * sizeof(uint32_t);
        shaderInfo.pCode = fragmentBytes.data();
        ck(vkCreateShaderModule(device, &shaderInfo, nullptr, &fragmentModule), "fragment shader module");
        if(triangleVertexCount>0){
            shaderInfo.codeSize=solidFragmentBytes.size()*sizeof(uint32_t);
            shaderInfo.pCode=solidFragmentBytes.data();
            ck(vkCreateShaderModule(device,&shaderInfo,nullptr,&solidFragmentModule),"solid fragment shader module");
        }
        if(textureReady){
            auto texVert=loadSpirv(assets,"textured.vert.spv");
            auto texFrag=loadSpirv(assets,"textured.frag.spv");
            shaderInfo.codeSize=texVert.size()*sizeof(uint32_t);shaderInfo.pCode=texVert.data();
            ck(vkCreateShaderModule(device,&shaderInfo,nullptr,&textureVertexModule),"textured vertex module");
            shaderInfo.codeSize=texFrag.size()*sizeof(uint32_t);shaderInfo.pCode=texFrag.data();
            ck(vkCreateShaderModule(device,&shaderInfo,nullptr,&textureFragmentModule),"textured fragment module");
        }
        VkPushConstantRange range{};
        range.stageFlags = VK_SHADER_STAGE_VERTEX_BIT;
        range.offset = 0;
        range.size = 28; // yaw, aspect, width, height, pitch, distance, viewport vertical offset
        VkPipelineLayoutCreateInfo layoutInfo{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
        layoutInfo.pushConstantRangeCount = 1;
        layoutInfo.pPushConstantRanges = &range;
        ck(vkCreatePipelineLayout(device, &layoutInfo, nullptr, &layout), "vkCreatePipelineLayout");
        VkPipelineShaderStageCreateInfo stages[2]{};
        stages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
        stages[0].module = vertexModule;
        stages[0].pName = "main";
        stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
        stages[1].module = fragmentModule;
        stages[1].pName = "main";
        VkVertexInputBindingDescription binding{};
        binding.binding=0; binding.stride=3*sizeof(float); binding.inputRate=VK_VERTEX_INPUT_RATE_VERTEX;
        VkVertexInputAttributeDescription attribute{};
        attribute.location=0; attribute.binding=0; attribute.format=VK_FORMAT_R32G32B32_SFLOAT; attribute.offset=0;
        VkPipelineVertexInputStateCreateInfo vertexInput{VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
        vertexInput.vertexBindingDescriptionCount=1;
        vertexInput.pVertexBindingDescriptions=&binding;
        vertexInput.vertexAttributeDescriptionCount=1;
        vertexInput.pVertexAttributeDescriptions=&attribute;
        VkPipelineInputAssemblyStateCreateInfo assembly{VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO};
        assembly.topology = VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
        VkViewport viewport{0, 0, static_cast<float>(size.width), static_cast<float>(size.height), 0, 1};
        VkRect2D scissor{{0, 0}, size};
        VkPipelineViewportStateCreateInfo viewportInfo{VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO};
        viewportInfo.viewportCount = 1;
        viewportInfo.pViewports = &viewport;
        viewportInfo.scissorCount = 1;
        viewportInfo.pScissors = &scissor;
        VkPipelineRasterizationStateCreateInfo raster{VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO};
        raster.polygonMode = VK_POLYGON_MODE_FILL;
        raster.cullMode = VK_CULL_MODE_NONE;
        raster.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;
        raster.lineWidth = 1;
        VkPipelineMultisampleStateCreateInfo multisample{VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO};
        multisample.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;
        VkPipelineColorBlendAttachmentState blendAttachment{};
        blendAttachment.colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                                         VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
        VkPipelineDepthStencilStateCreateInfo depthState{VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO};
        depthState.depthTestEnable=VK_FALSE;
        depthState.depthWriteEnable=VK_FALSE;
        depthState.depthCompareOp=VK_COMPARE_OP_LESS_OR_EQUAL;
        VkPipelineColorBlendStateCreateInfo blending{VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO};
        blending.attachmentCount = 1;
        blending.pAttachments = &blendAttachment;
        VkGraphicsPipelineCreateInfo pipelineInfo{VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO};
        pipelineInfo.stageCount = 2;
        pipelineInfo.pStages = stages;
        pipelineInfo.pVertexInputState = &vertexInput;
        pipelineInfo.pInputAssemblyState = &assembly;
        pipelineInfo.pViewportState = &viewportInfo;
        pipelineInfo.pRasterizationState = &raster;
        pipelineInfo.pDepthStencilState=&depthState;
        pipelineInfo.pMultisampleState = &multisample;
        pipelineInfo.pColorBlendState = &blending;
        pipelineInfo.layout = layout;
        pipelineInfo.renderPass = pass;
        ck(vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &pipelineInfo, nullptr, &pipeline),
           "vkCreateGraphicsPipelines(lines)");
        if(triangleVertexCount>0){
            assembly.topology=VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
            stages[1].module=solidFragmentModule;
            depthState.depthTestEnable=VK_TRUE;
            depthState.depthWriteEnable=VK_TRUE;
            ck(vkCreateGraphicsPipelines(device,VK_NULL_HANDLE,1,&pipelineInfo,nullptr,&solidPipeline),
               "vkCreateGraphicsPipelines(solid)");
        }
        if(textureReady){
            VkDescriptorSetLayoutBinding samplerBinding{};
            samplerBinding.binding=0;
            samplerBinding.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            samplerBinding.descriptorCount=1;
            samplerBinding.stageFlags=VK_SHADER_STAGE_FRAGMENT_BIT;
            VkDescriptorSetLayoutCreateInfo dsInfo{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
            dsInfo.bindingCount=1;dsInfo.pBindings=&samplerBinding;
            ck(vkCreateDescriptorSetLayout(device,&dsInfo,nullptr,&textureSetLayout),"texture set layout");
            VkPushConstantRange texRange=range;
            texRange.size=56; // camera(28), atlas(4), normal(4), light(4), relief(4), gloss(8), grid(4)
            texRange.stageFlags=VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT;
            VkPipelineLayoutCreateInfo texLayoutInfo=layoutInfo;
            texLayoutInfo.pPushConstantRanges=&texRange;
            texLayoutInfo.setLayoutCount=1;texLayoutInfo.pSetLayouts=&textureSetLayout;
            ck(vkCreatePipelineLayout(device,&texLayoutInfo,nullptr,&textureLayout),"texture pipeline layout");
            VkVertexInputBindingDescription uvBinding{};
            uvBinding.binding=0;uvBinding.stride=6*sizeof(float);uvBinding.inputRate=VK_VERTEX_INPUT_RATE_VERTEX;
            VkVertexInputAttributeDescription uvAttributes[3]{};
            uvAttributes[0]=attribute;
            uvAttributes[1].location=1;uvAttributes[1].binding=0;
            uvAttributes[1].format=VK_FORMAT_R32G32_SFLOAT;uvAttributes[1].offset=3*sizeof(float);
            uvAttributes[2].location=2;uvAttributes[2].binding=0;
            uvAttributes[2].format=VK_FORMAT_R32_SFLOAT;uvAttributes[2].offset=5*sizeof(float);
            VkPipelineVertexInputStateCreateInfo uvInput=vertexInput;
            uvInput.pVertexBindingDescriptions=&uvBinding;
            uvInput.vertexAttributeDescriptionCount=3;uvInput.pVertexAttributeDescriptions=uvAttributes;
            stages[0].module=textureVertexModule;
            stages[1].module=textureFragmentModule;
            pipelineInfo.pVertexInputState=&uvInput;
            pipelineInfo.layout=textureLayout;
            // v2.14: a driver may reject the full fragment program with
            // VK_ERROR_UNKNOWN (-13), even when line and solid pipelines work.
            // Never let an optional DDS shader blank the entire 3D scene.
            const VkResult fullResult=vkCreateGraphicsPipelines(
                device,VK_NULL_HANDLE,1,&pipelineInfo,nullptr,&texturePipeline);
            if(fullResult==VK_SUCCESS){
                textureMode="full";
            }else{
                __android_log_print(ANDROID_LOG_WARN,TAG,
                    "Full DDS pipeline failed VkResult=%d; trying compatible shader",
                    static_cast<int>(fullResult));
                if(texturePipeline){
                    vkDestroyPipeline(device,texturePipeline,nullptr);
                    texturePipeline=VK_NULL_HANDLE;
                }
                // Each alternate pipeline is compiled independently. A failure
                // in one Vulkan shader module or graphics pipeline is nonfatal.
                auto tryAlternate=[&](const char* vertexAsset,const char* fragmentAsset)->VkResult{
                    VkShaderModule vs=VK_NULL_HANDLE,fs=VK_NULL_HANDLE;
                    VkResult result=VK_ERROR_UNKNOWN;
                    try{
                        auto vertexWords=loadSpirv(assets,vertexAsset);
                        auto fragmentWords=loadSpirv(assets,fragmentAsset);
                        VkShaderModuleCreateInfo altInfo{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
                        altInfo.codeSize=vertexWords.size()*sizeof(uint32_t);
                        altInfo.pCode=vertexWords.data();
                        result=vkCreateShaderModule(device,&altInfo,nullptr,&vs);
                        if(result==VK_SUCCESS){
                            altInfo.codeSize=fragmentWords.size()*sizeof(uint32_t);
                            altInfo.pCode=fragmentWords.data();
                            result=vkCreateShaderModule(device,&altInfo,nullptr,&fs);
                        }
                        if(result==VK_SUCCESS){
                            stages[0].module=vs;
                            stages[1].module=fs;
                            result=vkCreateGraphicsPipelines(
                                device,VK_NULL_HANDLE,1,&pipelineInfo,nullptr,&texturePipeline);
                        }
                    }catch(const std::exception& error){
                        __android_log_print(ANDROID_LOG_WARN,TAG,
                            "DDS fallback shader unavailable: %s",error.what());
                        result=VK_ERROR_UNKNOWN;
                    }
                    if(fs)vkDestroyShaderModule(device,fs,nullptr);
                    if(vs)vkDestroyShaderModule(device,vs,nullptr);
                    if(result!=VK_SUCCESS&&texturePipeline){
                        vkDestroyPipeline(device,texturePipeline,nullptr);
                        texturePipeline=VK_NULL_HANDLE;
                    }
                    return result;
                };
                // A deliberately simpler normal preview using the proven
                // float material varying. If unsupported, preserve the
                // diffuse-only atlas-safe path without failing the GPU scene.
                const VkResult normalSafeResult=hasNormalAtlas?tryAlternate(
                    "textured_safe.vert.spv","textured_normal_safe.frag.spv"):VK_ERROR_UNKNOWN;
                if(hasNormalAtlas&&normalSafeResult==VK_SUCCESS){
                    textureMode="normal-safe";
                    textureCompatibility=" · DDS: normal-safe preview (2 карты, упрощённое освещение)";
                }else{
                const VkResult safeResult=tryAlternate(
                    "textured_safe.vert.spv","textured_safe.frag.spv");
                if(safeResult==VK_SUCCESS){
                    textureMode="atlas-safe";
                    textureCompatibility=" · DDS: совместимый атлас (без сложного освещения)";
                }else{
                    __android_log_print(ANDROID_LOG_WARN,TAG,
                        "Safe DDS pipeline failed VkResult=%d; trying basic sampler",
                        static_cast<int>(safeResult));
                    const VkResult basicResult=tryAlternate(
                        "textured_safe.vert.spv","textured_legacy.frag.spv");
                    if(basicResult==VK_SUCCESS){
                        textureMode="basic";
                        textureCompatibility=" · DDS: базовый режим (одна текстура)";
                    }else{
                        __android_log_print(ANDROID_LOG_ERROR,TAG,
                            "All DDS pipelines failed: full=%d safe=%d basic=%d; solid remains available",
                            static_cast<int>(fullResult),static_cast<int>(safeResult),
                            static_cast<int>(basicResult));
                        textureReady=false; // Skip texture upload/descriptor if no sampler pipeline.
                        hasNormalAtlas=false;
                        textureMode="off";
                        textureCompatibility=" · DDS недоступна: каркас и поверхность работают";
                    }
                }
                }
            }
        }
        VkCommandPoolCreateInfo poolInfo{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
        poolInfo.queueFamilyIndex = family;
        poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
        ck(vkCreateCommandPool(device, &poolInfo, nullptr, &pool), "vkCreateCommandPool");
        VkCommandBufferAllocateInfo alloc{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
        alloc.commandPool = pool;
        alloc.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        alloc.commandBufferCount = 1;
        ck(vkAllocateCommandBuffers(device, &alloc, &cmd), "vkAllocateCommandBuffers");
        if(textureReady){
            // Upload only a bounded CPU-decoded first mip into optimal tiled GPU memory.
            VkBufferCreateInfo stagingInfo{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
            stagingInfo.size=rgbaData.size();
            stagingInfo.usage=VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
            stagingInfo.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
            ck(vkCreateBuffer(device,&stagingInfo,nullptr,&stagingBuffer),"texture staging buffer");
            VkMemoryRequirements req{};
            vkGetBufferMemoryRequirements(device,stagingBuffer,&req);
            auto memoryType=[&](uint32_t bits,VkMemoryPropertyFlags properties)->uint32_t{
                for(uint32_t i=0;i<memoryProperties.memoryTypeCount;i++){
                    if((bits&(1u<<i)) &&
                       (memoryProperties.memoryTypes[i].propertyFlags&properties)==properties)return i;
                }
                return UINT32_MAX;
            };
            uint32_t stagingIndex=memoryType(req.memoryTypeBits,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            if(stagingIndex==UINT32_MAX)throw std::runtime_error("No host coherent texture staging memory");
            VkMemoryAllocateInfo stagingAlloc{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
            stagingAlloc.allocationSize=req.size;stagingAlloc.memoryTypeIndex=stagingIndex;
            ck(vkAllocateMemory(device,&stagingAlloc,nullptr,&stagingMemory),"texture staging allocation");
            ck(vkBindBufferMemory(device,stagingBuffer,stagingMemory,0),"texture staging bind");
            void* mapped=nullptr;
            ck(vkMapMemory(device,stagingMemory,0,rgbaData.size(),0,&mapped),"texture staging map");
            std::memcpy(mapped,rgbaData.data(),rgbaData.size());
            vkUnmapMemory(device,stagingMemory);
            VkImageCreateInfo textureInfo{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
            textureInfo.imageType=VK_IMAGE_TYPE_2D;textureInfo.format=VK_FORMAT_R8G8B8A8_UNORM;
            textureInfo.extent={texWidth,texHeight,1};
            textureInfo.mipLevels=1;textureInfo.arrayLayers=1;
            textureInfo.samples=VK_SAMPLE_COUNT_1_BIT;
            textureInfo.tiling=VK_IMAGE_TILING_OPTIMAL;
            textureInfo.usage=VK_IMAGE_USAGE_TRANSFER_DST_BIT|VK_IMAGE_USAGE_SAMPLED_BIT;
            textureInfo.sharingMode=VK_SHARING_MODE_EXCLUSIVE;
            textureInfo.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
            ck(vkCreateImage(device,&textureInfo,nullptr,&sampledImage),"sampled image");
            vkGetImageMemoryRequirements(device,sampledImage,&req);
            uint32_t imgIndex=memoryType(req.memoryTypeBits,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            if(imgIndex==UINT32_MAX)throw std::runtime_error("No device-local texture memory");
            VkMemoryAllocateInfo imgAlloc{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
            imgAlloc.allocationSize=req.size;imgAlloc.memoryTypeIndex=imgIndex;
            ck(vkAllocateMemory(device,&imgAlloc,nullptr,&sampledMemory),"texture image allocation");
            ck(vkBindImageMemory(device,sampledImage,sampledMemory,0),"texture image bind");
            VkCommandBuffer uploadCmd=VK_NULL_HANDLE;
            ck(vkAllocateCommandBuffers(device,&alloc,&uploadCmd),"texture upload command");
            VkCommandBufferBeginInfo uploadBegin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
            uploadBegin.flags=VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
            ck(vkBeginCommandBuffer(uploadCmd,&uploadBegin),"texture upload begin");
            VkImageMemoryBarrier bar{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
            bar.oldLayout=VK_IMAGE_LAYOUT_UNDEFINED;
            bar.newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
            bar.srcQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
            bar.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
            bar.image=sampledImage;
            bar.subresourceRange.aspectMask=VK_IMAGE_ASPECT_COLOR_BIT;
            bar.subresourceRange.levelCount=1;bar.subresourceRange.layerCount=1;
            bar.dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;
            vkCmdPipelineBarrier(uploadCmd,VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT,0,0,nullptr,0,nullptr,1,&bar);
            VkBufferImageCopy region{};
            region.imageSubresource.aspectMask=VK_IMAGE_ASPECT_COLOR_BIT;
            region.imageSubresource.layerCount=1;
            region.imageExtent={texWidth,texHeight,1};
            vkCmdCopyBufferToImage(uploadCmd,stagingBuffer,sampledImage,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,1,&region);
            bar.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
            bar.newLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
            bar.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;
            bar.dstAccessMask=VK_ACCESS_SHADER_READ_BIT;
            vkCmdPipelineBarrier(uploadCmd,VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,0,0,nullptr,0,nullptr,1,&bar);
            ck(vkEndCommandBuffer(uploadCmd),"texture upload end");
            VkSubmitInfo uploadSubmit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
            uploadSubmit.commandBufferCount=1;uploadSubmit.pCommandBuffers=&uploadCmd;
            ck(vkQueueSubmit(queue,1,&uploadSubmit,VK_NULL_HANDLE),"texture upload submit");
            ck(vkQueueWaitIdle(queue),"texture upload wait");
            vkFreeCommandBuffers(device,pool,1,&uploadCmd);
            vkDestroyBuffer(device,stagingBuffer,nullptr);stagingBuffer=VK_NULL_HANDLE;
            vkFreeMemory(device,stagingMemory,nullptr);stagingMemory=VK_NULL_HANDLE;
            VkImageViewCreateInfo sampleView{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
            sampleView.image=sampledImage;
            sampleView.viewType=VK_IMAGE_VIEW_TYPE_2D;
            sampleView.format=VK_FORMAT_R8G8B8A8_UNORM;
            sampleView.subresourceRange.aspectMask=VK_IMAGE_ASPECT_COLOR_BIT;
            sampleView.subresourceRange.levelCount=1;sampleView.subresourceRange.layerCount=1;
            ck(vkCreateImageView(device,&sampleView,nullptr,&sampledView),"sampled image view");
            VkFormatProperties fmtProperties{};
            vkGetPhysicalDeviceFormatProperties(physical,VK_FORMAT_R8G8B8A8_UNORM,&fmtProperties);
            bool linear=(fmtProperties.optimalTilingFeatures&
                VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT)!=0;
            VkSamplerCreateInfo samplerInfo{VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO};
            samplerInfo.magFilter=linear?VK_FILTER_LINEAR:VK_FILTER_NEAREST;
            samplerInfo.minFilter=linear?VK_FILTER_LINEAR:VK_FILTER_NEAREST;
            samplerInfo.addressModeU=VK_SAMPLER_ADDRESS_MODE_REPEAT;
            samplerInfo.addressModeV=VK_SAMPLER_ADDRESS_MODE_REPEAT;
            samplerInfo.addressModeW=VK_SAMPLER_ADDRESS_MODE_REPEAT;
            samplerInfo.mipmapMode=VK_SAMPLER_MIPMAP_MODE_NEAREST;
            samplerInfo.maxLod=0.0f;
            ck(vkCreateSampler(device,&samplerInfo,nullptr,&sampledSampler),"texture sampler");
            VkDescriptorPoolSize poolSize{};
            poolSize.type=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            poolSize.descriptorCount=1;
            VkDescriptorPoolCreateInfo descriptorPoolInfo{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
            descriptorPoolInfo.maxSets=1;descriptorPoolInfo.poolSizeCount=1;
            descriptorPoolInfo.pPoolSizes=&poolSize;
            ck(vkCreateDescriptorPool(device,&descriptorPoolInfo,nullptr,&textureDescriptorPool),
               "texture descriptor pool");
            VkDescriptorSetAllocateInfo descriptorAlloc{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
            descriptorAlloc.descriptorPool=textureDescriptorPool;
            descriptorAlloc.descriptorSetCount=1;
            descriptorAlloc.pSetLayouts=&textureSetLayout;
            ck(vkAllocateDescriptorSets(device,&descriptorAlloc,&textureDescriptor),
               "texture descriptor set");
            VkDescriptorImageInfo imageDescriptor{};
            imageDescriptor.sampler=sampledSampler;
            imageDescriptor.imageView=sampledView;
            imageDescriptor.imageLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
            VkWriteDescriptorSet write{VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
            write.dstSet=textureDescriptor;write.dstBinding=0;
            write.descriptorCount=1;write.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            write.pImageInfo=&imageDescriptor;
            vkUpdateDescriptorSets(device,1,&write,0,nullptr);
        }
        VkSemaphoreCreateInfo semaphore{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
        ck(vkCreateSemaphore(device, &semaphore, nullptr, &acquireSem), "acquire semaphore");
        ck(vkCreateSemaphore(device, &semaphore, nullptr, &presentSem), "present semaphore");
        {
            std::lock_guard<std::mutex> guard(statsMutex);
            gpuName = gpu;
            frameWidth = size.width;
            frameHeight = size.height;
            stage = "3D-сцена работает" + textureCompatibility;
            activeTextureMode = textureMode;
            rendering = true;
        }
    }

    bool frame(float angle, float pitch, float distance) {
        uint32_t imageIndex = 0;
        VkResult acquired = vkAcquireNextImageKHR(device, swapchain, 1000000000ULL, acquireSem,
                                                  VK_NULL_HANDLE, &imageIndex);
        if (acquired == VK_ERROR_OUT_OF_DATE_KHR) return false;
        if (acquired != VK_SUCCESS && acquired != VK_SUBOPTIMAL_KHR) ck(acquired, "vkAcquireNextImageKHR");
        ck(vkResetCommandBuffer(cmd, 0), "vkResetCommandBuffer");
        VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
        begin.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        ck(vkBeginCommandBuffer(cmd, &begin), "vkBeginCommandBuffer");
        VkClearValue clearValues[2]{};
        clearValues[0].color.float32[0] = 0.035f;
        clearValues[0].color.float32[1] = 0.040f;
        clearValues[0].color.float32[2] = 0.065f;
        clearValues[0].color.float32[3] = 1.0f;
        clearValues[1].depthStencil.depth=1.0f;
        VkRenderPassBeginInfo passBegin{VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};
        passBegin.renderPass = pass;
        passBegin.framebuffer = buffers.at(imageIndex);
        passBegin.renderArea = {{0, 0}, size};
        passBegin.clearValueCount = 2;
        passBegin.pClearValues = clearValues;
        vkCmdBeginRenderPass(cmd, &passBegin, VK_SUBPASS_CONTENTS_INLINE);
        const bool filled = solidMode.load(std::memory_order_relaxed) && solidPipeline!=VK_NULL_HANDLE;
        const bool textured=filled&&useTexture.load(std::memory_order_relaxed)&&
            texturePipeline!=VK_NULL_HANDLE&&textureDescriptor!=VK_NULL_HANDLE;
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS,
                          textured?texturePipeline:(filled?solidPipeline:pipeline));
        struct Parameters {float angle, aspect, width, height, pitch, distance, focusOffsetY;};
        static_assert(sizeof(Parameters) == 28, "Push constant layout must match GLSL");
        Parameters p{angle, static_cast<float>(size.width)/static_cast<float>(size.height),
                     static_cast<float>(size.width), static_cast<float>(size.height), pitch, distance,
                     cameraViewportOffset.load(std::memory_order_relaxed)};
        VkPipelineLayout chosenLayout=textured?textureLayout:layout;
        if(textured){
            struct TexturedParameters{
                Parameters camera;
                int atlasMode;
                int normalEnabled;
                int lightMode;
                float reliefStrength;
                float specularStrength;
                int specularSize;
                int diffuseAtlasGrid;
            } texParams{p,hasNormalAtlas?(diffuseAtlasGrid>0?3:1):(diffuseAtlasGrid>0?2:0),
                (hasNormalAtlas&&useNormalMap.load(std::memory_order_relaxed))?1:0,
                previewLightMode.load(std::memory_order_relaxed),
                previewReliefStrength.load(std::memory_order_relaxed),
                previewSpecularStrength.load(std::memory_order_relaxed),
                previewSpecularSize.load(std::memory_order_relaxed),
                diffuseAtlasGrid};
            static_assert(sizeof(TexturedParameters)==56,"GLSL push constants must be 56 bytes");
            vkCmdPushConstants(cmd,chosenLayout,
                VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT,
                0,sizeof(texParams),&texParams);
        }else{
            vkCmdPushConstants(cmd,chosenLayout,VK_SHADER_STAGE_VERTEX_BIT,0,sizeof(p),&p);
        }
        if(textured)vkCmdBindDescriptorSets(cmd,VK_PIPELINE_BIND_POINT_GRAPHICS,
            textureLayout,0,1,&textureDescriptor,0,nullptr);
        VkDeviceSize offset=0;
        VkBuffer chosenBuffer=textured?texturedBuffer:(filled ? triangleBuffer : vertexBuffer);
        vkCmdBindVertexBuffers(cmd,0,1,&chosenBuffer,&offset);
        vkCmdDraw(cmd, filled ? triangleVertexCount : vertexCount, 1, 0, 0);
        vkCmdEndRenderPass(cmd);
        ck(vkEndCommandBuffer(cmd), "vkEndCommandBuffer");
        VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
        submit.waitSemaphoreCount = 1;
        submit.pWaitSemaphores = &acquireSem;
        submit.pWaitDstStageMask = &waitStage;
        submit.commandBufferCount = 1;
        submit.pCommandBuffers = &cmd;
        submit.signalSemaphoreCount = 1;
        submit.pSignalSemaphores = &presentSem;
        ck(vkQueueSubmit(queue, 1, &submit, VK_NULL_HANDLE), "vkQueueSubmit");
        VkPresentInfoKHR present{VK_STRUCTURE_TYPE_PRESENT_INFO_KHR};
        present.waitSemaphoreCount = 1;
        present.pWaitSemaphores = &presentSem;
        present.swapchainCount = 1;
        present.pSwapchains = &swapchain;
        present.pImageIndices = &imageIndex;
        VkResult result = vkQueuePresentKHR(queue, &present);
        if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR && result != VK_ERROR_OUT_OF_DATE_KHR)
            ck(result, "vkQueuePresentKHR");
        ck(vkQueueWaitIdle(queue), "vkQueueWaitIdle"); // single-frame-in-flight smoke-test, not optimized engine.
        return result != VK_ERROR_OUT_OF_DATE_KHR && acquired != VK_SUBOPTIMAL_KHR;
    }
};

void run(JNIEnv* env, jobject surface, jobject assetManager) {
    setStats("Создание Vulkan сцены…");
    AAssetManager* assets = AAssetManager_fromJava(env, assetManager);
    Renderer r;
    r.init(env, surface, assets);
    auto previous = std::chrono::steady_clock::now();
    auto mark = previous;
    uint64_t windowFrames = 0;
    float rotation = 0.0f;
    while (!stopRequested.load(std::memory_order_acquire)) {
        auto frameStart = std::chrono::steady_clock::now();
        float dt = std::clamp(std::chrono::duration<float>(frameStart - previous).count(), 0.0f, 0.05f);
        previous = frameStart;
        if (autoRotate.load(std::memory_order_relaxed)) rotation += dt * 0.9f;
        float yaw = rotation + cameraYaw.load(std::memory_order_relaxed);
        float pitch = cameraPitch.load(std::memory_order_relaxed);
        float distance = cameraDistance.load(std::memory_order_relaxed);
        if (!r.frame(yaw, pitch, distance)) {
            setStats("Vulkan Surface обновлена; требуется пересоздание swapchain");
            break;
        }
        ++windowFrames;
        {
            std::lock_guard<std::mutex> guard(statsMutex);
            ++renderedFrames;
        }
        const auto frameEnd = std::chrono::steady_clock::now();
        double windowSeconds = std::chrono::duration<double>(frameEnd - mark).count();
        if (windowSeconds >= 0.6) {
            std::lock_guard<std::mutex> guard(statsMutex);
            measuredFps = static_cast<double>(windowFrames) / windowSeconds;
            mark = frameEnd;
            windowFrames = 0;
        }
        const int cap = maxFps.load(std::memory_order_relaxed);
        if (cap > 0) {
            const auto budget = std::chrono::microseconds(1000000 / cap);
            const auto spent = frameEnd - frameStart;
            if (spent < budget) std::this_thread::sleep_for(budget - spent);
        }
    }
}
std::string statsJson() {
    std::lock_guard<std::mutex> guard(statsMutex);
    std::ostringstream out;
    out.setf(std::ios::fixed);
    out.precision(1);
    std::string activeGeometry;
    uint32_t activeEdges=0;
    uint32_t activeTriangles=0;
    {
        std::lock_guard<std::mutex> geometryLock(geometryMutex);
        activeGeometry=geometryLabel;
        activeEdges=geometryEdgeCount;
        activeTriangles=geometryTriangleCount;
    }
    out << "{\"running\":" << (rendering?"true":"false")
        << ",\"error\":" << (failed?"true":"false")
        << ",\"fps\":" << measuredFps
        << ",\"frames\":" << renderedFrames
        << ",\"width\":" << frameWidth << ",\"height\":" << frameHeight
        << ",\"gpu\":\"" << esc(gpuName) << "\""
        << ",\"yaw\":" << cameraYaw.load(std::memory_order_relaxed)
        << ",\"pitch\":" << cameraPitch.load(std::memory_order_relaxed)
        << ",\"distance\":" << cameraDistance.load(std::memory_order_relaxed)
        << ",\"autoRotate\":" << (autoRotate.load(std::memory_order_relaxed)?"true":"false")
        << ",\"fpsCap\":" << maxFps.load(std::memory_order_relaxed)
        << ",\"geometry\":\"" << esc(activeGeometry) << "\""
        << ",\"edges\":" << activeEdges
        << ",\"triangles\":" << activeTriangles
        << ",\"solidMode\":" << (solidMode.load(std::memory_order_relaxed)?"true":"false")
        << ",\"textureMode\":\"" << esc(activeTextureMode) << "\""
        << ",\"normalMapApplied\":" << ((selectedNormalAtlas &&
            (activeTextureMode=="full"||activeTextureMode=="normal-safe") &&
            useNormalMap.load(std::memory_order_relaxed))?"true":"false")
        << ",\"normalAtlasPresent\":" << (selectedNormalAtlas?"true":"false")
        << ",\"message\":\"" << esc(stage) << "\"}";
    return out.str();
}
} // namespace

extern "C" JNIEXPORT void JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeStopScene(JNIEnv*, jclass) {
    stopRequested.store(true, std::memory_order_release);
}
extern "C" JNIEXPORT void JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeResetScene(JNIEnv*, jclass) {
    stopRequested.store(false, std::memory_order_release);
    std::lock_guard<std::mutex> guard(statsMutex);
    stage = "Ожидание Vulkan pipeline…";
    gpuName.clear();
    activeTextureMode = "off";
    renderedFrames = 0;
    measuredFps = 0;
    frameWidth = frameHeight = 0;
    rendering = failed = false;
}
extern "C" JNIEXPORT jstring JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeGetStats(JNIEnv* env, jclass) {
    const std::string data = statsJson();
    return env->NewStringUTF(data.c_str());
}
extern "C" JNIEXPORT void JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeRunScene(JNIEnv* env, jclass,
                                                                    jobject surface, jobject assetManager) {
    try {
        run(env, surface, assetManager);
        {
            std::lock_guard<std::mutex> guard(statsMutex);
            rendering = false;
            if (stopRequested.load()) stage = "Тест остановлен";
        }
    } catch (const std::exception& e) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Vulkan scene failure: %s", e.what());
        setStats(e.what(), true);
        std::lock_guard<std::mutex> guard(statsMutex);
        rendering = false;
    } catch (...) {
        setStats("Unknown native renderer failure", true);
        std::lock_guard<std::mutex> guard(statsMutex);
        rendering = false;
    }
}

// UI controls do not touch Vulkan objects. Atomics are read on the render thread.
extern "C" JNIEXPORT void JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetView(JNIEnv*, jclass,
    jfloat yaw, jfloat pitch, jfloat distance, jboolean moving) {
    if (!std::isfinite(yaw) || !std::isfinite(pitch) || !std::isfinite(distance)) return;
    cameraYaw.store(std::clamp(static_cast<float>(yaw), -1000.0f, 1000.0f), std::memory_order_relaxed);
    cameraPitch.store(std::clamp(static_cast<float>(pitch), -1.3f, 1.3f), std::memory_order_relaxed);
    cameraDistance.store(std::clamp(static_cast<float>(distance), 2.7f, 40.0f), std::memory_order_relaxed);
    autoRotate.store(moving == JNI_TRUE, std::memory_order_relaxed);
}
extern "C" JNIEXPORT void JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetFpsCap(JNIEnv*, jclass, jint cap) {
    if (cap == 0 || cap == 30 || cap == 60) maxFps.store(cap, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetViewportOffset(JNIEnv*, jclass, jfloat offset) {
    if (!std::isfinite(offset)) return;
    cameraViewportOffset.store(std::clamp(static_cast<float>(offset), -0.82f, 0.82f), std::memory_order_relaxed);
}

// Geometry is never treated as executable code. The renderer accepts only bounded xyz lines.
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetGeometry(JNIEnv* env,jclass,jfloatArray values,jstring label){
    if(!values||!label)return JNI_FALSE;
    jsize len=env->GetArrayLength(values);
    if(len<6||len%6!=0||len>30000*6)return JNI_FALSE;
    std::vector<float> geometry(static_cast<size_t>(len));
    env->GetFloatArrayRegion(values,0,len,geometry.data());
    if(env->ExceptionCheck())return JNI_FALSE;
    for(float p:geometry) if(!std::isfinite(p)||std::abs(p)>1.5f)return JNI_FALSE;
    const char* name=env->GetStringUTFChars(label,nullptr);
    if(!name)return JNI_FALSE;
    std::string safe(name);
    env->ReleaseStringUTFChars(label,name);
    if(safe.size()>120)safe.resize(120);
    {
        std::lock_guard<std::mutex> guard(geometryMutex);
        selectedGeometry=std::move(geometry);
        selectedTriangles.clear();
        selectedUv.clear();selectedPixels.clear();
        selectedMaterialSlots.clear();selectedDiffuseAtlasGrid=0;
        selectedTextureWidth=selectedTextureHeight=0;
        selectedNormalAtlas=false;
        useTexture.store(false,std::memory_order_relaxed);
        useNormalMap.store(false,std::memory_order_relaxed);
        geometryTriangleCount=0;
        solidMode.store(false,std::memory_order_relaxed);
        geometryLabel=std::move(safe);
        geometryEdgeCount=static_cast<uint32_t>(len/6);
    }
    return JNI_TRUE;
}
extern "C" JNIEXPORT void JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeClearGeometry(JNIEnv*,jclass){
    std::lock_guard<std::mutex> guard(geometryMutex);
    selectedGeometry.clear();
    selectedTriangles.clear();geometryTriangleCount=0;
    selectedUv.clear();selectedPixels.clear();
        selectedMaterialSlots.clear();selectedDiffuseAtlasGrid=0;
    selectedTextureWidth=selectedTextureHeight=0;
    selectedNormalAtlas=false;
    useTexture.store(false,std::memory_order_relaxed);
    useNormalMap.store(false,std::memory_order_relaxed);
    solidMode.store(false,std::memory_order_relaxed);
    geometryLabel="Куб (встроенный)";geometryEdgeCount=12;
}

// Model input comes from the validated, bounded BSTriShape decoder. The native
// bridge checks it again independently, allowing no NaN, infinities or excess faces.
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetSurfaceTriangles(JNIEnv* env,jclass,jfloatArray values){
    if(!values)return JNI_FALSE;
    jsize len=env->GetArrayLength(values);
    if(len<9 || len%9!=0 || len>30000*9)return JNI_FALSE;
    std::vector<float> geometry(static_cast<size_t>(len));
    env->GetFloatArrayRegion(values,0,len,geometry.data());
    if(env->ExceptionCheck())return JNI_FALSE;
    for(float value:geometry)
        if(!std::isfinite(value)||std::abs(value)>1.5f)return JNI_FALSE;
    std::lock_guard<std::mutex> guard(geometryMutex);
    if(selectedGeometry.empty())return JNI_FALSE;
    selectedTriangles=std::move(geometry);
    selectedUv.clear();selectedPixels.clear();
        selectedMaterialSlots.clear();selectedDiffuseAtlasGrid=0;
    selectedTextureWidth=selectedTextureHeight=0;
    selectedNormalAtlas=false;
    useTexture.store(false,std::memory_order_relaxed);
    useNormalMap.store(false,std::memory_order_relaxed);
    geometryTriangleCount=static_cast<uint32_t>(len/9);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetSolidMode(JNIEnv*,jclass,jboolean enabled){
    if(enabled==JNI_TRUE){
        std::lock_guard<std::mutex> guard(geometryMutex);
        if(selectedTriangles.empty())return JNI_FALSE;
    }
    solidMode.store(enabled==JNI_TRUE,std::memory_order_relaxed);
    return JNI_TRUE;
}


// Upload a bounded UV-per-triangle payload and CPU-decoded DDS first mip.
// The JNI bridge owns no Android Bitmap and writes no user files.
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetTexture(
    JNIEnv* env,jclass,jfloatArray coordinates,jintArray image,jint width,jint height){
    if(!coordinates||!image||width<1||height<1||width>2048||height>2048)return JNI_FALSE;
    const int64_t pixels=static_cast<int64_t>(width)*height;
    if(pixels>2048LL*2048LL||env->GetArrayLength(image)!=pixels)return JNI_FALSE;
    jsize len=env->GetArrayLength(coordinates);
    if(len<6||len>30000*3*2||len%6!=0)return JNI_FALSE;
    std::vector<float> uv(static_cast<size_t>(len));
    env->GetFloatArrayRegion(coordinates,0,len,uv.data());
    if(env->ExceptionCheck())return JNI_FALSE;
    for(float v:uv)if(!std::isfinite(v)||std::abs(v)>8192.f)return JNI_FALSE;
    std::vector<jint> input(static_cast<size_t>(pixels));
    env->GetIntArrayRegion(image,0,static_cast<jsize>(pixels),input.data());
    if(env->ExceptionCheck())return JNI_FALSE;
    std::vector<uint8_t> rgba(static_cast<size_t>(pixels)*4);
    for(int64_t i=0;i<pixels;i++){
        uint32_t color=static_cast<uint32_t>(input[static_cast<size_t>(i)]);
        rgba[i*4]=static_cast<uint8_t>((color>>16)&255);
        rgba[i*4+1]=static_cast<uint8_t>((color>>8)&255);
        rgba[i*4+2]=static_cast<uint8_t>(color&255);
        rgba[i*4+3]=static_cast<uint8_t>((color>>24)&255);
    }
    std::lock_guard<std::mutex> guard(geometryMutex);
    if(selectedTriangles.empty()||uv.size()!=(selectedTriangles.size()/3)*2)return JNI_FALSE;
    selectedUv=std::move(uv);
    selectedPixels=std::move(rgba);
    selectedTextureWidth=static_cast<uint32_t>(width);
    selectedTextureHeight=static_cast<uint32_t>(height);
    selectedNormalAtlas=false;
    selectedMaterialSlots.clear();selectedDiffuseAtlasGrid=0;
    useNormalMap.store(false,std::memory_order_relaxed);
    return JNI_TRUE;
}

// v2.13: one bounded, already CPU-decoded RGBA atlas for all NIF parts.
// Existing nativeSetTexture and the single DDS normal-atlas path remain unchanged.
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetDiffuseAtlas(
    JNIEnv* env,jclass,jfloatArray coordinates,jfloatArray materialIds,
    jintArray image,jint width,jint height,jint grid){
    if(!coordinates||!materialIds||!image||grid<1||grid>4||
       width<1||height<1||width>2048||height>2048||
       width%grid!=0||height%grid!=0)return JNI_FALSE;
    int64_t count=static_cast<int64_t>(width)*height;
    if(count>2048LL*2048LL||env->GetArrayLength(image)!=count)return JNI_FALSE;
    jsize uvLen=env->GetArrayLength(coordinates);
    jsize idLen=env->GetArrayLength(materialIds);
    if(uvLen<6||uvLen>30000*3*2||uvLen%6!=0||
       idLen!=uvLen/2)return JNI_FALSE;
    std::vector<float> uv(static_cast<size_t>(uvLen));
    std::vector<float> ids(static_cast<size_t>(idLen));
    env->GetFloatArrayRegion(coordinates,0,uvLen,uv.data());
    env->GetFloatArrayRegion(materialIds,0,idLen,ids.data());
    if(env->ExceptionCheck())return JNI_FALSE;
    for(float v:uv)if(!std::isfinite(v)||std::abs(v)>8192.0f)return JNI_FALSE;
    for(float id:ids)
        if(!std::isfinite(id)||id<0||id>=grid*grid||std::floor(id)!=id)
            return JNI_FALSE;
    std::vector<jint> input(static_cast<size_t>(count));
    env->GetIntArrayRegion(image,0,static_cast<jsize>(count),input.data());
    if(env->ExceptionCheck())return JNI_FALSE;
    std::vector<uint8_t> rgba(static_cast<size_t>(count)*4);
    for(int64_t i=0;i<count;i++){
        uint32_t pixel=static_cast<uint32_t>(input[static_cast<size_t>(i)]);
        rgba[i*4]=static_cast<uint8_t>((pixel>>16)&255);
        rgba[i*4+1]=static_cast<uint8_t>((pixel>>8)&255);
        rgba[i*4+2]=static_cast<uint8_t>(pixel&255);
        rgba[i*4+3]=static_cast<uint8_t>((pixel>>24)&255);
    }
    std::lock_guard<std::mutex> guard(geometryMutex);
    if(selectedTriangles.empty()||idLen!=static_cast<jsize>(selectedTriangles.size()/3))
        return JNI_FALSE;
    selectedUv=std::move(uv);
    selectedMaterialSlots=std::move(ids);
    selectedPixels=std::move(rgba);
    selectedTextureWidth=static_cast<uint32_t>(width);
    selectedTextureHeight=static_cast<uint32_t>(height);
    selectedNormalAtlas=false;
    selectedDiffuseAtlasGrid=grid;
    useNormalMap.store(false,std::memory_order_relaxed);
    return JNI_TRUE;
}

// v2.18: 2048-bounded single-sampler stacked material atlas.
// The top half has diffuse slots, and bottom half has matching normal slots.
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetDiffuseNormalAtlas(
    JNIEnv* env,jclass cls,jfloatArray coordinates,jfloatArray materialIds,
    jintArray combined,jint width,jint height,jint grid){
    if(height<2 || (height&1)!=0 || height>2048)return JNI_FALSE;
    jboolean accepted=Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetDiffuseAtlas(
        env,cls,coordinates,materialIds,combined,width,height,grid);
    if(accepted!=JNI_TRUE)return JNI_FALSE;
    std::lock_guard<std::mutex> lock(geometryMutex);
    if(selectedDiffuseAtlasGrid!=grid||selectedTextureWidth!=(uint32_t)width||
       selectedTextureHeight!=(uint32_t)height)return JNI_FALSE;
    selectedNormalAtlas=true;
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetUseTexture(
    JNIEnv*,jclass,jboolean enabled){
    useTexture.store(enabled==JNI_TRUE,std::memory_order_relaxed);
}


// v2.2: Single sampled RGBA8 atlas; avoids a second Vulkan descriptor and
// preserves the proven v2.1.x image upload path. This path supports normal
// maps up to 2048x1024, with original diffuse in top half, normals below.
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetNormalTexture(
    JNIEnv* env,jclass,jintArray image,jint width,jint height){
    if(!image||width<1||width>2048||height<1||height>1024)return JNI_FALSE;
    int64_t pixels=static_cast<int64_t>(width)*height;
    if(env->GetArrayLength(image)!=pixels)return JNI_FALSE;
    std::vector<jint> input(static_cast<size_t>(pixels));
    env->GetIntArrayRegion(image,0,static_cast<jsize>(pixels),input.data());
    if(env->ExceptionCheck())return JNI_FALSE;
    std::vector<uint8_t> rgba(static_cast<size_t>(pixels)*4);
    for(int64_t i=0;i<pixels;i++){
        uint32_t c=static_cast<uint32_t>(input[static_cast<size_t>(i)]);
        rgba[i*4] =static_cast<uint8_t>((c>>16)&255);
        rgba[i*4+1]=static_cast<uint8_t>((c>>8)&255);
        rgba[i*4+2]=static_cast<uint8_t>(c&255);
        rgba[i*4+3]=static_cast<uint8_t>((c>>24)&255);
    }
    std::lock_guard<std::mutex> guard(geometryMutex);
    if(selectedNormalAtlas||selectedDiffuseAtlasGrid!=0||selectedPixels.empty()||
       selectedTextureWidth!=static_cast<uint32_t>(width)||
       selectedTextureHeight!=static_cast<uint32_t>(height)||
       selectedPixels.size()!=rgba.size())return JNI_FALSE;
    selectedPixels.insert(selectedPixels.end(),rgba.begin(),rgba.end());
    selectedTextureHeight=static_cast<uint32_t>(height*2);
    selectedNormalAtlas=true;
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetUseNormalMap(
    JNIEnv*,jclass,jboolean enabled){
    useNormalMap.store(enabled==JNI_TRUE,std::memory_order_relaxed);
}


// v2.3 controls update atomically while rendering. No swapchain restart,
// no asset reload, and values are constrained independently of the UI.
extern "C" JNIEXPORT void JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetLightMode(
    JNIEnv*,jclass,jint mode){
    previewLightMode.store((mode>=0 && mode<=3)?static_cast<int>(mode):0,
                           std::memory_order_relaxed);
}
extern "C" JNIEXPORT void JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetNormalStrength(
    JNIEnv*,jclass,jfloat strength){
    const float value=std::isfinite(strength)
        ?std::max(0.5f,std::min(1.5f,static_cast<float>(strength))):1.0f;
    previewReliefStrength.store(value,std::memory_order_relaxed);
}


// v2.4: atomic specular preview controls. No texture reload or Vulkan rebuild.
// 0.0f is the default and matches the user's proven v2.3 render exactly.
extern "C" JNIEXPORT void JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetSpecularStrength(
    JNIEnv*,jclass,jfloat strength){
    float safe=std::isfinite(strength)
        ?std::max(0.0f,std::min(1.0f,static_cast<float>(strength))):0.0f;
    previewSpecularStrength.store(safe,std::memory_order_relaxed);
}
extern "C" JNIEXPORT void JNICALL
Java_dev_skyrimsemobile_launcher_VulkanSceneActivity_nativeSetSpecularSize(
    JNIEnv*,jclass,jint size){
    previewSpecularSize.store((size>=0 && size<=2)?static_cast<int>(size):1,
                              std::memory_order_relaxed);
}

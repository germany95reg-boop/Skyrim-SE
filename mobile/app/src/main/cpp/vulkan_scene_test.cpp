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
    VkImage depthImage = VK_NULL_HANDLE;
    VkDeviceMemory depthMemory = VK_NULL_HANDLE;
    VkImageView depthView = VK_NULL_HANDLE;
    VkFormat depthFormat = VK_FORMAT_UNDEFINED;
    VkShaderModule vertexModule = VK_NULL_HANDLE, fragmentModule = VK_NULL_HANDLE;
    VkShaderModule solidFragmentModule = VK_NULL_HANDLE;
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
            if (solidPipeline) vkDestroyPipeline(device, solidPipeline, nullptr);
            if (pipeline) vkDestroyPipeline(device, pipeline, nullptr);
            if (layout) vkDestroyPipelineLayout(device, layout, nullptr);
            if (vertexModule) vkDestroyShaderModule(device, vertexModule, nullptr);
            if (fragmentModule) vkDestroyShaderModule(device, fragmentModule, nullptr);
            if (solidFragmentModule) vkDestroyShaderModule(device, solidFragmentModule, nullptr);
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
        std::vector<float> triangleData;
        {
            std::lock_guard<std::mutex> lock(geometryMutex);
            vertexData = selectedGeometry.empty() ? defaultCube() : selectedGeometry;
            triangleData = selectedTriangles;
            geometryEdgeCount = static_cast<uint32_t>(vertexData.size()/6);
        }
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
        VkCommandPoolCreateInfo poolInfo{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
        poolInfo.queueFamilyIndex = family;
        poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
        ck(vkCreateCommandPool(device, &poolInfo, nullptr, &pool), "vkCreateCommandPool");
        VkCommandBufferAllocateInfo alloc{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
        alloc.commandPool = pool;
        alloc.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        alloc.commandBufferCount = 1;
        ck(vkAllocateCommandBuffers(device, &alloc, &cmd), "vkAllocateCommandBuffers");
        VkSemaphoreCreateInfo semaphore{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
        ck(vkCreateSemaphore(device, &semaphore, nullptr, &acquireSem), "acquire semaphore");
        ck(vkCreateSemaphore(device, &semaphore, nullptr, &presentSem), "present semaphore");
        {
            std::lock_guard<std::mutex> guard(statsMutex);
            gpuName = gpu;
            frameWidth = size.width;
            frameHeight = size.height;
            stage = "3D-сцена работает";
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
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, filled ? solidPipeline : pipeline);
        struct Parameters {float angle, aspect, width, height, pitch, distance, focusOffsetY;};
        static_assert(sizeof(Parameters) == 28, "Push constant layout must match GLSL");
        Parameters p{angle, static_cast<float>(size.width)/static_cast<float>(size.height),
                     static_cast<float>(size.width), static_cast<float>(size.height), pitch, distance,
                     cameraViewportOffset.load(std::memory_order_relaxed)};
        vkCmdPushConstants(cmd, layout, VK_SHADER_STAGE_VERTEX_BIT, 0, sizeof(p), &p);
        VkDeviceSize offset=0;
        VkBuffer chosenBuffer=filled ? triangleBuffer : vertexBuffer;
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

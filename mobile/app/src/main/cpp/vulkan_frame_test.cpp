// Skyrim SE Mobile Launcher v0.3: one real Vulkan swapchain frame.
// This is NOT an implementation of the Skyrim renderer and runs NO game code.
#define VK_USE_PLATFORM_ANDROID_KHR 1
#include <jni.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <vulkan/vulkan.h>
#include <algorithm>
#include <cstdint>
#include <cstring>
#include <exception>
#include <memory>
#include <mutex>
#include <map>
#include <atomic>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
constexpr const char* TAG = "SkyrimVulkanFrameTest";
void check(VkResult r, const char* operation) {
    if (r != VK_SUCCESS) {
        throw std::runtime_error(std::string(operation) + " failed, VkResult=" + std::to_string(r));
    }
}
std::string escape(const std::string& s) {
    std::string out;
    for (unsigned char c : s) {
        if (c == '\\' || c == '"') { out += '\\'; out += static_cast<char>(c); }
        else if (c == '\n') out += "\\n";
        else if (c >= 32 && c < 127) out += static_cast<char>(c);
        else if (c >= 128) out += static_cast<char>(c); // UTF-8 preserved
    }
    return out;
}
std::string resultJson(bool ok, const std::string& detail, uint32_t width=0,
                       uint32_t height=0, const std::string& gpu="", uint64_t session=0) {
    std::ostringstream out;
    out << "{\"success\":" << (ok ? "true" : "false")
        << ",\"message\":\"" << escape(detail) << "\""
        << ",\"width\":" << width << ",\"height\":" << height
        << ",\"gpu\":\"" << escape(gpu) << "\""
        << ",\"session\":" << session << "}";
    return out.str();
}

// All Vulkan/ANativeWindow handles have one owner, including on an error path.
struct FrameContext {
    ANativeWindow* window = nullptr;
    VkInstance instance = VK_NULL_HANDLE;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkPhysicalDevice physical = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkQueue queue = VK_NULL_HANDLE;
    uint32_t queueFamily = UINT32_MAX;
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    VkCommandPool pool = VK_NULL_HANDLE;
    VkSemaphore imageAvailable = VK_NULL_HANDLE;
    VkSemaphore frameFinished = VK_NULL_HANDLE;
    VkExtent2D extent{};
    std::string gpu;

    ~FrameContext() {
        if (device != VK_NULL_HANDLE) {
            vkDeviceWaitIdle(device);
            if (frameFinished != VK_NULL_HANDLE) vkDestroySemaphore(device, frameFinished, nullptr);
            if (imageAvailable != VK_NULL_HANDLE) vkDestroySemaphore(device, imageAvailable, nullptr);
            if (pool != VK_NULL_HANDLE) vkDestroyCommandPool(device, pool, nullptr);
            if (swapchain != VK_NULL_HANDLE) vkDestroySwapchainKHR(device, swapchain, nullptr);
            vkDestroyDevice(device, nullptr);
        }
        if (surface != VK_NULL_HANDLE && instance != VK_NULL_HANDLE)
            vkDestroySurfaceKHR(instance, surface, nullptr);
        if (instance != VK_NULL_HANDLE) vkDestroyInstance(instance, nullptr);
        if (window != nullptr) ANativeWindow_release(window);
    }
    FrameContext(const FrameContext&) = delete;
    FrameContext& operator=(const FrameContext&) = delete;
    FrameContext() = default;
};

std::mutex sessionsMutex;
std::map<uint64_t, std::unique_ptr<FrameContext>> activeFrames;
std::atomic<uint64_t> nextSession{1};

std::string drawOneFrame(JNIEnv* env, jobject javaSurface) {
    auto owner = std::make_unique<FrameContext>();
    FrameContext& r = *owner;
    if (!javaSurface) throw std::runtime_error("Android Surface is null");
    r.window = ANativeWindow_fromSurface(env, javaSurface);
    if (!r.window) throw std::runtime_error("Cannot obtain ANativeWindow from Surface");

    const char* extensions[] = {VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};
    VkApplicationInfo app{};
    app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app.pApplicationName = "Skyrim SE Launcher Vulkan Frame Test";
    app.applicationVersion = VK_MAKE_VERSION(0, 3, 0);
    app.pEngineName = "Independent Vulkan Test (NOT Skyrim)";
    app.apiVersion = VK_API_VERSION_1_0;
    VkInstanceCreateInfo instanceInfo{};
    instanceInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    instanceInfo.pApplicationInfo = &app;
    instanceInfo.enabledExtensionCount = 2;
    instanceInfo.ppEnabledExtensionNames = extensions;
    check(vkCreateInstance(&instanceInfo, nullptr, &r.instance), "vkCreateInstance");

    VkAndroidSurfaceCreateInfoKHR androidSurface{};
    androidSurface.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    androidSurface.window = r.window;
    check(vkCreateAndroidSurfaceKHR(r.instance, &androidSurface, nullptr, &r.surface), "vkCreateAndroidSurfaceKHR");

    uint32_t count = 0;
    check(vkEnumeratePhysicalDevices(r.instance, &count, nullptr), "vkEnumeratePhysicalDevices/count");
    if (!count) throw std::runtime_error("Vulkan physical device not found");
    std::vector<VkPhysicalDevice> devices(count);
    check(vkEnumeratePhysicalDevices(r.instance, &count, devices.data()), "vkEnumeratePhysicalDevices");

    for (uint32_t d=0; d<count && r.physical == VK_NULL_HANDLE; ++d) {
        uint32_t familyCount = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(devices[d], &familyCount, nullptr);
        std::vector<VkQueueFamilyProperties> families(familyCount);
        vkGetPhysicalDeviceQueueFamilyProperties(devices[d], &familyCount, families.data());
        for (uint32_t f=0; f<familyCount; ++f) {
            VkBool32 present = VK_FALSE;
            if (vkGetPhysicalDeviceSurfaceSupportKHR(devices[d], f, r.surface, &present) != VK_SUCCESS) continue;
            if (present && (families[f].queueFlags & VK_QUEUE_GRAPHICS_BIT)) {
                uint32_t extCount = 0;
                if (vkEnumerateDeviceExtensionProperties(devices[d], nullptr, &extCount, nullptr) != VK_SUCCESS) continue;
                std::vector<VkExtensionProperties> ext(extCount);
                if (vkEnumerateDeviceExtensionProperties(devices[d], nullptr, &extCount, ext.data()) != VK_SUCCESS) continue;
                bool swapchainSupport = false;
                for (const auto& item : ext)
                    if (std::strcmp(item.extensionName, VK_KHR_SWAPCHAIN_EXTENSION_NAME) == 0) swapchainSupport = true;
                if (!swapchainSupport) continue;
                r.physical = devices[d]; r.queueFamily = f;
                VkPhysicalDeviceProperties p{};
                vkGetPhysicalDeviceProperties(r.physical, &p);
                r.gpu = p.deviceName;
                break;
            }
        }
    }
    if (r.physical == VK_NULL_HANDLE) throw std::runtime_error("No GPU queue supports both graphics and presentation");

    float priority = 1.0f;
    VkDeviceQueueCreateInfo queueInfo{};
    queueInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    queueInfo.queueFamilyIndex = r.queueFamily;
    queueInfo.queueCount = 1;
    queueInfo.pQueuePriorities = &priority;
    const char* deviceExtensions[] = {VK_KHR_SWAPCHAIN_EXTENSION_NAME};
    VkDeviceCreateInfo deviceInfo{};
    deviceInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    deviceInfo.queueCreateInfoCount = 1;
    deviceInfo.pQueueCreateInfos = &queueInfo;
    deviceInfo.enabledExtensionCount = 1;
    deviceInfo.ppEnabledExtensionNames = deviceExtensions;
    check(vkCreateDevice(r.physical, &deviceInfo, nullptr, &r.device), "vkCreateDevice");
    vkGetDeviceQueue(r.device, r.queueFamily, 0, &r.queue);

    VkSurfaceCapabilitiesKHR caps{};
    check(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(r.physical, r.surface, &caps), "surface capabilities");
    if (!(caps.supportedUsageFlags & VK_IMAGE_USAGE_TRANSFER_DST_BIT))
        throw std::runtime_error("Swapchain images do not support VK_IMAGE_USAGE_TRANSFER_DST_BIT");
    uint32_t formatsCount = 0;
    check(vkGetPhysicalDeviceSurfaceFormatsKHR(r.physical, r.surface, &formatsCount, nullptr), "surface formats count");
    if (!formatsCount) throw std::runtime_error("No surface formats");
    std::vector<VkSurfaceFormatKHR> formats(formatsCount);
    check(vkGetPhysicalDeviceSurfaceFormatsKHR(r.physical, r.surface, &formatsCount, formats.data()), "surface formats");
    VkSurfaceFormatKHR format = formats[0];
    if (format.format == VK_FORMAT_UNDEFINED) {
        format.format = VK_FORMAT_R8G8B8A8_UNORM;
    } else {
        for (auto& f : formats)
            if (f.format == VK_FORMAT_R8G8B8A8_UNORM || f.format == VK_FORMAT_B8G8R8A8_UNORM) {
                format = f; break;
            }
    }

    r.extent = caps.currentExtent;
    if (r.extent.width == UINT32_MAX) {
        const uint32_t w = static_cast<uint32_t>(std::max(1, ANativeWindow_getWidth(r.window)));
        const uint32_t h = static_cast<uint32_t>(std::max(1, ANativeWindow_getHeight(r.window)));
        r.extent.width = std::clamp(w, caps.minImageExtent.width, caps.maxImageExtent.width);
        r.extent.height = std::clamp(h, caps.minImageExtent.height, caps.maxImageExtent.height);
    }
    if (!r.extent.width || !r.extent.height) throw std::runtime_error("Surface size is zero");
    uint32_t imagesWanted = caps.minImageCount + 1;
    if (caps.maxImageCount && imagesWanted > caps.maxImageCount) imagesWanted = caps.maxImageCount;
    VkCompositeAlphaFlagBitsKHR alpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    for (VkCompositeAlphaFlagBitsKHR a : {
            VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR, VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,
            VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR, VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR}) {
        if (caps.supportedCompositeAlpha & a) { alpha = a; break; }
    }
    VkSwapchainCreateInfoKHR swapInfo{};
    swapInfo.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
    swapInfo.surface = r.surface;
    swapInfo.minImageCount = imagesWanted;
    swapInfo.imageFormat = format.format;
    swapInfo.imageColorSpace = format.colorSpace;
    swapInfo.imageExtent = r.extent;
    swapInfo.imageArrayLayers = 1;
    swapInfo.imageUsage = VK_IMAGE_USAGE_TRANSFER_DST_BIT;
    swapInfo.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    swapInfo.preTransform = caps.currentTransform;
    swapInfo.compositeAlpha = alpha;
    swapInfo.presentMode = VK_PRESENT_MODE_FIFO_KHR; // guaranteed by Vulkan WSI
    swapInfo.clipped = VK_TRUE;
    check(vkCreateSwapchainKHR(r.device, &swapInfo, nullptr, &r.swapchain), "vkCreateSwapchainKHR");

    uint32_t imageCount = 0;
    check(vkGetSwapchainImagesKHR(r.device, r.swapchain, &imageCount, nullptr), "swapchain image count");
    if (!imageCount) throw std::runtime_error("Swapchain has no images");
    std::vector<VkImage> images(imageCount);
    check(vkGetSwapchainImagesKHR(r.device, r.swapchain, &imageCount, images.data()), "swapchain images");

    VkCommandPoolCreateInfo poolInfo{};
    poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    poolInfo.queueFamilyIndex = r.queueFamily;
    check(vkCreateCommandPool(r.device, &poolInfo, nullptr, &r.pool), "vkCreateCommandPool");
    VkCommandBufferAllocateInfo bufferInfo{};
    bufferInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    bufferInfo.commandPool = r.pool;
    bufferInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    bufferInfo.commandBufferCount = 1;
    VkCommandBuffer commands = VK_NULL_HANDLE;
    check(vkAllocateCommandBuffers(r.device, &bufferInfo, &commands), "vkAllocateCommandBuffers");

    VkSemaphoreCreateInfo semInfo{};
    semInfo.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
    check(vkCreateSemaphore(r.device, &semInfo, nullptr, &r.imageAvailable), "image available semaphore");
    check(vkCreateSemaphore(r.device, &semInfo, nullptr, &r.frameFinished), "render finished semaphore");
    uint32_t imageIndex = 0;
    VkResult acquire = vkAcquireNextImageKHR(r.device, r.swapchain, 2000000000ULL,
                                               r.imageAvailable, VK_NULL_HANDLE, &imageIndex);
    if (acquire != VK_SUCCESS && acquire != VK_SUBOPTIMAL_KHR)
        check(acquire, "vkAcquireNextImageKHR");
    if (imageIndex >= imageCount) throw std::runtime_error("Invalid swapchain image index");

    VkCommandBufferBeginInfo begin{};
    begin.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    begin.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    check(vkBeginCommandBuffer(commands, &begin), "vkBeginCommandBuffer");
    VkImageMemoryBarrier toTransfer{};
    toTransfer.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    toTransfer.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    toTransfer.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    toTransfer.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    toTransfer.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    toTransfer.image = images[imageIndex];
    toTransfer.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    toTransfer.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    toTransfer.subresourceRange.levelCount = 1;
    toTransfer.subresourceRange.layerCount = 1;
    vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                         0, 0, nullptr, 0, nullptr, 1, &toTransfer);
    // Distinct lavender frame. All pixels are written on the GPU using Vulkan.
    VkClearColorValue purple{};
    purple.float32[0] = 0.38f; purple.float32[1] = 0.24f;
    purple.float32[2] = 0.57f; purple.float32[3] = 1.0f;
    VkImageSubresourceRange wholeImage{};
    wholeImage.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    wholeImage.levelCount = 1;
    wholeImage.layerCount = 1;
    vkCmdClearColorImage(commands, images[imageIndex], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                         &purple, 1, &wholeImage);
    VkImageMemoryBarrier toPresent = toTransfer;
    toPresent.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    toPresent.newLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    toPresent.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    toPresent.dstAccessMask = 0;
    vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                         0, 0, nullptr, 0, nullptr, 1, &toPresent);
    check(vkEndCommandBuffer(commands), "vkEndCommandBuffer");

    VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
    VkSubmitInfo submit{};
    submit.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submit.waitSemaphoreCount = 1;
    submit.pWaitSemaphores = &r.imageAvailable;
    submit.pWaitDstStageMask = &waitStage;
    submit.commandBufferCount = 1;
    submit.pCommandBuffers = &commands;
    submit.signalSemaphoreCount = 1;
    submit.pSignalSemaphores = &r.frameFinished;
    check(vkQueueSubmit(r.queue, 1, &submit, VK_NULL_HANDLE), "vkQueueSubmit");
    VkPresentInfoKHR present{};
    present.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    present.waitSemaphoreCount = 1;
    present.pWaitSemaphores = &r.frameFinished;
    present.swapchainCount = 1;
    present.pSwapchains = &r.swapchain;
    present.pImageIndices = &imageIndex;
    VkResult shown = vkQueuePresentKHR(r.queue, &present);
    if (shown != VK_SUCCESS && shown != VK_SUBOPTIMAL_KHR) check(shown, "vkQueuePresentKHR");
    check(vkDeviceWaitIdle(r.device), "vkDeviceWaitIdle");
    __android_log_print(ANDROID_LOG_INFO, TAG, "Test frame presented %ux%u on %s",
                        r.extent.width, r.extent.height, r.gpu.c_str());
    const uint32_t width = r.extent.width, height = r.extent.height;
    const std::string gpu = r.gpu;
    const uint64_t session = nextSession.fetch_add(1);
    {
        std::lock_guard<std::mutex> lock(sessionsMutex);
        activeFrames.emplace(session, std::move(owner));
    }
    // Keep the swapchain alive until the Activity releases its Surface.
    // Otherwise the visible frame could disappear immediately after present.
    return resultJson(true, "Vulkan frame presented; swapchain stays alive until screen closes",
                      width, height, gpu, session);
}
} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_dev_skyrimsemobile_launcher_VulkanTestActivity_nativeDrawFrame(JNIEnv* env, jclass, jobject surface) {
    try {
        std::string output = drawOneFrame(env, surface);
        return env->NewStringUTF(output.c_str());
    } catch (const std::exception& e) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", e.what());
        std::string output = resultJson(false, e.what());
        return env->NewStringUTF(output.c_str());
    } catch (...) {
        return env->NewStringUTF("{\"success\":false,\"message\":\"Unknown native error\"}");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_skyrimsemobile_launcher_VulkanTestActivity_nativeReleaseFrame(JNIEnv*, jclass, jlong token) {
    if (token <= 0) return;
    std::unique_ptr<FrameContext> released;
    {
        std::lock_guard<std::mutex> lock(sessionsMutex);
        auto it = activeFrames.find(static_cast<uint64_t>(token));
        if (it != activeFrames.end()) {
            released = std::move(it->second);
            activeFrames.erase(it);
        }
    }
    // Destroy Vulkan objects without holding the session registry mutex.
}

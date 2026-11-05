#include <jni.h>
#include <vulkan/vulkan.h>
#include <algorithm>
#include <sstream>
#include <string>
#include <vector>

// Read-only Vulkan device enumeration. This does not draw frames or run Skyrim.
namespace {
std::string escapeJson(const char *value) {
    std::string out;
    for (const unsigned char *p = reinterpret_cast<const unsigned char *>(value); *p; ++p) {
        if (*p == '\\' || *p == '"') { out.push_back('\\'); out.push_back(static_cast<char>(*p)); }
        else if (*p >= 0x20) out.push_back(static_cast<char>(*p));
    }
    return out;
}
std::string version(uint32_t v) {
    return std::to_string(VK_VERSION_MAJOR(v)) + "." +
        std::to_string(VK_VERSION_MINOR(v)) + "." + std::to_string(VK_VERSION_PATCH(v));
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_skyrimsemobile_launcher_RuntimeDiagnostics_nativeProbeVulkan(JNIEnv *env, jclass) {
    std::ostringstream o;
    uint32_t loaderVersion = VK_API_VERSION_1_0;
    auto enumVersion = reinterpret_cast<PFN_vkEnumerateInstanceVersion>(
        vkGetInstanceProcAddr(VK_NULL_HANDLE, "vkEnumerateInstanceVersion"));
    if (enumVersion) {
        VkResult r = enumVersion(&loaderVersion);
        if (r != VK_SUCCESS) loaderVersion = VK_API_VERSION_1_0;
    }
    o << "{\"vulkanLoader\":true,\"loaderVersion\":\"" << version(loaderVersion) << "\"";
    VkApplicationInfo application{};
    application.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    application.pApplicationName = "Skyrim SE Launcher Device Check";
    application.applicationVersion = VK_MAKE_VERSION(0, 2, 0);
    application.pEngineName = "None - diagnostic only";
    application.engineVersion = 0;
    application.apiVersion = VK_API_VERSION_1_0;
    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &application;
    VkInstance instance = VK_NULL_HANDLE;
    const VkResult created = vkCreateInstance(&createInfo, nullptr, &instance);
    if (created != VK_SUCCESS || instance == VK_NULL_HANDLE) {
        o << ",\"instanceCreated\":false,\"gpuCount\":0,\"vulkan11\":false,\"message\":\""
          << "Vulkan instance creation failed (VkResult " << static_cast<int>(created) << ")\"}";
        return env->NewStringUTF(o.str().c_str());
    }
    uint32_t count = 0;
    VkResult enumerated = vkEnumeratePhysicalDevices(instance, &count, nullptr);
    std::vector<VkPhysicalDevice> devices;
    if (enumerated == VK_SUCCESS && count > 0) {
        devices.resize(count);
        enumerated = vkEnumeratePhysicalDevices(instance, &count, devices.data());
    }
    bool vulkan11 = false;
    std::string selectedName;
    std::string selectedVersion;
    if (enumerated == VK_SUCCESS) {
        count = std::min<uint32_t>(count, static_cast<uint32_t>(devices.size()));
        for (uint32_t i = 0; i < count; ++i) {
            VkPhysicalDeviceProperties props{};
            vkGetPhysicalDeviceProperties(devices[i], &props);
            if (i == 0 || (!vulkan11 && VK_VERSION_MAJOR(props.apiVersion) >= 1 && VK_VERSION_MINOR(props.apiVersion) >= 1)) {
                selectedName = props.deviceName;
                selectedVersion = version(props.apiVersion);
            }
            if (VK_VERSION_MAJOR(props.apiVersion) > 1 ||
                (VK_VERSION_MAJOR(props.apiVersion) == 1 && VK_VERSION_MINOR(props.apiVersion) >= 1)) vulkan11 = true;
        }
    } else count = 0;
    vkDestroyInstance(instance, nullptr);
    o << ",\"instanceCreated\":true,\"gpuCount\":" << count
      << ",\"gpuName\":\"" << escapeJson(selectedName.c_str()) << "\""
      << ",\"apiVersion\":\"" << selectedVersion << "\""
      << ",\"vulkan11\":" << (vulkan11 ? "true" : "false")
      << ",\"message\":\"" << (vulkan11 ? "Vulkan 1.1+ detected; renderer not tested" : "Vulkan 1.1 GPU not detected")
      << "\"}";
    return env->NewStringUTF(o.str().c_str());
}

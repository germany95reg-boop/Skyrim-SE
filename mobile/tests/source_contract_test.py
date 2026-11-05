"""Static checks only: do not confuse with an Android/Vulkan runtime test."""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
main=(root/'app/src/main/java/dev/skyrimsemobile/launcher/MainActivity.java').read_text()
activity=(root/'app/src/main/java/dev/skyrimsemobile/launcher/VulkanTestActivity.java').read_text()
cpp=(root/'app/src/main/cpp/vulkan_frame_test.cpp').read_text()
cmake=(root/'app/src/main/cpp/CMakeLists.txt').read_text()
ui=(root/'app/src/main/assets/index.html').read_text()
preview=(root/'launcher_preview.html').read_text()
assert ui == preview, 'Android asset and preview must match'
assert 'android:name=".VulkanTestActivity"' in manifest
assert 'android:screenOrientation="unspecified"' in manifest
assert 'engineConnected", false' in main
assert 'openVulkanTest()' in main and 'VulkanTestActivity.class' in main
assert 'vkCreateAndroidSurfaceKHR' in cpp
assert 'vkCreateSwapchainKHR' in cpp
assert 'vkCmdClearColorImage' in cpp
assert 'vkQueuePresentKHR' in cpp
assert 'vkDestroySwapchainKHR' in cpp
assert 'nativeReleaseFrame' in activity and 'nativeReleaseFrame' in cpp
assert 'target_link_libraries(skyrimse_renderer' in cmake
assert 'Тест Vulkan-кадра' in ui
assert 'document.getElementById(\'play\').disabled=!' in ui
print('PASS: source contracts, 13 checks (static only)')

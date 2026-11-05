cmake_minimum_required(VERSION 3.22.1)
project(skyrimse_native_tests LANGUAGES CXX)

find_library(vulkan_library vulkan REQUIRED)
find_library(log_library log REQUIRED)
find_library(android_library android REQUIRED)

add_library(skyrimse_probe SHARED skyrimse_probe.cpp)
target_compile_features(skyrimse_probe PRIVATE cxx_std_17)
target_compile_options(skyrimse_probe PRIVATE -Wall -Wextra)
target_link_libraries(skyrimse_probe PRIVATE ${vulkan_library} ${log_library})

# Separate test harness intentionally has no loader, recompiler or Skyrim executable.
add_library(skyrimse_renderer SHARED vulkan_frame_test.cpp)
target_compile_features(skyrimse_renderer PRIVATE cxx_std_17)
target_compile_options(skyrimse_renderer PRIVATE -Wall -Wextra)
target_link_libraries(skyrimse_renderer PRIVATE ${vulkan_library} ${log_library} ${android_library})

# Continuously animated perspective 3D cube benchmark, independent of game code.
add_library(skyrimse_scene SHARED vulkan_scene_test.cpp)
target_compile_features(skyrimse_scene PRIVATE cxx_std_17)
target_compile_options(skyrimse_scene PRIVATE -Wall -Wextra)
target_link_libraries(skyrimse_scene PRIVATE ${vulkan_library} ${log_library} ${android_library})

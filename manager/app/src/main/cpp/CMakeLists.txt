# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.
cmake_minimum_required(VERSION 3.18.1)

project("kernelsu")

add_library(kernelsu
        SHARED
        jni.c
        ksu.c
        legacy.c
)

target_include_directories(kernelsu PRIVATE .)

# On Windows, Git may check out the uapi symlink as a text file. Include its
# existing target so native builds can still resolve "uapi/ksu.h".
if(NOT IS_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/uapi")
        target_include_directories(kernelsu PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../")
endif()

find_library(log-lib log)

target_link_libraries(
        kernelsu
        ${log-lib}
)

find_package(cxx REQUIRED CONFIG)
link_libraries(cxx::cxx)

add_library(adbroot SHARED adbroot.cc)
target_link_libraries(adbroot ${log-lib})


cmake_minimum_required(VERSION 3.22.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(dbscan STATIC
    # List C/C++ source files with relative paths to this CMakeLists.txt.
    dbscan.cpp)

target_include_directories(dbscan PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

target_include_directories(dbscan PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/nanoflann)


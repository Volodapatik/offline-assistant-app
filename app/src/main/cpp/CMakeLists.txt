cmake_minimum_required(VERSION 3.22.1)
project(llama_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED YES)

set(LLAMA_LOCAL_DIR ${CMAKE_CURRENT_LIST_DIR}/llama)

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SHARED OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

if (EXISTS "${LLAMA_LOCAL_DIR}/CMakeLists.txt")
    set(LLAMA_SOURCE_DIR "${LLAMA_LOCAL_DIR}")
else()
    include(FetchContent)
    set(LLAMA_FETCH_TAG "master" CACHE STRING "llama.cpp git tag")
    FetchContent_Declare(
        llama_cpp
        GIT_REPOSITORY https://github.com/ggerganov/llama.cpp.git
        GIT_TAG ${LLAMA_FETCH_TAG}
    )
    FetchContent_MakeAvailable(llama_cpp)
    set(LLAMA_SOURCE_DIR "${llama_cpp_SOURCE_DIR}")
endif()

add_subdirectory(${LLAMA_SOURCE_DIR} ${CMAKE_BINARY_DIR}/llama)

add_library(llama_jni SHARED
    llama_jni.cpp
)

target_include_directories(llama_jni PRIVATE
    ${LLAMA_SOURCE_DIR}
)

target_compile_options(llama_jni PRIVATE
    -O3
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(llama_jni
    llama
    ${log-lib}
    ${android-lib}
)

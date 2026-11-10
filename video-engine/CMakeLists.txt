cmake_minimum_required(VERSION 3.16)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(CMAKE_CXX_FLAGS "-DANDROID -g")
set(CMAKE_C_FLAGS "-w -std=c11 -DANDROID -D_LARGEFILE_SOURCE=1 -g -Wno-error=implicit-function-declaration")
set(CMAKE_ASM_FLAGS "${CFLAGS} -x assembler-with-cpp")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ffunction-sections -fdata-sections -fvisibility=hidden")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ffunction-sections -fdata-sections -fvisibility=hidden -fvisibility-inlines-hidden")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -Wl,--icf=safe -Wl,-Bsymbolic")

if (${ANDROID_ABI} STREQUAL "armeabi-v7a" OR ${ANDROID_ABI} STREQUAL "arm64-v8a")
    enable_language(ASM)
else()
    enable_language(ASM_NASM)
endif()

set(CAN_COMPILE_SME FALSE CACHE INTERNAL "Disable libyuv SME" FORCE)
add_subdirectory(
        third_party/libyuv
        ${CMAKE_CURRENT_BINARY_DIR}/libyuv
        EXCLUDE_FROM_ALL)

# Abseil
set(ABSL_PROPAGATE_CXX_STD ON)
set(ABSL_BUILD_TESTING OFF)
set(ABSL_ENABLE_INSTALL OFF)
add_subdirectory(
        third_party/absl
        ${CMAKE_CURRENT_BINARY_DIR}/absl
        EXCLUDE_FROM_ALL)

add_library(webrtc_absl INTERFACE)
target_link_libraries(webrtc_absl INTERFACE
        absl::base
        absl::strings
        absl::str_format
        absl::cord
        absl::synchronization
        absl::time
        absl::status
        absl::statusor
        absl::hash
        absl::flat_hash_map
        absl::flat_hash_set
        absl::flags
        absl::flags_parse
        absl::random_random
        absl::random_distributions
        absl::crc32c
        absl::log
        absl::check
)


# --- Riv Post: only the call engine from Telegram's native code ---
add_library(crypto STATIC IMPORTED)
set_target_properties(crypto PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libcrypto.a)
add_library(ssl STATIC IMPORTED)
set_target_properties(ssl PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libssl.a)
add_library(libvpx STATIC IMPORTED)
set_target_properties(libvpx PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libvpx.a)
add_library(libopus STATIC IMPORTED)
set_target_properties(libopus PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libopus.a)
add_library(tde2e STATIC IMPORTED)
set_target_properties(tde2e PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libtde2e.a)
add_library(tdutils STATIC IMPORTED)
set_target_properties(tdutils PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/libtdutils.a)

add_library(tgnet STATIC
        openssl/aes_ige.c
        tgnet/FileLog.cpp)
target_compile_options(tgnet PUBLIC -Wall -frtti -finline-functions -ffast-math -Oz)
target_compile_definitions(tgnet PUBLIC HAVE_PTHREAD)
target_include_directories(tgnet PUBLIC third_party/boringssl/include openssl/include)
target_link_libraries(tgnet crypto)

include(${CMAKE_HOME_DIRECTORY}/voip/CMakeLists.txt)

add_library(rivcalls SHARED rivjni.c)
target_link_options(rivcalls PRIVATE "-Wl,-z,max-page-size=16384")
target_link_options(rivcalls PRIVATE "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/exports.map")
target_link_libraries(rivcalls
        -Wl,--whole-archive rnnoise openh264 voipandroid -Wl,--no-whole-archive
        webrtc_absl
        tgcalls
        wasm
        tgcalls_tp
        tgnet
        tde2e
        tdutils
        libvpx
        libopus
        yuv
        ssl
        crypto
        log
        z
        GLESv2
        EGL
        android
        OpenSLES
        cpufeatures)

include(AndroidNdkModules)
android_ndk_import_module_cpufeatures()

cmake_minimum_required(VERSION 3.15)

project(monumentFinderLibJ LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# FindJNI breaks when JAVA_HOME contains spaces on Windows (e.g. C:\Program Files\Java\...).
# Resolve JAVA_HOME manually and pass -DJAVA_HOME=... from Gradle with forward slashes.
if(DEFINED JAVA_HOME)
    set(_JAVA_HOME "${JAVA_HOME}")
elseif(DEFINED ENV{JAVA_HOME})
    set(_JAVA_HOME "$ENV{JAVA_HOME}")
else()
    find_program(_JAVA_EXE java REQUIRED)
    get_filename_component(_JAVA_BIN "${_JAVA_EXE}" DIRECTORY)
    get_filename_component(_JAVA_HOME "${_JAVA_BIN}" DIRECTORY)
endif()

file(TO_CMAKE_PATH "${_JAVA_HOME}" _JAVA_HOME_CMAKE)

set(JNI_INCLUDE_DIRS "${_JAVA_HOME_CMAKE}/include")
if(WIN32)
    list(APPEND JNI_INCLUDE_DIRS "${_JAVA_HOME_CMAKE}/include/win32")
elseif(APPLE)
    list(APPEND JNI_INCLUDE_DIRS "${_JAVA_HOME_CMAKE}/include/darwin")
else()
    list(APPEND JNI_INCLUDE_DIRS "${_JAVA_HOME_CMAKE}/include/linux")
endif()

find_program(Java_JAVAC_EXECUTABLE javac
        HINTS "${_JAVA_HOME_CMAKE}/bin"
        REQUIRED
)

set(PROJECT_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/..)
set(JAVA_BRIDGE_SRC ${PROJECT_ROOT}/src/main/java/project/MonumentFinderBridge.java)
set(JNI_GEN_DIR ${CMAKE_CURRENT_SOURCE_DIR}/generated)
set(JNI_GEN_HEADER ${JNI_GEN_DIR}/project_MonumentFinderBridge.h)

add_custom_command(
        OUTPUT ${JNI_GEN_HEADER}
        COMMAND ${CMAKE_COMMAND} -E make_directory ${JNI_GEN_DIR}
        COMMAND ${Java_JAVAC_EXECUTABLE} -encoding UTF-8 -h ${JNI_GEN_DIR} ${JAVA_BRIDGE_SRC}
        DEPENDS ${JAVA_BRIDGE_SRC}
        COMMENT "Generating JNI header from MonumentFinderBridge.java"
        VERBATIM
)

add_custom_target(generate_jni_header DEPENDS ${JNI_GEN_HEADER})

add_subdirectory(${PROJECT_ROOT}/cubiomes ${CMAKE_BINARY_DIR}/cubiomes)

set(SOURCES
        project_MonumentFinderBridge.cpp
        ../monument_search.cpp
        ../monument_search_core.c
)

add_library(monumentFinderLibJ SHARED ${SOURCES} ${JNI_GEN_HEADER})

add_dependencies(monumentFinderLibJ generate_jni_header)

target_link_libraries(monumentFinderLibJ PRIVATE cubiomes_static)

target_include_directories(monumentFinderLibJ PRIVATE
        ${JNI_INCLUDE_DIRS}
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/..
        ../cubiomes
)

if(MSVC)
    target_compile_options(monumentFinderLibJ PRIVATE /O2 /DNDEBUG)
else()
    target_compile_options(monumentFinderLibJ PRIVATE -O3 -DNDEBUG -ffast-math)
endif()

if(MINGW)
    target_link_options(monumentFinderLibJ PRIVATE
            -static
            -static-libgcc
            -static-libstdc++
    )
endif()

set_target_properties(monumentFinderLibJ PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/native
        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/native
)

if(NOT MSVC)
    find_program(CMAKE_STRIP strip)
    if(CMAKE_STRIP)
        add_custom_command(TARGET monumentFinderLibJ POST_BUILD
                COMMAND ${CMAKE_STRIP} $<TARGET_FILE:monumentFinderLibJ>
        )
    endif()
endif()

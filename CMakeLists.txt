cmake_minimum_required(VERSION 3.20)

# Double monument kernel for All-In-One Finder.
# Builds a small static library; the parent AIO build compiles the same
# sources directly and does not use this file.
project(double_monument_core LANGUAGES C CXX)

if(MSVC)
    message(FATAL_ERROR "MSVC is not supported. Use MSYS2 UCRT64 gcc/g++.")
endif()

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
endif()

# AIO provides cubiomes at tools/cubiomes.  This checkout does not vendor its
# own copy, so point the build at the parent repository's checkout.
set(DOUBLE_MONUMENT_CUBIOMES_DIR "" CACHE PATH "Path to a cubiomes checkout (contains finders.h)")
if(NOT DOUBLE_MONUMENT_CUBIOMES_DIR)
    foreach(candidate
            "${CMAKE_CURRENT_SOURCE_DIR}/cubiomes"
            "${CMAKE_CURRENT_SOURCE_DIR}/../cubiomes"
            "${CMAKE_CURRENT_SOURCE_DIR}/../tools/cubiomes"
            "${CMAKE_CURRENT_SOURCE_DIR}/../../tools/cubiomes")
        if(EXISTS "${candidate}/finders.h")
            get_filename_component(DOUBLE_MONUMENT_CUBIOMES_DIR "${candidate}" ABSOLUTE)
            break()
        endif()
    endforeach()
endif()
if(NOT EXISTS "${DOUBLE_MONUMENT_CUBIOMES_DIR}/finders.h")
    message(FATAL_ERROR
        "cubiomes not found. Pass -DDOUBLE_MONUMENT_CUBIOMES_DIR=<path to tools/cubiomes>.")
endif()
get_filename_component(DOUBLE_MONUMENT_CUBIOMES_PARENT "${DOUBLE_MONUMENT_CUBIOMES_DIR}" DIRECTORY)
message(STATUS "double monument: cubiomes = ${DOUBLE_MONUMENT_CUBIOMES_DIR}")

set(THREADS_PREFER_PTHREAD_FLAG ON)
find_package(Threads REQUIRED)

add_library(double_monument_core STATIC
    monument_search.cpp
    monument_search_core.c
    monument_search.h
    monument_search_core.h
    Thread.h
)

target_include_directories(double_monument_core
    PUBLIC  "${CMAKE_CURRENT_SOURCE_DIR}"
    PRIVATE "${DOUBLE_MONUMENT_CUBIOMES_DIR}"
            "${DOUBLE_MONUMENT_CUBIOMES_PARENT}"
)
target_link_libraries(double_monument_core PUBLIC Threads::Threads)
target_compile_options(double_monument_core PRIVATE -O3 -DNDEBUG -fwrapv)

option(DOUBLE_MONUMENT_BUILD_SELFTEST "Build the standalone selftest executable" OFF)
if(DOUBLE_MONUMENT_BUILD_SELFTEST)
    # Only the cubiomes files needed by the monument search.  tests.c is
    # deliberately excluded (it defines main).
    set(CUBIOMES_ROOT_SOURCES
        finders.c carver.c generator.c layers.c biomenoise.c biomes.c noise.c
        terrainnoise.c util.c quadbase.c xradv.c
    )
    set(CUBIOMES_SOURCES "")
    foreach(src ${CUBIOMES_ROOT_SOURCES})
        list(APPEND CUBIOMES_SOURCES "${DOUBLE_MONUMENT_CUBIOMES_DIR}/${src}")
    endforeach()
    file(GLOB CUBIOMES_FEATURE_SOURCES "${DOUBLE_MONUMENT_CUBIOMES_DIR}/features/*.c")
    list(APPEND CUBIOMES_SOURCES ${CUBIOMES_FEATURE_SOURCES})

    add_executable(double_monument_selftest test/selftest.cpp ${CUBIOMES_SOURCES})
    target_include_directories(double_monument_selftest PRIVATE
        "${DOUBLE_MONUMENT_CUBIOMES_DIR}"
        "${DOUBLE_MONUMENT_CUBIOMES_PARENT}"
    )
    target_link_libraries(double_monument_selftest PRIVATE double_monument_core)
    target_compile_options(double_monument_selftest PRIVATE -O3 -DNDEBUG -fwrapv)
endif()

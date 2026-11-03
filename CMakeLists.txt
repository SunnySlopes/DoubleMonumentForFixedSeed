cmake_minimum_required(VERSION 3.20)

project(DoubleMonumentForFixedSeed LANGUAGES C CXX)

# ---------------------------------------------------------------------------
# DoubleMonumentForFixedSeed
#
# All C/C++ search logic and cubiomes live in the DoubleMonumentFinderBase git
# submodule. This project only builds:
#   - the base static library + standalone CLI (subdirectory),
#   - the thin JNI shim (jni/) that forwards to the base C API.
#
# Initialize the submodule first:
#   git submodule update --init --recursive
# ---------------------------------------------------------------------------

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(DMF_BASE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/DoubleMonumentFinderBase"
    CACHE PATH "Path to the DoubleMonumentFinderBase checkout")

if(NOT EXISTS "${DMF_BASE_DIR}/CMakeLists.txt" OR
   NOT EXISTS "${DMF_BASE_DIR}/include/doublemonumentfinder/base.h")
  message(FATAL_ERROR
    "DoubleMonumentFinderBase submodule is missing: ${DMF_BASE_DIR}\n"
    "Run 'git submodule update --init --recursive', or pass "
    "-DDMF_BASE_DIR=<path-to-DoubleMonumentFinderBase>.")
endif()
if(NOT EXISTS "${DMF_BASE_DIR}/cubiomes/generator.c")
  message(FATAL_ERROR
    "DoubleMonumentFinderBase/cubiomes is empty.\n"
    "Run 'git -C \"${DMF_BASE_DIR}\" submodule update --init --recursive'.")
endif()

add_subdirectory("${DMF_BASE_DIR}" "${CMAKE_BINARY_DIR}/DoubleMonumentFinderBase")

add_subdirectory(jni)

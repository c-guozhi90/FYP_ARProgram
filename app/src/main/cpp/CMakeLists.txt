#this cmakefile is to build a native library that can read font bitmap using freetype

cmake_minimum_required(VERSION 3.6.0)

#add freetype library
add_library(freetype STATIC IMPORTED)

set(freetype_distribution_path ${CMAKE_CURRENT_SOURCE_DIR}/../../../../distribution)

set_target_properties(freetype PROPERTIES
                        IMPORTED_LOCATION
                        ${freetype_distribution_path}/${ANDROID_ABI}/libfreetype.a)

add_library( # Specifies the name of the library.
             readFont

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             readFont.cpp )

find_library( # Defines the name of the path variable that stores the
        # location of the NDK library.
        log-lib

        # Specifies the name of the NDK library that
        # CMake needs to locate.
        log )

target_include_directories(readFont
                            PUBLIC
                            ${freetype_distribution_path}/include)

#link against freetype library
target_link_libraries(readFont freetype ${log-lib})

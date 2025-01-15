if(TARGET cld2)
    return()
endif()


# Collect all source files
set(CLD2_SOURCES
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/cldutil.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/cldutil_shared.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/compact_lang_det.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/compact_lang_det_hint_code.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/compact_lang_det_impl.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/debug.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/fixunicodevalue.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/generated_entities.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/generated_language.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/generated_ulscript.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/getonescriptspan.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/lang_script.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/offsetmap.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/scoreonescriptspan.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/tote.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/utf8statetable.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/cld_generated_cjk_uni_prop_80.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/cld2_generated_cjk_compatible.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/cld_generated_cjk_delta_bi_4.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/generated_distinct_bi_0.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/cld2_generated_quadchrome_2.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/cld2_generated_deltaoctachrome.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/cld2_generated_distinctoctachrome.cc
    ${CMAKE_SOURCE_DIR}/third_party/cld2/internal/cld_generated_score_quad_octa_2.cc
)

# Create shared library
add_library(cld2 SHARED ${CLD2_SOURCES})

# Set properties for the library
set_target_properties(cld2 PROPERTIES
    POSITION_INDEPENDENT_CODE ON
    SOVERSION 1
    VERSION 1.0.0
)

target_compile_options(cld2 PRIVATE 
    -Wno-narrowing
)

# Add include directories
target_include_directories(cld2
    PUBLIC
        ${CMAKE_SOURCE_DIR}/third_party/cld2
        ${CMAKE_SOURCE_DIR}/third_party/cld2/public
        ${CMAKE_SOURCE_DIR}/third_party/cld2/internal
)

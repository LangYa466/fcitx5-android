cmake_minimum_required(VERSION 3.18)

project(fcitx5-android-plugin-rime VERSION ${VERSION_NAME})

# For reproducible build
add_link_options("LINKER:--hash-style=gnu,--build-id=none")

# prefab dependency
find_package(fcitx5 REQUIRED CONFIG)
get_target_property(FCITX5_CMAKE_MODULES fcitx5::cmake INTERFACE_INCLUDE_DIRECTORIES)
set(CMAKE_MODULE_PATH ${FCITX5_CMAKE_MODULES} ${CMAKE_MODULE_PATH})

find_package(ECM MODULE)
find_package(Fcitx5Core MODULE)
find_package(Fcitx5Module MODULE)

set(Rime_VERSION "1.12.0")
set(Rime_dependencies
        log # glog links to android log
        "${PREBUILT_DIR}/glog/${ANDROID_ABI}/lib/libglog.a"
        "${PREBUILT_DIR}/leveldb/${ANDROID_ABI}/lib/libleveldb.a"
        "${PREBUILT_DIR}/lua/${ANDROID_ABI}/lib/liblua_static.a"
        "${PREBUILT_DIR}/marisa/${ANDROID_ABI}/lib/libmarisa.a"
        "${PREBUILT_DIR}/opencc/${ANDROID_ABI}/lib/libopencc.a"
        "${PREBUILT_DIR}/yaml-cpp/${ANDROID_ABI}/lib/libyaml-cpp.a"
        )

add_library(Rime_static STATIC IMPORTED)
set_target_properties(Rime_static PROPERTIES
        IMPORTED_LOCATION "${PREBUILT_DIR}/librime/${ANDROID_ABI}/lib/librime.a"
        INTERFACE_INCLUDE_DIRECTORIES "${PREBUILT_DIR}/librime/${ANDROID_ABI}/include"
        INTERFACE_LINK_LIBRARIES "${Rime_dependencies}"
        )

# https://github.com/rime/librime/blob/1.9.0/src/rime_api.h#L663
# https://stackoverflow.com/questions/805555/ld-linker-question-the-whole-archive-option
# https://cmake.org/cmake/help/latest/manual/cmake-generator-expressions.7.html#link-features
set(RIME_TARGET $<LINK_LIBRARY:WHOLE_ARCHIVE,Rime_static>)
set(RIME_DATA_DIR "${CMAKE_INSTALL_DATADIR}/rime-data")
add_subdirectory(fcitx5-rime)

install(FILES
        "default.yaml"
        "rime-essay/essay.txt"
        "rime-stroke/stroke.dict.yaml"
        "rime-stroke/stroke.schema.yaml"
        "rime-ice/rime_ice.schema.yaml"
        "rime-ice/rime_ice.dict.yaml"
        "rime-ice/melt_eng.schema.yaml"
        "rime-ice/melt_eng.dict.yaml"
        "rime-ice/radical_pinyin.schema.yaml"
        "rime-ice/radical_pinyin.dict.yaml"
        "rime-ice/symbols_v.yaml"
        "rime-ice/symbols_caps_v.yaml"
        "rime-ice/custom_phrase.txt"
        DESTINATION "${FCITX_INSTALL_DATADIR}/rime-data"
        COMPONENT prebuilt-assets
        )

install(FILES
        "rime-ice/cn_dicts/41448.dict.yaml"
        "rime-ice/cn_dicts/8105.dict.yaml"
        "rime-ice/cn_dicts/base.dict.yaml"
        "rime-ice/cn_dicts/ext.dict.yaml"
        "rime-ice/cn_dicts/others.dict.yaml"
        "rime-ice/cn_dicts/tencent.dict.yaml"
        DESTINATION "${FCITX_INSTALL_DATADIR}/rime-data/cn_dicts"
        COMPONENT prebuilt-assets
        )

install(FILES
        "rime-ice/en_dicts/en.dict.yaml"
        "rime-ice/en_dicts/en_ext.dict.yaml"
        "rime-ice/en_dicts/cn_en.txt"
        "rime-ice/en_dicts/cn_en_abc.txt"
        "rime-ice/en_dicts/cn_en_double_pinyin.txt"
        "rime-ice/en_dicts/cn_en_flypy.txt"
        "rime-ice/en_dicts/cn_en_jiajia.txt"
        "rime-ice/en_dicts/cn_en_mspy.txt"
        "rime-ice/en_dicts/cn_en_sogou.txt"
        "rime-ice/en_dicts/cn_en_ziguang.txt"
        DESTINATION "${FCITX_INSTALL_DATADIR}/rime-data/en_dicts"
        COMPONENT prebuilt-assets
        )

install(FILES
        "rime-ice/lua/autocap_filter.lua"
        "rime-ice/lua/calc_translator.lua"
        "rime-ice/lua/cn_en_spacer.lua"
        "rime-ice/lua/convert_ar_num_to_zh.lua"
        "rime-ice/lua/corrector.lua"
        "rime-ice/lua/date_translator.lua"
        "rime-ice/lua/debuger.lua"
        "rime-ice/lua/en_spacer.lua"
        "rime-ice/lua/force_gc.lua"
        "rime-ice/lua/is_in_user_dict.lua"
        "rime-ice/lua/long_word_filter.lua"
        "rime-ice/lua/lunar.db"
        "rime-ice/lua/lunar.lua"
        "rime-ice/lua/number_translator.lua"
        "rime-ice/lua/pin_cand_filter.lua"
        "rime-ice/lua/reduce_english_filter.lua"
        "rime-ice/lua/search.lua"
        "rime-ice/lua/select_character.lua"
        "rime-ice/lua/t9_preedit.lua"
        "rime-ice/lua/unicode.lua"
        "rime-ice/lua/uuid.lua"
        "rime-ice/lua/v_filter.lua"
        DESTINATION "${FCITX_INSTALL_DATADIR}/rime-data/lua"
        COMPONENT prebuilt-assets
        )

install(FILES
        "rime-ice/opencc/emoji.json"
        "rime-ice/opencc/emoji.txt"
        "rime-ice/opencc/others.txt"
        DESTINATION "${FCITX_INSTALL_DATADIR}/opencc"
        COMPONENT prebuilt-assets
        )

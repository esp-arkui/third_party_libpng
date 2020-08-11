#!/bin/bash
# Copyright (c) Huawei Technologies Co., Ltd. 2020. All rights reserved.

set -e
OUT_DIR=$1
ROOT_DIR=$(dirname "$0")

git checkout -- configure
git apply huawei_libpng_patch1.patch

./configure CPPFLAGS="-I../zlib" LDFLAGS="-L../../out/ipcamera_hi3516dv300_liteos_a" CC=$ROOT_DIR/../../prebuilts/gcc/linux-x86/arm/arm-linux-harmonyeabi-gcc/bin/arm-linux-ohoseabi-gcc --host=arm-linux 

make clean
make -j
$ROOT_DIR/../../prebuilts/gcc/linux-x86/arm/arm-linux-harmonyeabi-gcc/arm-linux-ohoseabi/bin/strip $ROOT_DIR/.libs/libpng16.so
cp $ROOT_DIR/.libs/libpng16.so $OUT_DIR/libpng.so

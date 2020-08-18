#!/bin/bash
# Copyright (c) Huawei Technologies Co., Ltd. 2020. All rights reserved.

set -e
OUT_DIR=$1
ROOT_DIR=$(dirname "$0")

git checkout -- configure
git apply huawei_libpng_patch1.patch

./configure CPPFLAGS="-I../zlib" LDFLAGS="-L$OUT_DIR" CC=$ROOT_DIR/../../prebuilts/gcc/linux-x86/arm/arm-linux-ohoseabi-gcc/bin/arm-linux-ohoseabi-gcc --host=arm-linux CFLAGS='-fstack-protector-strong -Wl,-z,relro,-z,now'

make clean
make -j
$ROOT_DIR/../../prebuilts/gcc/linux-x86/arm/arm-linux-ohoseabi-gcc/arm-linux-ohoseabi/bin/strip $ROOT_DIR/.libs/libpng16.so
cp $ROOT_DIR/.libs/libpng16.so $OUT_DIR/libpng.so

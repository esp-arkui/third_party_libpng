cmake_minimum_required(VERSION 3.16.5)
set(LibpngSrc "${PROJECT_SOURCE_DIR}/third_party/libpng")
set(LibpngInc "${PROJECT_SOURCE_DIR}/third_party/libpng")
include_directories(${LibpngInc})
message(${LibpngSrc})

add_library(libpng STATIC
  ${LibpngSrc}/png.c
  ${LibpngSrc}/pngerror.c
  ${LibpngSrc}/pngget.c
  ${LibpngSrc}/pngmem.c
  ${LibpngSrc}/pngpread.c
  ${LibpngSrc}/pngread.c
  ${LibpngSrc}/pngrio.c
  ${LibpngSrc}/pngrtran.c
  ${LibpngSrc}/pngrutil.c
  ${LibpngSrc}/pngset.c
  ${LibpngSrc}/pngtrans.c
  ${LibpngSrc}/pngwio.c
  ${LibpngSrc}/pngwrite.c
  ${LibpngSrc}/pngwtran.c
  ${LibpngSrc}/pngwutil.c
)

target_link_libraries(libpng PUBLIC zlib)
target_include_directories(libpng PUBLIC ${LibpngInc})
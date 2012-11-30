#ifndef JOPENJPEG2_H
#define JOPENJPEG2_H

#ifdef __cplusplus
extern "C" {
#endif

#include "opj_config.h"
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <math.h>
#include "openjpeg.h"

#ifdef WIN32
#define JOPENJPEG2_EXPORT __declspec(dllexport)
#else
#define JOPENJPEG2_EXPORT 
#endif

JOPENJPEG2_EXPORT struct jopj_SImgInfo
{
	int width;
	int height;
	int num_components;
	int num_resolutions_max;
	int num_layers;
	int num_tiles;
	int num_x_tiles;
	int num_y_tiles;
	int tile_x_offset;
	int tile_y_offset;
	int tile_width;
	int tile_height;
	// Taken from first component
	int prec;
	int factor;
	int resno_decoded;
	int sgnd;
	int bpp;
	int x0;
	int y0;
	int dx;
	int dy;
	int w;
	int h;
};
typedef struct jopj_SImgInfo jopj_ImgInfo;

JOPENJPEG2_EXPORT struct jopj_SImg;
typedef struct jopj_SImg jopj_Img;

JOPENJPEG2_EXPORT int jopj_read_img_region_data(const char* filepath, int resolution, int comp_index, int x0, int y0, int width, int height, short data[]);

JOPENJPEG2_EXPORT jopj_Img* jopj_open_img(const char* filepath, int resolution);
JOPENJPEG2_EXPORT int jopj_read_img_tile_data(jopj_Img* img, int comp_index, int tile_index, short data[]);
JOPENJPEG2_EXPORT void jopj_dispose_img(jopj_Img* img);

JOPENJPEG2_EXPORT jopj_ImgInfo* jopj_get_img_info(jopj_Img* img);
JOPENJPEG2_EXPORT void jopj_dispose_img_info(jopj_ImgInfo* img_info);

#ifdef __cplusplus
}
#endif

#endif /* JOPENJPEG2_H */
#include "jopenjpeg2.h"

JOPENJPEG2_EXPORT struct jopj_SImg
{
	FILE*                      fsrc;
	opj_dparameters_t          parameters;
	opj_image_t*               image;
	opj_stream_t*              l_stream;
	opj_codec_t*               l_codec; 
	opj_codestream_info_v2_t*  cstr_info;
};


static void error_callback(const char *msg, void* client_data)
{
	fprintf(stdout, "[ERROR] %s", msg);
}

static void warning_callback(const char *msg, void* client_data) 
{
	fprintf(stdout, "[WARNING] %s", msg);
}

static void info_callback(const char *msg, void* client_data) 
{
	fprintf(stdout, "[INFO] %s", msg);
}


JOPENJPEG2_EXPORT jopj_Img* jopj_open_img(const char* filepath, int resolution)
{
	jopj_Img* img;

	img = (jopj_Img*) calloc(1, sizeof (jopj_Img));
	if (img == NULL) {
		fprintf(stderr, "error: out of memory\n");
		return NULL;
	}
 
	opj_set_default_decoder_parameters(&img->parameters);
	img->parameters.cp_reduce = resolution;

	img->fsrc = fopen(filepath, "rb");
	if (img->fsrc == NULL) {
		jopj_dispose_img(img);
		fprintf(stderr, "error: failed to open %s for reading\n", filepath);
		return NULL;
	}

	img->l_stream = opj_stream_create_default_file_stream(img->fsrc, 1);
	if (img->l_stream == NULL) {
		jopj_dispose_img(img);
		fprintf(stderr, "error: failed to create the stream from the file\n");
		return NULL;
	}

	img->l_codec = opj_create_decompress(OPJ_CODEC_JP2);
	if (img->l_codec == NULL) {
		jopj_dispose_img(img);
		fprintf(stderr, "error: failed to create the decompressor\n");
		return NULL;
	}

	opj_set_info_handler(img->l_codec, info_callback, NULL);
	opj_set_warning_handler(img->l_codec, warning_callback, NULL);
	opj_set_error_handler(img->l_codec, error_callback, NULL);

	if (!opj_setup_decoder(img->l_codec, &img->parameters)) {
		jopj_dispose_img(img);
		fprintf(stderr, "error: failed to setup the decoder\n");
		return NULL;
	}

	if (!opj_read_header(img->l_stream, img->l_codec, &img->image)) {
		jopj_dispose_img(img);
		fprintf(stderr, "error: failed to read the header\n");
		return NULL;
	}

	if (!opj_set_decoded_resolution_factor(img->l_codec, resolution)) {
		jopj_dispose_img(img);
		fprintf(stderr, "error: failed to set resolution factor\n");
	}

	img->cstr_info = opj_get_cstr_info(img->l_codec);

	return img;
}

void jopj_copy_data(opj_image_t* image, int comp_index, int x0, int y0, int dw, int dh, short* data)
{
	opj_image_comp_t* comp;
	int nbytes;
	int cx0, cy0, cw, ch, n;
	int i, j;

	comp = &image->comps[comp_index];
	cx0 = comp->x0;
	cy0 = comp->y0;
	cw = comp->w;
	ch = comp->h;

	if (comp->prec <= 8) {
		nbytes = 1;
	} else if (comp->prec <= 16) {
		nbytes = 2;
	} else {
		nbytes = 4;
	}

	n = cw * ch;
	
	fprintf(stderr, "info: copying %d pixels, factor=%d, from cx0=%d, cy0=%d, cw=%d, ch=%d, #bytes/sample=%d to tile tw=%d, th=%d\n", n, comp->factor, cx0, cy0, cw, ch, nbytes, dw, dh);

	for (j = 0; j < dh; j++) {
		for (i = 0; i < dw; i++) {
			if (i < cw && j < ch) {
				data[j * dw + i] = (short) comp->data[j * cw + i];
			} else {
				data[j * dw + i] = 0;
			}
		}
	}

}


JOPENJPEG2_EXPORT int jopj_read_img_region_data(const char* filepath, int resolution, int comp_index, int x0, int y0, int w, int h, short data[])
{
	jopj_Img* img;
	opj_image_t* image;
	int x1, y1;

	img = jopj_open_img(filepath, resolution);
	if (img == NULL) {
		return 0;
	}

	image = img->image;
	x1 = x0 + w - 1;
	y1 = y0 + h - 1;

	if (comp_index < 0 || comp_index >= (int) image->numcomps) {
		fprintf(stderr, "error: invalid component index %d\n", comp_index);
		return 0;
	}
 
	if (!opj_set_decode_area(img->l_codec, image, x0, y0, x1, y1)) {
		fprintf(stderr,	"error: failed to set the decoded region x0=%d, y0=%d, x1=%d, y1=%d\n", x0, y0, x1, y1);
		jopj_dispose_img(img);
		return 0;
	}

	if (!opj_decode(img->l_codec, img->l_stream, image)) {
		fprintf(stderr,	"error: failed to decode region x0=%d, y0=%d, x1=%d, y1=%d\n", x0, y0, x1, y1);
		jopj_dispose_img(img);
		return 0;
	}

	jopj_copy_data(image, comp_index, x0, y0, w, h, data);
	
	if (!opj_end_decompress(img->l_codec, img->l_stream)) {
		fprintf(stderr, "warning: failed to end decompression\n");
	}

	jopj_dispose_img(img);

	return 1;
}


JOPENJPEG2_EXPORT int jopj_read_img_tile_data(jopj_Img* img, int comp_index, int tile_index, short data[])
{
	opj_image_t* image;
	opj_image_comp_t* comp;
	int tx0, ty0;
	int tw, th;

	image = img->image;

	if (comp_index < 0 || comp_index >= (int) image->numcomps) {
		fprintf(stderr, "error: invalid component index %d\n", comp_index);
		return 0;
	}

	if (!opj_get_decoded_tile(img->l_codec, img->l_stream, image, tile_index)) {
		fprintf(stderr, "error: failed to decode tile %d\n", tile_index);
		return 0;
	}

	comp = &image->comps[comp_index];
	tx0 = comp->x0;
	ty0 = comp->y0;
	tw = img->cstr_info->tdx >> comp->factor;
	th = img->cstr_info->tdy >> comp->factor;

	jopj_copy_data(image, comp_index, tx0, ty0, tw, th, data);

	/* I don't know if the following code is save, but for sure, we don't need the memory anymore (nf) */
	if (comp->data != NULL) {
		free(comp->data);
		comp->data = NULL;
	}

	return 1;
}


JOPENJPEG2_EXPORT void jopj_dispose_img(jopj_Img* img) 
{
	if (img == NULL) {
		return;
	}
	if (img->l_stream != NULL) {
		opj_stream_destroy(img->l_stream);
		img->l_stream = NULL;
	}
	if (img->fsrc != NULL) {
		fclose(img->fsrc);
		img->fsrc = NULL;
	}
	if (img->cstr_info != NULL) {
		opj_destroy_cstr_info(&img->cstr_info);
		img->cstr_info = NULL;
	}
	if (img->l_codec != NULL) {
		opj_destroy_codec(img->l_codec);
		img->l_codec = NULL;
	}
	if (img->image != NULL) {
		opj_image_destroy(img->image);
		img->image = NULL;
	}
	free(img);
}

JOPENJPEG2_EXPORT jopj_ImgInfo* jopj_get_img_info(jopj_Img* img)
{
	int i, res;
	jopj_ImgInfo* img_info;
	
	img_info = (jopj_ImgInfo*) calloc(1, sizeof (jopj_ImgInfo));
	if (img_info == NULL) {
		return NULL;
	}

	img_info->width = img->image->x1 - img->image->x0; 
	img_info->height = img->image->y1 - img->image->y0;
	img_info->num_components = img->image->numcomps;

	if (img->cstr_info != NULL) {
		img_info->num_tiles = img->cstr_info->tw * img->cstr_info->th;
		img_info->num_x_tiles = img->cstr_info->tw;
		img_info->num_y_tiles = img->cstr_info->th;
		img_info->tile_x_offset = img->cstr_info->tx0;
		img_info->tile_y_offset = img->cstr_info->ty0;
		img_info->tile_width = img->cstr_info->tdx;
		img_info->tile_height = img->cstr_info->tdy;
		img_info->num_layers = img->cstr_info->m_default_tile_info.numlayers;

		for (i = 0; i < (int) img->image->numcomps; i++) {
			res = img->cstr_info->m_default_tile_info.tccp_info[i].numresolutions;
			if (img_info->num_resolutions_max < res) {
				img_info->num_resolutions_max = res;
			}
		}
	}

	if (img->image->comps != NULL) {
		img_info->factor = img->image->comps[0].factor;
		img_info->prec = img->image->comps[0].prec;
		img_info->resno_decoded = img->image->comps[0].resno_decoded;
		img_info->sgnd = img->image->comps[0].sgnd;
		img_info->bpp = img->image->comps[0].bpp;
		img_info->x0 = img->image->comps[0].x0;
		img_info->y0 = img->image->comps[0].y0;
		img_info->dx = img->image->comps[0].dx;
		img_info->dy = img->image->comps[0].dy;
		img_info->w = img->image->comps[0].w;
		img_info->h = img->image->comps[0].h;
	}

	return img_info;
}

JOPENJPEG2_EXPORT void jopj_dispose_img_info(jopj_ImgInfo* img_info)
{
	if (img_info == NULL) {
		return;
	}
	free(img_info);
}
package jopenjpeg2;

import jopenjpeg2.jna.Jopenjpeg2Library;
import jopenjpeg2.jna.jopj_Img;
import jopenjpeg2.jna.jopj_ImgInfo;

import java.io.File;
import java.io.IOException;

/**
 * @author Norman Fomferra
 */
public class Jp2Image {

    private static final Jopenjpeg2Library lib = Jopenjpeg2Library.INSTANCE;

    public static final class Layout {
        public final int numResolutions;
        public final int numComponents;
        public final int width;
        public final int height;
        public final int numXTiles;
        public final int numYTiles;
        public final int tileOffsetX;
        public final int tileOffsetY;
        public final int tileWidth;
        public final int tileHeight;

        private Layout(int numResolutions, int numComponents, int width, int height, int numXTiles, int numYTiles, int tileOffsetX, int tileOffsetY, int tileWidth, int tileHeight) {
            this.numResolutions = numResolutions;
            this.numComponents = numComponents;
            this.width = width;
            this.height = height;
            this.numXTiles = numXTiles;
            this.numYTiles = numYTiles;
            this.tileOffsetX = tileOffsetX;
            this.tileOffsetY = tileOffsetY;
            this.tileWidth = tileWidth;
            this.tileHeight = tileHeight;
        }
    }

    private jopj_Img img;
    public final File file;
    public final Layout layout;

    public static Jp2Image open(File file, int resolution) throws IOException {
        jopj_Img img = _open(file, resolution);
        Layout layout = _getLayout(file, img);
        return new Jp2Image(img, file, layout);
    }

    public static Layout getLayout(File file) throws IOException {
        jopj_Img img = _open(file, 0);
        try {
            return _getLayout(file, img);
        } finally {
            lib.jopj_dispose_img(img);
        }
    }

    public synchronized void readTileData(int componentIndex, int tileX, int tileY, short[] tileData) throws IOException {
        final int tileIndex = layout.numYTiles * tileY + tileX;
        if (!lib.jopj_read_img_tile_data(img, componentIndex, tileIndex, tileData)) {
            throw new IOException(String.format("Failed to read tile (%d, %d) from %s", tileX, tileY, file.getName()));
        }
    }

    public synchronized void dispose() {
        if (img != null) {
            lib.jopj_dispose_img(img);
            img = null;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        dispose();
    }

    private static Layout _getLayout(File file, jopj_Img img) throws IOException {
        jopj_ImgInfo imgInfo = lib.jopj_get_img_info(img);
        if (imgInfo == null) {
            throw new IOException("Failed to read image info from " + file.getName());
        }
        return new Layout(imgInfo.num_resolutions_max,
                          imgInfo.num_components,
                          imgInfo.width,
                          imgInfo.height,
                          imgInfo.num_x_tiles,
                          imgInfo.num_y_tiles,
                          imgInfo.tile_x_offset,
                          imgInfo.tile_y_offset,
                          imgInfo.tile_width,
                          imgInfo.tile_height
        );
    }

    private static jopj_Img _open(File file, int resolution) throws IOException {
        jopj_Img img = lib.jopj_open_img(file.getPath(), resolution);
        if (img == null) {
            throw new IOException("Failed to open " + file.getName());
        }
        return img;
    }

    private Jp2Image(jopj_Img img, File file, Layout layout) {
        this.img = img;
        this.file = file;
        this.layout = layout;
    }
}

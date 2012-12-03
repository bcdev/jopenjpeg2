package jopenjpeg2;

import jopenjpeg2.jna.Jopenjpeg2Lib;
import jopenjpeg2.jna.jopj_Img;
import jopenjpeg2.jna.jopj_ImgInfo;

import java.io.File;
import java.io.IOException;

/**
 * @author Norman Fomferra
 */
class Jp2LibImage implements Jp2Image {

    private static final Jopenjpeg2Lib LIB = Jopenjpeg2Lib.INSTANCE;

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public Layout getLayout() {
        return layout;
    }

    private jopj_Img img;
    private final File file;
    private final Layout layout;

    public static Jp2Image open(File file, int resolution) throws IOException {
        jopj_Img img = _open(file, resolution);
        Layout layout = _getLayout(file, img);
        return new Jp2LibImage(img, file, layout);
    }

    public static Layout getLayout(File file) throws IOException {
        jopj_Img img = _open(file, 0);
        try {
            return _getLayout(file, img);
        } finally {
            LIB.jopj_dispose_img(img);
        }
    }

    @Override
    public synchronized void readTileData(int componentIndex, int tileX, int tileY, short[] tileData) throws IOException {
        final int tileIndex = getLayout().numYTiles * tileY + tileX;
        if (!LIB.jopj_read_img_tile_data(img, componentIndex, tileIndex, tileData)) {
            throw new IOException(String.format("Failed to read tile (%d, %d) from %s", tileX, tileY, getFile().getName()));
        }
    }

    @Override
    public synchronized void dispose() {
        if (img != null) {
            LIB.jopj_dispose_img(img);
            img = null;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        dispose();
    }

    private static Layout _getLayout(File file, jopj_Img img) throws IOException {
        jopj_ImgInfo imgInfo = LIB.jopj_get_img_info(img);
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
        jopj_Img img = LIB.jopj_open_img(file.getPath(), resolution);
        if (img == null) {
            throw new IOException("Failed to open " + file.getName());
        }
        return img;
    }

    private Jp2LibImage(jopj_Img img, File file, Layout layout) {
        this.img = img;
        this.file = file;
        this.layout = layout;
    }
}

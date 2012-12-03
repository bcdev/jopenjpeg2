package jopenjpeg2.jna;

import org.junit.Test;

import java.io.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Norman Fomferra
 */
public class Jopenjpeg2LibTest {
    // On Norman's notebook -Dsentinel2.testdataset=C:\Users\Norman\BC\EOData\S2_Simulated_Test_Data
    public static final File F = new File(System.getProperty("sentinel2.testdataset"),
            "IMG_GPPL1C_054_20091210235100_20091210235130_01_000000_15SVD.jp2");

    //@Test
    public void testDecodeRegion() throws Exception {
        Jopenjpeg2Lib lib = Jopenjpeg2Lib.INSTANCE;
        jopj_Img img = lib.jopj_open_img(F.getPath(), 0);
        assertNotNull(img);
        jopj_ImgInfo img_info = lib.jopj_get_img_info(img);
        assertNotNull(img_info);
        int width = img_info.width;
        int height = img_info.height;
        int tile_width = img_info.tile_width;
        int tile_height = img_info.tile_height;
        lib.jopj_dispose_img_info(img_info);
        lib.jopj_dispose_img(img);

        assertEquals(10960, width);
        assertEquals(10960, height);
        assertEquals(4096, tile_width);
        assertEquals(4096, tile_height);

        int w = 512;
        int h = 512;

        short[] tile_data = new short[w * h];

        boolean ok;

        for (int y0 = 0; y0 < height; y0 += h) {
            for (int x0 = 0; x0 < width; x0 += w) {
                final String region = String.format("%d_%d_%d_%d", x0, y0, w, h);

                long t0 = System.currentTimeMillis();
                ok = lib.jopj_read_img_region_data(F.getPath(), 0, 0, x0, y0, w, h, tile_data);
                //ok = lib.jopj_read_img_region_data(img, 0, 0, 0, 0, 0, tile_data);
                if (ok) {
                    long t1 = System.currentTimeMillis();
                    long dt = t1 - t0;
                    System.out.printf("region %s decoded in %d ms (%d pixels)\n", region, dt, w * h);

                    writePgm(String.format("images/region-%s.pgm", region), w, h, tile_data);
                } else {
                    System.out.printf("jopj_read_img_region_data failed for region %s\n", region);
                }
            }
        }

    }

    @Test
    public void testDecodeTile() throws Exception {
        //testDecodeTile(5);
        //testDecodeTile(4);
        //testDecodeTile(3);
        testDecodeTile(2);
        //testDecodeTile(1);
        //testDecodeTile(0);
    }

    private void testDecodeTile(int res) throws IOException {
        Jopenjpeg2Lib lib = Jopenjpeg2Lib.INSTANCE;
        jopj_Img img = lib.jopj_open_img(F.getPath(), res);
        assertNotNull(img);
        jopj_ImgInfo img_info = lib.jopj_get_img_info(img);
        assertNotNull(img_info);

        assertEquals(0, img_info.resno_decoded);
        assertEquals(res, img_info.factor);
        assertEquals(10960, img_info.width);
        assertEquals(10960, img_info.height);
        assertEquals(6, img_info.num_resolutions_max);
        assertEquals(9, img_info.num_tiles);
        assertEquals(3, img_info.num_x_tiles);
        assertEquals(3, img_info.num_y_tiles);
        assertEquals(0, img_info.tile_x_offset);
        assertEquals(0, img_info.tile_y_offset);
        assertEquals(4096, img_info.tile_width);
        assertEquals(4096, img_info.tile_height);

        int w = img_info.tile_width >> res;
        int h = img_info.tile_height >> res;

        short[] tile_data = new short[w * h];

        for (int tileIndex = 0; tileIndex < img_info.num_x_tiles * img_info.num_y_tiles; tileIndex++) {
            System.out.printf("Decoding tile...");
            long t0 = System.currentTimeMillis();
            lib.jopj_read_img_tile_data(img, 0, tileIndex, tile_data);
            long t1 = System.currentTimeMillis();
            long dt = t1 - t0;
            System.out.printf("Tile %d-%d decoded in %d ms (%d pixels)\n", res, tileIndex, dt, w * h);
            //writePgm(String.format("images/tile-%d-%d.pgm", res, tileIndex), w, h, tile_data);
        }

        lib.jopj_dispose_img_info(img_info);
        lib.jopj_dispose_img(img);
    }

    private void writePgm(String name, int w, int h, short[] tile_data) throws IOException {
        OutputStream writer = new BufferedOutputStream(new FileOutputStream(name), 1024 * 1024);
        try {
            writer.write(String.format("P5\n%d %d\n255\n", w, h).getBytes());
            int v;
            for (short s : tile_data) {
                v = s & 0x00FF;
                v = v < 255 ? v : 0;
                writer.write(v);
            }
        } finally {
            writer.close();
        }
    }
}

package org.esa.beam.dataio.sentinel2;

import jopenjpeg2.Jopenjpeg2LibraryTest;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.junit.Test;

import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.FileInputStream;

import static org.junit.Assert.*;

/**
 * @author Norman Fomferra
 */
public class Sentinel2ProductReaderTest {



    @Test
    public void testFilePat() throws Exception {
        //                       "IMG_GPP([A-Z0-9]{3})_(\\d{3})_(\\d{14})_(\\d{14})_(\\d{2})_000000_(\\d{2}[A-Z]{3})\\.jp2";
        final S2FileNameInfo fn = S2FileNameInfo.create("IMG_GPPL1C_054_20091210235100_20091210235130_01_000000_15TVE.jp2");
        assertNotNull(fn);
        assertEquals("L1C",fn.type);
        assertEquals("054",fn.orbit);
        assertEquals("20091210235100", fn.start);
        assertEquals("20091210235130", fn.stop);
        assertEquals("01", fn.band);
        assertEquals("15TVE", fn.utmId);

        assertNull(S2FileNameInfo.create("MTD_GPPL1C_054_20091210235100_20091210235130_0001.xml"));
        assertNull(S2FileNameInfo.create("TBN_GPPL1C_054_20091210235100_20091210235130_000000_15SUD.jpg"));
    }

    @Test
    public void testFileAlreadyOpen() throws Exception {
        final FileInputStream s1 = new FileInputStream("pom.xml");
        final FileInputStream s2 = new FileInputStream("pom.xml");
        final FileInputStream s3 = new FileInputStream("pom.xml");
        s3.close();
        s2.close();
        s1.close();
    }

    @Test
    public void testReader() throws Exception {
        final Sentinel2ProductReaderPlugIn sentinel2ProductReaderPlugIn = new Sentinel2ProductReaderPlugIn();
        final ProductReader readerInstance = sentinel2ProductReaderPlugIn.createReaderInstance();
        final Product product = readerInstance.readProductNodes(Jopenjpeg2LibraryTest.F, null);
        assertNotNull(product);
        assertEquals(10960, product.getSceneRasterWidth());
        assertEquals(10960, product.getSceneRasterHeight());
        final Band band = product.getBand("data");
        assertNotNull(band);

        final int[] pixels = new int[16 * 16];
        band.readPixels(0, 0, 16, 16, pixels);

        final RenderedImage image = band.getSourceImage().getImage(5);
        final Raster data = image.getData();
        assertNotNull(data);

        product.dispose();
    }
}

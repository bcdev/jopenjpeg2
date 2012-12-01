package org.esa.beam.dataio.sentinel2;

import jopenjpeg2.jna.Jopenjpeg2LibraryTest;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.junit.Test;

import java.awt.image.Raster;
import java.awt.image.RenderedImage;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 * @author Norman Fomferra
 */
public class Sentinel2ProductReaderTest {

    @Test
    public void testReader() throws Exception {
        final Sentinel2ProductReaderPlugIn sentinel2ProductReaderPlugIn = new Sentinel2ProductReaderPlugIn();
        final ProductReader readerInstance = sentinel2ProductReaderPlugIn.createReaderInstance();
        final Product product = readerInstance.readProductNodes(Jopenjpeg2LibraryTest.F, null);
        assertNotNull(product);
        assertEquals(10960, product.getSceneRasterWidth());
        assertEquals(10960, product.getSceneRasterHeight());
        assertEquals(6, product.getNumBands());
        final Band band = product.getBand("radiance_1");
        assertNotNull(band);

        final int[] pixels = new int[16 * 16];
        band.readPixels(0, 0, 16, 16, pixels);

        final RenderedImage image = band.getSourceImage().getImage(5);
        final Raster data = image.getData();
        assertNotNull(data);

        product.dispose();
    }
}

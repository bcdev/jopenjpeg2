package org.esa.beam.dataio.sentinel2;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.glevel.MultiLevelModel;
import com.bc.ceres.glevel.support.AbstractMultiLevelSource;
import com.bc.ceres.glevel.support.DefaultMultiLevelImage;
import com.bc.ceres.glevel.support.DefaultMultiLevelModel;
import jopenjpeg2.Jopenjpeg2Library;
import jopenjpeg2.jopj_Img;
import jopenjpeg2.jopj_ImgInfo;
import org.esa.beam.framework.dataio.AbstractProductReader;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.jai.ResolutionLevel;
import org.esa.beam.jai.SingleBandedOpImage;

import javax.media.jai.PlanarImage;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferUShort;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Norman Fomferra
 */
public class Sentinel2ProductReader extends AbstractProductReader {

    public static final Jopenjpeg2Library JP2LIB = Jopenjpeg2Library.INSTANCE;

    Sentinel2ProductReader(Sentinel2ProductReaderPlugIn readerPlugIn) {
        super(readerPlugIn);
    }

    int width;
    int height;
    int tileWidth;
    int tileHeight;
    int numResolutions;

    private static class BandInfo {
        File file;
        int bandIndex;
        int width;
        int height;
        int tileWidth;
        int tileHeight;
        int numResolutions;
    }

    @Override
    protected Product readProductNodesImpl() throws IOException {
        final String s = getInput().toString();

        final File file0 = new File(s);
        final File dir = file0.getParentFile();

        final S2FileNameInfo fni0 = S2FileNameInfo.create(file0.getName());
        if (fni0 == null) {
            throw new IOException();
        }

        final Map<Integer, BandInfo> fileMap = new HashMap<Integer, BandInfo>();
        if (dir != null) {
            File[] files = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(Sentinel2ProductReaderPlugIn.JP2_EXT);
                }
            });
            if (files != null) {
                for (File file : files) {
                    int bandIndex = fni0.getBand(file.getName());
                    if (bandIndex >= 0) {
                        final jopj_Img img = JP2LIB.jopj_open_img(file.getPath(), 0);
                        if (img != null) {
                            final jopj_ImgInfo imgInfo = JP2LIB.jopj_get_img_info(img);
                            if (imgInfo != null) {
                                BandInfo bandInfo = new BandInfo();
                                bandInfo.file = file;
                                bandInfo.bandIndex = bandIndex;
                                bandInfo.width = imgInfo.width;
                                bandInfo.height = imgInfo.height;
                                bandInfo.tileWidth = imgInfo.tile_width;
                                bandInfo.tileHeight = imgInfo.tile_height;
                                bandInfo.numResolutions = imgInfo.num_resolutions_max;
                                JP2LIB.jopj_dispose_img_info(imgInfo);
                                JP2LIB.jopj_dispose_img(img);
                                fileMap.put(bandIndex, bandInfo);
                            } else {
                                JP2LIB.jopj_dispose_img(img);
                            }
                        }
                    }
                }
            }
        }

        final ArrayList<Integer> bandIndexes = new ArrayList<Integer>(fileMap.keySet());
        Collections.sort(bandIndexes);

        final Product product = new Product(dir != null ? dir.getName() : "S2L1C", "S2L1C", width, height);

        try {
            product.setStartTime(ProductData.UTC.parse(fni0.start, "yyyyMMddHHmmss"));
        } catch (ParseException e) {
            // warn
        }

        try {
            product.setEndTime(ProductData.UTC.parse(fni0.stop, "yyyyMMddHHmmss"));
        } catch (ParseException e) {
            // warn
        }

        for (Integer bandIndex : bandIndexes) {
            final BandInfo bandInfo = fileMap.get(bandIndex);
            width = Math.max(width, bandInfo.width);
            height = Math.max(height, bandInfo.height);
            tileWidth = Math.max(tileWidth, bandInfo.tileWidth);
            tileHeight = Math.max(tileHeight, bandInfo.tileHeight);
            numResolutions = Math.max(numResolutions, bandInfo.numResolutions);
            final Band band = product.addBand("radiance_" + bandIndex, ProductData.TYPE_UINT16);
            band.setSpectralBandIndex(bandIndex);
            band.setSourceImage(new DefaultMultiLevelImage(new Jp2MultiLevelSource(bandInfo.file)));
        }

        return product;
    }

    private MultiLevelModel createImageModel() {
        return new DefaultMultiLevelModel(numResolutions, new AffineTransform(), width, height);
    }

    @Override
    protected void readBandRasterDataImpl(int sourceOffsetX, int sourceOffsetY, int sourceWidth, int sourceHeight, int sourceStepX, int sourceStepY, Band destBand, int destOffsetX, int destOffsetY, int destWidth, int destHeight, ProductData destBuffer, ProgressMonitor pm) throws IOException {
        throw new IllegalStateException("Should not come here");
    }

    public Dimension getTileDim(int res) {
        return new Dimension(tileWidth >> res, tileHeight >> res);
    }

    private class Jp2MultiLevelSource extends AbstractMultiLevelSource {
        private final File file;

        public Jp2MultiLevelSource(File file) {
            super(createImageModel());
            this.file = file;
        }

        @Override
        protected RenderedImage createImage(int res) {
            return new Jp2OpImage(file, getModel(), res);
        }
    }

    private class Jp2OpImage extends SingleBandedOpImage {
        private final File file;
        private jopj_Img img;

        public Jp2OpImage(File file, MultiLevelModel imageModel, int res) {
            super(DataBuffer.TYPE_USHORT,
                  Sentinel2ProductReader.this.width,
                  Sentinel2ProductReader.this.height,
                  getTileDim(res),
                  null,
                  ResolutionLevel.create(imageModel, res));
            this.file = file;
        }

        @Override
        protected synchronized void computeRect(PlanarImage[] sources, WritableRaster dest, Rectangle destRect) {
            DataBufferUShort dbs = (DataBufferUShort) dest.getDataBuffer();
            final short[] tileData = dbs.getData();

            if (img == null) {
                img = JP2LIB.jopj_open_img(file.getPath(), getLevel());
                if (img == null) {
                    throw new RuntimeException(String.format("Failed to open image '%s'", file));
                }
            }

            final int tileX = destRect.x / this.getTileWidth();
            final int tileY = destRect.y / this.getTileHeight();
            final int tileIndex = this.getNumXTiles() * tileY + tileX;
            if (!JP2LIB.jopj_read_img_tile_data(img, 0, tileIndex, tileData)) {
                JP2LIB.jopj_dispose_img(img);
                img = null;
                throw new RuntimeException(String.format("Failed to read tile (%d,%d)", tileX, tileY));
            }
        }

        @Override
        public synchronized void dispose() {
            super.dispose();
            if (img != null) {
                JP2LIB.jopj_dispose_img(img);
                img = null;
            }
        }
    }

}

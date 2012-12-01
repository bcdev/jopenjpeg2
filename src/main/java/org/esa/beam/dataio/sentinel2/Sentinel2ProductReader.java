package org.esa.beam.dataio.sentinel2;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.glevel.MultiLevelModel;
import com.bc.ceres.glevel.support.AbstractMultiLevelSource;
import com.bc.ceres.glevel.support.DefaultMultiLevelImage;
import com.bc.ceres.glevel.support.DefaultMultiLevelModel;
import jopenjpeg2.Jp2Image;
import org.esa.beam.framework.dataio.AbstractProductReader;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.jai.ResolutionLevel;
import org.esa.beam.jai.SingleBandedOpImage;
import org.esa.beam.util.logging.BeamLogManager;

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
        Jp2Image.Layout imageLayout;
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
                        try {
                            Jp2Image.Layout imageLayout = Jp2Image.getLayout(file);
                            BandInfo bandInfo = new BandInfo();
                            bandInfo.file = file;
                            bandInfo.bandIndex = bandIndex;
                            bandInfo.imageLayout = imageLayout;
                            fileMap.put(bandIndex, bandInfo);
                        } catch (IOException e) {
                            BeamLogManager.getSystemLogger().warning(e.getMessage());
                        }
                    }
                }
            }
        }

        final ArrayList<Integer> bandIndexes = new ArrayList<Integer>(fileMap.keySet());
        Collections.sort(bandIndexes);

        if (bandIndexes.isEmpty()) {
            throw new IOException("No valid bands found.");
        }

        for (Integer bandIndex : bandIndexes) {
            final BandInfo bandInfo = fileMap.get(bandIndex);
            width = Math.max(width, bandInfo.imageLayout.width);
            height = Math.max(height, bandInfo.imageLayout.height);
            tileWidth = Math.max(tileWidth, bandInfo.imageLayout.tileWidth);
            tileHeight = Math.max(tileHeight, bandInfo.imageLayout.tileHeight);
            numResolutions = Math.max(numResolutions, bandInfo.imageLayout.numResolutions);
        }

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
            final Band band = product.addBand("radiance_" + bandIndex, ProductData.TYPE_UINT16);
            band.setSpectralBandIndex(bandIndex);
            band.setSourceImage(new DefaultMultiLevelImage(new Jp2MultiLevelSource(bandInfo)));
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
        final BandInfo bandInfo;

        public Jp2MultiLevelSource(BandInfo bandInfo) {
            super(createImageModel());
            this.bandInfo = bandInfo;
        }

        @Override
        protected RenderedImage createImage(int res) {
            return new Jp2OpImage(bandInfo, getModel(), res);
        }
    }

    private class Jp2OpImage extends SingleBandedOpImage {
        private final BandInfo bandInfo;
        private Jp2Image jp2Image;

        public Jp2OpImage(BandInfo bandInfo, MultiLevelModel imageModel, int res) {
            super(DataBuffer.TYPE_USHORT,
                  Sentinel2ProductReader.this.width,
                  Sentinel2ProductReader.this.height,
                  getTileDim(res),
                  null,
                  ResolutionLevel.create(imageModel, res));
            this.bandInfo = bandInfo;
        }

        @Override
        protected synchronized void computeRect(PlanarImage[] sources, WritableRaster dest, Rectangle destRect) {
            DataBufferUShort dbs = (DataBufferUShort) dest.getDataBuffer();
            final short[] tileData = dbs.getData();

            if (jp2Image == null) {
                try {
                    jp2Image = Jp2Image.open(bandInfo.file, getLevel());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            final int tileX = destRect.x / this.getTileWidth();
            final int tileY = destRect.y / this.getTileHeight();
            try {
                jp2Image.readTileData(0, tileX, tileY, tileData);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public synchronized void dispose() {
            super.dispose();
            if (jp2Image != null) {
                jp2Image.dispose();
            }
        }
    }

}

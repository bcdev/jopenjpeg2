package org.esa.beam.dataio.sentinel2;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.glevel.MultiLevelModel;
import com.bc.ceres.glevel.support.AbstractMultiLevelSource;
import com.bc.ceres.glevel.support.DefaultMultiLevelImage;
import com.bc.ceres.glevel.support.DefaultMultiLevelModel;
import org.esa.beam.framework.dataio.AbstractProductReader;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.jai.ResolutionLevel;
import org.esa.beam.jai.SingleBandedOpImage;
import org.esa.beam.util.SystemUtils;
import org.esa.beam.util.io.FileUtils;

import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.media.jai.PlanarImage;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferUShort;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;

public class Sentinel2ProductReader2 extends AbstractProductReader {

    /**
     * @author Norman Fomferra
     */
    private static class BandInfo {
        File imageFile;
        int bandIndex;
        S2WavebandInfo wavebandInfo;
    }

    S2WavebandInfo[] WAVEBAND_INFOS = new S2WavebandInfo[]{
            new S2WavebandInfo("1", 443, 20, 60),
            new S2WavebandInfo("2", 490, 65, 10),
            new S2WavebandInfo("3", 560, 35, 10),
            new S2WavebandInfo("4", 665, 30, 10),
            new S2WavebandInfo("5", 705, 15, 20),
            new S2WavebandInfo("6", 740, 15, 20),
            new S2WavebandInfo("7", 775, 20, 20),
            new S2WavebandInfo("8", 842, 115, 10),
            new S2WavebandInfo("8a", 865, 20, 20),
            new S2WavebandInfo("9", 940, 20, 60),
            new S2WavebandInfo("10", 1380, 30, 60),
            new S2WavebandInfo("11", 1610, 90, 20),
            new S2WavebandInfo("12", 2190, 180, 20),
    };

    int width;
    int height;
    int tileWidth;
    int tileHeight;
    int numXTiles;
    int numYTiles;

    int numResolutions;

    Sentinel2ProductReader2(Sentinel2ProductReaderPlugIn readerPlugIn) {
        super(readerPlugIn);
        width = 10960;
        height = 10960;
        tileWidth = 4096;
        tileHeight = 4096;
        numXTiles = 3;
        numYTiles = 3;
        numResolutions = 6;
    }

    @Override
    protected Product readProductNodesImpl() throws IOException {
        final String s = getInput().toString();

        final File file0 = new File(s);
        final File dir = file0.getParentFile();

        final S2FilenameInfoX fni0 = S2FilenameInfoX.create(file0.getName());
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
                        BandInfo bandInfo = new BandInfo();
                        bandInfo.imageFile = file;
                        bandInfo.bandIndex = bandIndex;
                        bandInfo.wavebandInfo = WAVEBAND_INFOS[bandIndex];
                        fileMap.put(bandIndex, bandInfo);
                    }
                }
            }
        }

        final ArrayList<Integer> bandIndexes = new ArrayList<Integer>(fileMap.keySet());
        Collections.sort(bandIndexes);

        if (bandIndexes.isEmpty()) {
            throw new IOException("No valid bands found.");
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
            final Band band = product.addBand("band_" + bandInfo.wavebandInfo.bandName, ProductData.TYPE_UINT16);
            band.setSpectralWavelength((float) bandInfo.wavebandInfo.centralWavelength);
            band.setSpectralBandwidth((float) bandInfo.wavebandInfo.bandWidth);
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

    public Dimension getJp2TileDim(int res) {
        return new Dimension(tileWidth >> res, tileHeight >> res);
    }

    static final int DEFAULT_TILE_SIZE = 512;

    public Dimension getTileDim(int res) {
        Dimension jp2TileDim = getJp2TileDim(res);
        return new Dimension(jp2TileDim.width < DEFAULT_TILE_SIZE ? jp2TileDim.width : DEFAULT_TILE_SIZE,
                             jp2TileDim.height < DEFAULT_TILE_SIZE ? jp2TileDim.height : DEFAULT_TILE_SIZE);
    }

    private class Jp2MultiLevelSource extends AbstractMultiLevelSource {
        final BandInfo bandInfo;

        public Jp2MultiLevelSource(BandInfo bandInfo) {
            super(createImageModel());
            this.bandInfo = bandInfo;
        }

        @Override
        protected RenderedImage createImage(int resolution) {
            try {
                return new Jp2ExeOpImage(bandInfo, getModel(), resolution);
            } catch (IOException e) {
                return null;
            }
        }
    }

    private static final String EXE = System.getProperty("openjpeg2.decompressor.path", "opj_decompress");


    /**
     * Tiled image at a given resolution level.
     */
    class Jp2ExeOpImage extends SingleBandedOpImage {

        private final File imageFile;
        private final File cacheDir;
        private Map<File, ImageInputStream> openFiles;

        Jp2ExeOpImage(BandInfo bandInfo, MultiLevelModel imageModel, int resolution) throws IOException {
            super(DataBuffer.TYPE_USHORT,
                  Sentinel2ProductReader2.this.width,
                  Sentinel2ProductReader2.this.height,
                  getTileDim(resolution),
                  null,
                  ResolutionLevel.create(imageModel, resolution));

            final File resolvedFile = bandInfo.imageFile.getCanonicalFile();
            if (!resolvedFile.exists()) {
                throw new FileNotFoundException("File not found: " + bandInfo.imageFile);
            }

            if (resolvedFile.getParentFile() == null) {
                throw new IOException("Can't determine package directory");
            }

            final File cacheDir = new File(new File(SystemUtils.getApplicationDataDir(), "jopenjpeg/cache"),
                                           resolvedFile.getParentFile().getName());
            cacheDir.mkdirs();
            if (!cacheDir.exists() || !cacheDir.isDirectory() || !cacheDir.canWrite()) {
                throw new IOException("Can't access package cache directory");
            }

            this.imageFile = resolvedFile;
            this.cacheDir = cacheDir;
            this.openFiles = new HashMap<File, ImageInputStream>();
        }


        @Override
        protected synchronized void computeRect(PlanarImage[] sources, WritableRaster dest, Rectangle destRect) {
            final DataBufferUShort dataBuffer = (DataBufferUShort) dest.getDataBuffer();
            final short[] tileData = dataBuffer.getData();

            final int tileX = destRect.x / this.getTileWidth();
            final int tileY = destRect.y / this.getTileHeight();

            final int resolution = getLevel();
            final Dimension jp2TileDim = getJp2TileDim(resolution);
            final int jp2TileX = destRect.x / jp2TileDim.width;
            final int jp2TileY = destRect.y / jp2TileDim.height;

            // 0 - 10960  - 4096
            // 1 -  5480  - 2048
            // 2 -  2740  - 1024
            // 3 -  1370  -  512
            // 4 -   685  -  256
            // 5 -   343  -  128

            final File outputFile = new File(cacheDir,
                                             FileUtils.exchangeExtension(imageFile.getName(),
                                                                         String.format("_R%d_TX%d_TY%d.pgx",
                                                                                       resolution, jp2TileX, jp2TileY)));
            final File outputFile0 = getFirstComponentOutputFile(outputFile);
            if (!outputFile0.exists()) {
                System.out.printf("Jp2ExeImage.readTileData(): recomputing res=%d, tile=(%d,%d)\n", resolution, jp2TileX, jp2TileY);
                try {
                    decompressTile(outputFile, jp2TileX, jp2TileY);
                } catch (IOException e) {
                    // warn
                    outputFile0.delete();
                }
                if (!outputFile0.exists()) {
                    Arrays.fill(tileData, (short) 0);
                    return;
                }
            }

            try {
                System.out.printf("Jp2ExeImage.readTileData(): reading res=%d, tile=(%d,%d)\n", resolution, jp2TileX, jp2TileY);
                readTileData(outputFile0, jp2TileX, jp2TileY, tileData);
            } catch (IOException e) {
                // warn
            }
        }

        private File getFirstComponentOutputFile(File outputFile) {
            return FileUtils.exchangeExtension(outputFile, "_0.pgx");
        }

        private void decompressTile(final File outputFile, int jp2TileX, int jp2TileY) throws IOException {
            final int tileIndex = numXTiles * jp2TileY + jp2TileX;
            final Process process = new ProcessBuilder(EXE,
                                                       "-i", imageFile.getPath(),
                                                       "-o", outputFile.getPath(),
                                                       "-r", getLevel() + "",
                                                       "-t", tileIndex + "").directory(cacheDir).start();

            try {
                final int exitCode = process.waitFor();
                if (exitCode != 0) {
                    System.err.println("Failed to uncompress tile: exitCode = " + exitCode);
                }
            } catch (InterruptedException e) {
                System.err.println("InterruptedException: " + e.getMessage());
            }
        }

        @Override
        public synchronized void dispose() {

            for (ImageInputStream imageInputStream : openFiles.values()) {
                try {
                    imageInputStream.close();
                } catch (IOException e) {
                    // warn
                }
            }

            for (File file : openFiles.keySet()) {
                if (!file.delete()) {
                    // warn
                }
            }

            openFiles.clear();

            if (!cacheDir.delete()) {
                // warn
            }
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            dispose();
        }

        private void readTileData(File outputFile, int jp2TileX, int jp2TileY, short[] tileData) throws IOException {

            final int tileWidth = getTileWidth();
            final int tileHeight = getTileHeight();

            if (tileWidth * tileHeight != tileData.length) {
                throw new IllegalStateException(String.format("tileWidth (=%d) * tileHeight (=%d) != tileData.length (=%d)",
                                                              tileWidth, tileHeight, tileData.length));
            }

            ImageInputStream stream = openFiles.get(outputFile);
            if (stream == null) {
                stream = new FileImageInputStream(outputFile);
                openFiles.put(outputFile, stream);
            }

            String header = stream.readLine();
            final String[] tokens = header.split(" ");
            if (tokens.length != 6) {
                throw new IOException("Unexpected tile format");
            }

            String pg = tokens[0];   // PG
            String ml = tokens[1];   // ML
            String plus = tokens[2]; // +
            int width;
            int height;
            try {
                int nbits = Integer.parseInt(tokens[3]);
                width = Integer.parseInt(tokens[4]);
                height = Integer.parseInt(tokens[5]);
            } catch (NumberFormatException e) {
                throw new IOException("Unexpected tile format");
            }

            if (width > jp2TileX || height > jp2TileY) {
                throw new IllegalStateException(String.format("width (=%d) > tileWidth (=%d) || height (=%d) > tileHeight (=%d)",
                                                              width, jp2TileX, height, jp2TileY));
            }

            if (width * height == tileData.length) {
                stream.readFully(tileData, 0, width * height);
            } else if (width * height < tileData.length) {
                short[] temp = new short[width * height];
                stream.readFully(temp, 0, width * height);
                for (int y = 0; y < tileHeight; y++) {
                    for (int x = 0; x < tileWidth; x++) {
                        if (x < width && y < height) {
                            tileData[y * tileWidth + x] = temp[y * width + x];
                        } else {
                            tileData[y * tileWidth + x] = (short) 0;
                        }
                    }
                }
            } else {
                Arrays.fill(tileData, (short) 0);
            }
        }


    }
}

package jopenjpeg2;

import com.bc.ceres.core.ProcessObserver;
import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.util.SystemUtils;
import org.esa.beam.util.io.FileUtils;

import javax.imageio.stream.FileImageInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Norman Fomferra
 */
class Jp2ExeImage implements Jp2Image {

    private static final String EXE = System.getProperty("openjpeg2.decompressor.path", "opj_decompress");

    private final File file;
    private final File cacheDir;
    private final Layout layout;
    private final int resolution;
    private final Set<File> tilesInProgress;
    private final Set<File> tilesGenerated;

    public static Jp2Image open(File file, int resolution) throws IOException {

        final File resolvedFile = file.getCanonicalFile();
        if (!resolvedFile.exists()) {
            throw new FileNotFoundException("File not found: " + file);
        }

        if (resolvedFile.getParentFile() == null) {
            throw new IOException("Can't determine package directory");
        }

        final File packageCacheDir = new File(new File(SystemUtils.getApplicationDataDir(), "jopenjpeg/cache"),
                                              resolvedFile.getParentFile().getName());
        packageCacheDir.mkdirs();
        if (!packageCacheDir.exists() || !packageCacheDir.isDirectory() || !packageCacheDir.canWrite()) {
            throw new IOException("Can't access package cache directory");
        }

        return new Jp2ExeImage(resolvedFile, packageCacheDir, _getLayout(file), resolution);
    }

    public static Layout getLayout(File file) throws IOException {
        return _getLayout(file);
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public Layout getLayout() {
        return layout;
    }

    @Override
    public void readTileData(final int componentIndex, final int tileX, final int tileY, final short[] tileData) throws IOException {
        final File outputFile = new File(cacheDir, FileUtils.exchangeExtension(file.getName(), String.format("_R%d_TX%d_TY%d.pgx", resolution, tileX, tileY)));
        final File outputFile0 = getFirstComponentOutputFile(outputFile);
        if (!outputFile0.exists()) {
            System.out.printf("Jp2ExeImage.readTileData(): recomputing res=%d, tile=(%d,%d)\n", resolution, tileX, tileY);
            decompressTile(tileX, tileY, outputFile);
        }
        if (outputFile0.exists()) {
            System.out.printf("Jp2ExeImage.readTileData(): reading res=%d, tile=(%d,%d)\n", resolution, tileX, tileY);
            readTileData(outputFile0, tileData);
        } else {
            Arrays.fill(tileData, (short) 0);
        }
    }

    @Override
    public synchronized void dispose() {
        System.out.println(this + ".dispose():");
        // rmdir -r
        for (File tileFile : tilesGenerated) {
            // tileFile.delete();
            System.out.println("Generated tile file: " + tileFile);
        }
        // cacheDir.delete();
        tilesGenerated.clear();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        dispose();
    }

    private void readTileData(File outputFile, short[] tileData) throws IOException {

        final int tileWidth = layout.tileWidth >> resolution;
        final int tileHeight = layout.tileHeight >> resolution;

        if (tileWidth * tileHeight != tileData.length) {
            throw new IllegalStateException(String.format("tileWidth (=%d) * tileHeight (=%d) != tileData.length (=%d)",
                                                          tileWidth, tileHeight, tileData.length));
        }

        final FileImageInputStream fileInputStream = new FileImageInputStream(outputFile);
        try {

            String header = fileInputStream.readLine();
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

            if (width > tileWidth || height > tileHeight) {
                throw new IllegalStateException(String.format("width (=%d) > tileWidth (=%d) || height (=%d) > tileHeight (=%d)",
                                                              width, tileWidth, height, tileHeight));
            }

            if (width * height == tileData.length) {
                fileInputStream.readFully(tileData, 0, width * height);
            } else if (width * height < tileData.length) {
                short[] temp = new short[width * height];
                fileInputStream.readFully(temp, 0, width * height);
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
        } finally {
            fileInputStream.close();
        }
    }

    private File existsPgx(File outputFile) {
        File modFile;
        for (int i = 0; i < 3 * 3; i++) {
            modFile = getFirstComponentOutputFile(outputFile);
            if (modFile.exists()) {
                return modFile;
            }
        }
        return null;
    }

    private File getFirstComponentOutputFile(File outputFile) {
        return FileUtils.exchangeExtension(outputFile, "_0.pgx");
    }

    private void decompressTile(int tileX, int tileY, final File outputFile) throws IOException {
        final int tileIndex = getLayout().numYTiles * tileY + tileX;
        final Process process = new ProcessBuilder(EXE,
                                                   "-i", file.getPath(),
                                                   "-o", outputFile.getPath(),
                                                   "-r", resolution + "",
                                                   "-t", tileIndex + "").directory(cacheDir).start();

        final ProcessObserver.DefaultHandler handler = new ProcessObserver.DefaultHandler() {
            @Override
            public void onObservationStarted(ProcessObserver.ObservedProcess process, ProgressMonitor pm) {
                super.onObservationStarted(process, pm);
                tilesInProgress.add(outputFile);
            }

            @Override
            public void onObservationEnded(ProcessObserver.ObservedProcess process, Integer exitCode, ProgressMonitor pm) {
                super.onObservationEnded(process, exitCode, pm);
                tilesInProgress.remove(outputFile);

                if (exitCode == 0) {
                    final File file1 = existsPgx(outputFile);
                    if (file1 != null) {
                        tilesGenerated.add(file1);
                    } else {
                        System.err.println("File not found: " + outputFile);
                    }
                }

            }
        };
        new ProcessObserver(process)
                .setHandler(handler)
                .setPollPeriod(100)
                .setMode(ProcessObserver.Mode.BLOCKING)
                .start();


    }

    private static Layout _getLayout(File file) throws IOException {
        // todo - parse output of "jp2_dump <file>"
        return new Layout(6,
                          1,
                          10960,
                          10960,
                          3,
                          3,
                          0,
                          0,
                          4096,
                          4096
        );
    }

    private Jp2ExeImage(File file, File cacheDir, Layout layout, int resolution) {
        this.file = file;
        this.cacheDir = cacheDir;
        this.layout = layout;
        this.resolution = resolution;

        tilesInProgress = new HashSet<File>();
        tilesGenerated = new HashSet<File>();
    }
}

package jopenjpeg2;

import java.io.File;
import java.io.IOException;

/**
 * @author Norman Fomferra
 */
public interface Jp2Image {
    File getFile();

    Layout getLayout();

    void readTileData(int componentIndex, int tileX, int tileY, short[] tileData) throws IOException;

    void dispose();

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

        public Layout(int numResolutions, int numComponents, int width, int height, int numXTiles, int numYTiles, int tileOffsetX, int tileOffsetY, int tileWidth, int tileHeight) {
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
}

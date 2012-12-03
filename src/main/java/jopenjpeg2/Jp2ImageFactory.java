package jopenjpeg2;

import java.io.File;
import java.io.IOException;

/**
 * @author Norman Fomferra
 */
public abstract class Jp2ImageFactory {
    public enum Type {
        LIB,
        EXE,
    }
    public abstract Jp2Image open(File file, int resolution) throws IOException;

    public abstract Jp2Image.Layout getLayout(File file) throws IOException;

    public static  Jp2ImageFactory create(Type type) {
        if (type == Type.LIB) {
            return new Jp2ImageFactory() {
                @Override
                public Jp2Image open(File file, int resolution) throws IOException {
                    return Jp2LibImage.open(file, resolution);
                }

                @Override
                public Jp2Image.Layout getLayout(File file) throws IOException {
                    return Jp2LibImage.getLayout(file);
                }
            };
        } else {
            return new Jp2ImageFactory() {
                @Override
                public Jp2Image open(File file, int resolution) throws IOException {
                    return Jp2ExeImage.open(file, resolution);
                }

                @Override
                public Jp2Image.Layout getLayout(File file) throws IOException {
                    return Jp2ExeImage.getLayout(file);
                }
            };
        }
    }
}

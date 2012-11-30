package org.esa.beam.dataio.sentinel2;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Norman Fomferra
 */
public class S2FileNameInfo {

    final static String FORMAT = "IMG_GPP%s_%s_%s_%s_(\\d{2})_000000_%s.jp2";
    final static String REGEX = "IMG_GPP([A-Z0-9]{3})_(\\d{3})_(\\d{14})_(\\d{14})_(\\d{2})_000000_(\\d{2}[A-Z]{3}).jp2";
    final static Pattern PATTERN = Pattern.compile(REGEX);

    public final String fileName;
    public final String type;
    public final String orbit;
    public final String start;
    public final String stop;
    public final String band;
    public final String utmId;

    private final String regex;
    private final Pattern pattern;

    private S2FileNameInfo(String fileName, String type, String orbit, String start, String stop, String band, String utmId) {
        this.fileName = fileName;
        this.type = type;
        this.orbit = orbit;
        this.start = start;
        this.stop = stop;
        this.band = band;
        this.utmId = utmId;

        this.regex = String.format(FORMAT, type, orbit, start, stop, utmId);
        this.pattern = Pattern.compile(this.regex);
    }

    public int getBand() {
        try {
            return Integer.parseInt(band);
        } catch (NumberFormatException e) {
            return -1;
        }
    }


    public int getBand(String fileName) {
        Matcher matcher = pattern.matcher(fileName);
        if (matcher.matches()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
        } else {
            return -1;
        }
    }

    public static S2FileNameInfo create(String fileName) {
        final Matcher matcher = PATTERN.matcher(fileName);
        final boolean matches = matcher.matches();
        if (matches) {
            return new S2FileNameInfo(fileName,
                                      matcher.group(1),
                                      matcher.group(2),
                                      matcher.group(3),
                                      matcher.group(4),
                                      matcher.group(5),
                                      matcher.group(6));
        } else {
            return null;
        }
    }
}

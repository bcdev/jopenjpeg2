package org.esa.beam.dataio.sentinel2;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Norman Fomferra
 */
public class S2FileNameInfo {

    final static String REGEX = "IMG_GPP([A-Z0-9]{3})_(\\d{3})_(\\d{14})_(\\d{14})_(\\d{2})_000000_(\\d{2}[A-Z]{3}).jp2";
    final static Pattern PATTERN = Pattern.compile(REGEX);

    public final String type;
    public final String orbit;
    public final String start;
    public final String stop;
    public final String band;
    public final String utmId;

    private S2FileNameInfo(String type, String orbit, String start, String stop, String band, String utmId) {
        this.type = type;
        this.orbit = orbit;
        this.start = start;
        this.stop = stop;
        this.band = band;
        this.utmId = utmId;
    }

    public static S2FileNameInfo create(String name) {
        final Matcher matcher = PATTERN.matcher(name);
        final boolean matches = matcher.matches();
        if (matches) {
            return new S2FileNameInfo(matcher.group(1),
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

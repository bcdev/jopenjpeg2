package org.esa.beam.dataio.sentinel2;

/**
* @author Norman Fomferra
*/
public class S2WavebandInfo {
    String bandName;
    double centralWavelength;
    double bandWidth;
    int resolution;

    S2WavebandInfo(String bandName, double centralWavelength, double bandWidth, int resolution) {
        this.bandName = bandName;
        this.centralWavelength = centralWavelength;
        this.bandWidth = bandWidth;
        this.resolution = resolution;
    }
}

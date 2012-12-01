package jopenjpeg2.jna;

import com.sun.jna.Pointer;
import com.sun.jna.PointerType;

/**
* @author Norman Fomferra
*/
public class jopj_Img extends PointerType {
    public jopj_Img() {
    }

    public jopj_Img(Pointer p) {
        super(p);
    }
}

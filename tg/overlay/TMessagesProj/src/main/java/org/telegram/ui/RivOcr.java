package org.telegram.ui;

import android.graphics.Bitmap;
import android.util.SparseArray;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import org.telegram.messenger.ApplicationLoader;

/** Riv Post: reads printed text out of a picture with the vision library Telegram already carries (Latin letters only). */
final class RivOcr {

    private RivOcr() {
    }

    /** Returns the text, or null when the phone's text reader isn't available (no Google Play services, or not downloaded yet). */
    static String read(Bitmap bitmap) {
        TextRecognizer recognizer = new TextRecognizer.Builder(ApplicationLoader.applicationContext).build();
        try {
            if (!recognizer.isOperational()) return null;
            Frame frame = new Frame.Builder().setBitmap(bitmap).build();
            SparseArray<TextBlock> blocks = recognizer.detect(frame);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < blocks.size(); i++) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(blocks.valueAt(i).getValue());
            }
            return sb.toString();
        } finally {
            recognizer.release();
        }
    }
}

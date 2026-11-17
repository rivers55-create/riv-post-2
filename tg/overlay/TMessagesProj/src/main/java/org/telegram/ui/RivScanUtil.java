package org.telegram.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import org.telegram.messenger.ApplicationLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

/** Riv Post: shared helpers for the scanners and PDF tools (copy a picked file, load a photo, clean up a page, write a PDF). */
final class RivScanUtil {

    static final int ORIGINAL = 0, DOCUMENT = 1, GRAY = 2;

    private RivScanUtil() {
    }

    static File dir(String name) {
        File d = new File(ApplicationLoader.applicationContext.getCacheDir(), name);
        d.mkdirs();
        return d;
    }

    /** Copies something the file picker or camera gave us into our own cache so we can read it freely. */
    static File copy(Uri uri, String folder, String suffix) throws Exception {
        File f = new File(dir(folder), "f" + System.nanoTime() + suffix);
        try (InputStream in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(f)) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        }
        return f;
    }

    /** Loads a photo no larger than maxSide pixels on its long edge, turned upright. */
    static Bitmap load(File file, int maxSide) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), o);
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap b = BitmapFactory.decodeFile(file.getAbsolutePath(), o2);
        if (b == null) return null;
        try {
            android.media.ExifInterface exif = new android.media.ExifInterface(file.getAbsolutePath());
            int orient = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL);
            int deg = orient == android.media.ExifInterface.ORIENTATION_ROTATE_90 ? 90 : orient == android.media.ExifInterface.ORIENTATION_ROTATE_180 ? 180 : orient == android.media.ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            if (deg != 0) {
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.postRotate(deg);
                Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (r != b) b.recycle();
                b = r;
            }
        } catch (Throwable ignored) {
        }
        int m = Math.max(b.getWidth(), b.getHeight());
        if (m > maxSide) {
            float k = maxSide / (float) m;
            Bitmap r = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * k)), Math.max(1, Math.round(b.getHeight() * k)), true);
            if (r != b) b.recycle();
            b = r;
        }
        return b;
    }

    static Bitmap filter(Bitmap src, int mode) {
        if (mode == ORIGINAL) return src;
        if (mode == GRAY) {
            Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(0);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            p.setColorFilter(new ColorMatrixColorFilter(cm));
            new Canvas(out).drawBitmap(src, 0, 0, p);
            return out;
        }
        // DOCUMENT: divide by the blurred background so shadows and uneven light vanish, then whiten the paper
        int w = src.getWidth(), h = src.getHeight();
        Bitmap small = Bitmap.createScaledBitmap(src, Math.max(1, w / 24), Math.max(1, h / 24), true);
        Bitmap bgBmp = Bitmap.createScaledBitmap(small, w, h, true);
        small.recycle();
        int[] px = new int[w * h];
        int[] bg = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        bgBmp.getPixels(bg, 0, w, 0, 0, w, h);
        bgBmp.recycle();
        for (int i = 0; i < px.length; i++) {
            int c = px[i], b = bg[i];
            int g = (((c >> 16) & 255) * 30 + ((c >> 8) & 255) * 59 + (c & 255) * 11) / 100;
            int gb = (((b >> 16) & 255) * 30 + ((b >> 8) & 255) * 59 + (b & 255) * 11) / 100;
            int v = Math.min(255, g * 255 / Math.max(gb, 1));
            v = v > 215 ? 255 : (int) (v * 0.82f);
            px[i] = 0xFF000000 | (v << 16) | (v << 8) | v;
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        return out;
    }

    /** One page per bitmap, 595 points wide (A4 width), the height following each picture. */
    static void writePdf(List<Bitmap> pages, File out) throws Exception {
        PdfDocument doc = new PdfDocument();
        try {
            int n = 1;
            for (Bitmap b : pages) {
                int pw = 595;
                int ph = Math.max(1, Math.round(pw * b.getHeight() / (float) b.getWidth()));
                PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(pw, ph, n++).create());
                page.getCanvas().drawBitmap(b, null, new Rect(0, 0, pw, ph), new Paint(Paint.FILTER_BITMAP_FLAG));
                doc.finishPage(page);
            }
            try (FileOutputStream fos = new FileOutputStream(out)) {
                doc.writeTo(fos);
            }
        } finally {
            doc.close();
        }
    }
}

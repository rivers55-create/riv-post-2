package org.telegram.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.RivT;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.util.ArrayList;

/**
 * Riv Post: one scanner screen with two modes.
 * TEXT: take or pick a photo and read the printed text out of it (copy, share).
 * DOCUMENT: take or pick pages, clean them up (document / grey / original) and save them as one PDF.
 */
public class RivScanActivity extends RivToolScreen {

    public static final int TEXT = 0, DOCUMENT = 1;
    private static final int REQ_GALLERY = 7751, REQ_CAMERA = 7752;

    private final int mode;
    private final ArrayList<File> pages = new ArrayList<>();
    private int filter = RivScanUtil.DOCUMENT;
    private String text, status = "";
    private boolean busy;
    private File cameraFile, lastPdf;

    public RivScanActivity(int mode) {
        this.mode = mode;
    }

    @Override
    protected String title() {
        return mode == TEXT ? RivT.t("Text scanner", "ماسح النصوص") : RivT.t("Document scanner", "ماسح المستندات");
    }

    @Override
    protected void build() {
        addAction(RivT.t("Take a photo", "التقاط صورة"), true, this::camera);
        addAction(RivT.t("Choose from gallery", "اختيار من المعرض"), mode == DOCUMENT || text != null || !status.isEmpty(), this::gallery);
        if (mode == TEXT) {
            if (busy) addInfo(RivT.t("Reading…", "جارٍ القراءة…"));
            if (text != null) {
                addHeader(RivT.t("Text found", "النص المكتشف"));
                TextView tv = addText(text.isEmpty() ? RivT.t("No text found in this picture.", "لم يُعثر على نص في هذه الصورة.") : text, false);
                tv.setTextIsSelectable(true);
                if (!text.isEmpty()) {
                    addAction(RivT.t("Copy all", "نسخ الكل"), true, () -> copy(text));
                    addAction(RivT.t("Share text", "مشاركة النص"), false, () -> {
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType("text/plain");
                        i.putExtra(Intent.EXTRA_TEXT, text);
                        ctx.startActivity(Intent.createChooser(i, title()));
                    });
                }
            }
            if (!status.isEmpty()) addInfo(status);
            addInfo(RivT.t("Reads printed text in Latin letters (English and similar). It uses the text reader that comes with Google Play services.",
                    "يقرأ النص المطبوع بالأحرف اللاتينية (الإنجليزية وما شابهها). يستخدم قارئ النصوص الذي يأتي مع خدمات Google Play."));
            return;
        }
        addHeader(RivT.t("Pages", "الصفحات") + " (" + pages.size() + ")");
        String[] names = {RivT.t("Original", "الأصلية"), RivT.t("Clean document", "مستند نظيف"), RivT.t("Grey", "رمادي")};
        addCell(RivT.t("Look", "المظهر"), names[filter], true, () -> choose(RivT.t("Look", "المظهر"), names, i -> {
            filter = i;
            rebuild();
        }), null);
        for (int i = 0; i < pages.size(); i++) {
            final int idx = i;
            addCell(RivT.t("Page ", "صفحة ") + (i + 1), "", true, null, () -> confirm(RivT.t("Remove page ", "حذف الصفحة ") + (idx + 1) + "?", "", RivT.t("Remove", "حذف"), () -> {
                pages.remove(idx);
                rebuild();
            }));
        }
        if (!pages.isEmpty()) {
            addAction(busy ? RivT.t("Saving…", "جارٍ الحفظ…") : RivT.t("Save as PDF", "حفظ كملف PDF"), lastPdf != null, this::savePdf);
        }
        if (lastPdf != null) {
            addAction(RivT.t("Share the PDF", "مشاركة ملف PDF"), false, () -> {
                try {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("application/pdf");
                    i.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(ctx, ApplicationLoader.getApplicationId() + ".provider", lastPdf));
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    ctx.startActivity(Intent.createChooser(i, title()));
                } catch (Throwable t) {
                    toast(String.valueOf(t.getMessage()));
                }
            });
        }
        if (!status.isEmpty()) addInfo(status);
        addInfo(RivT.t("Long-press a page to remove it. Pages are saved in the order you added them.", "اضغط مطولاً على صفحة لحذفها. تُحفظ الصفحات بالترتيب الذي أضفتها به."));
    }

    private void gallery() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        if (mode == DOCUMENT) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(i, title()), REQ_GALLERY);
    }

    private void camera() {
        try {
            cameraFile = AndroidUtilities.generatePicturePath();
            Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(ctx, ApplicationLoader.getApplicationId() + ".provider", cameraFile));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(i, REQ_CAMERA);
        } catch (Throwable t) {
            toast(RivT.t("The camera can't open here. Choose from the gallery instead.", "تعذّر فتح الكاميرا. اختر من المعرض بدلاً من ذلك."));
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) return;
        final ArrayList<Uri> uris = new ArrayList<>();
        if (requestCode == REQ_GALLERY && data != null) {
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
        } else if (requestCode == REQ_CAMERA && cameraFile != null && cameraFile.exists()) {
            uris.add(Uri.fromFile(cameraFile));
        }
        if (uris.isEmpty()) return;
        busy = true;
        status = "";
        rebuild();
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<File> got = new ArrayList<>();
            String error = "";
            for (Uri u : uris) {
                try {
                    got.add(RivScanUtil.copy(u, "rivscan", ".img"));
                } catch (Throwable t) {
                    error = String.valueOf(t.getMessage());
                }
            }
            String ocrText = null;
            boolean unavailable = false;
            if (mode == TEXT && !got.isEmpty()) {
                try {
                    Bitmap b = RivScanUtil.load(got.get(0), 2400);
                    if (b != null) {
                        ocrText = RivOcr.read(b);
                        unavailable = ocrText == null;
                        b.recycle();
                    }
                } catch (Throwable t) {
                    error = String.valueOf(t.getMessage());
                }
            }
            final String fErr = error, fText = ocrText;
            final boolean fUnavailable = unavailable;
            AndroidUtilities.runOnUIThread(() -> {
                busy = false;
                if (mode == TEXT) {
                    text = fText;
                    status = fUnavailable ? RivT.t("The text reader isn't available on this phone (it needs Google Play services, and may still be downloading).", "قارئ النصوص غير متاح على هذا الهاتف (يحتاج خدمات Google Play وقد يكون قيد التنزيل).") : fErr;
                } else {
                    pages.addAll(got);
                    lastPdf = null;
                    status = fErr;
                }
                rebuild();
            });
        });
    }

    private void savePdf() {
        if (busy || pages.isEmpty()) return;
        busy = true;
        status = "";
        rebuild();
        final ArrayList<File> src = new ArrayList<>(pages);
        final int look = filter;
        Utilities.globalQueue.postRunnable(() -> {
            String error = "";
            File out = null;
            ArrayList<Bitmap> done = new ArrayList<>();
            try {
                for (File f : src) {
                    Bitmap b = RivScanUtil.load(f, 1800);
                    if (b == null) continue;
                    Bitmap r = RivScanUtil.filter(b, look);
                    if (r != b) b.recycle();
                    done.add(r);
                }
                File dir = ctx.getExternalFilesDir("Scans");
                if (dir == null) dir = RivScanUtil.dir("scans");
                dir.mkdirs();
                out = new File(dir, "scan_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(new java.util.Date()) + ".pdf");
                RivScanUtil.writePdf(done, out);
            } catch (Throwable t) {
                error = String.valueOf(t.getMessage());
                out = null;
            }
            for (Bitmap b : done) b.recycle();
            final File fOut = out;
            final String fErr = error;
            AndroidUtilities.runOnUIThread(() -> {
                busy = false;
                lastPdf = fOut;
                status = fOut != null ? RivT.t("Saved: ", "تم الحفظ: ") + fOut.getName() : fErr;
                rebuild();
            });
        });
    }
}

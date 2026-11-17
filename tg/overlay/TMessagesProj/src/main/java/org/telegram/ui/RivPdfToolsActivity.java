package org.telegram.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.RivT;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.TreeSet;

/**
 * Riv Post: small PDF tools using only what Android already has. Merge PDFs, keep chosen pages,
 * and turn pages into pictures. Results are rebuilt from page pictures, so text in the new file
 * is no longer selectable.
 */
public class RivPdfToolsActivity extends RivToolScreen {

    private static final int REQ_MERGE = 7761, REQ_EXTRACT = 7762, REQ_IMAGES = 7763;
    private static final float SCALE = 2f; // 144 dpi

    private String status = "";
    private boolean busy;
    private final ArrayList<File> results = new ArrayList<>();
    private String pendingRange = "";

    @Override
    protected String title() {
        return RivT.t("PDF tools", "أدوات PDF");
    }

    @Override
    protected void build() {
        addHeader(RivT.t("Choose a tool", "اختر أداة"));
        addAction(RivT.t("Merge PDFs into one", "دمج ملفات PDF في ملف واحد"), true, () -> pick(REQ_MERGE, true));
        addAction(RivT.t("Keep only some pages", "الاحتفاظ ببعض الصفحات فقط"), true, () -> ask(RivT.t("Pages to keep", "الصفحات المطلوبة"), "1-3,5", pendingRange, false, v -> {
            pendingRange = v.trim();
            if (!pendingRange.isEmpty()) pick(REQ_EXTRACT, false);
        }));
        addAction(RivT.t("Turn pages into pictures", "تحويل الصفحات إلى صور"), false, () -> pick(REQ_IMAGES, false));
        if (busy) addInfo(RivT.t("Working…", "جارٍ العمل…"));
        if (!status.isEmpty()) addInfo(status);
        if (!results.isEmpty()) {
            addHeader(RivT.t("Results", "النتائج"));
            for (int i = 0; i < results.size(); i++) {
                final File f = results.get(i);
                addCell(f.getName(), "", i != results.size() - 1, () -> share(f), null);
            }
            addInfo(RivT.t("Tap a result to share or save it.", "اضغط على نتيجة لمشاركتها أو حفظها."));
        }
    }

    private void pick(int req, boolean multiple) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("application/pdf");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        if (multiple) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(i, title()), req);
    }

    private void share(File f) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(f.getName().endsWith(".pdf") ? "application/pdf" : "image/png");
            i.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(ctx, ApplicationLoader.getApplicationId() + ".provider", f));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(Intent.createChooser(i, title()));
        } catch (Throwable t) {
            toast(String.valueOf(t.getMessage()));
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) return;
        final ArrayList<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) return;
        if (requestCode == REQ_MERGE && uris.size() < 2) {
            status = RivT.t("Choose at least two PDF files.", "اختر ملفي PDF على الأقل.");
            rebuild();
            return;
        }
        busy = true;
        status = "";
        rebuild();
        final String range = pendingRange;
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<File> made = new ArrayList<>();
            String error = "";
            try {
                ArrayList<File> src = new ArrayList<>();
                for (Uri u : uris) src.add(RivScanUtil.copy(u, "rivpdf", ".pdf"));
                File outDir = ctx.getExternalFilesDir("PDF");
                if (outDir == null) outDir = RivScanUtil.dir("pdfout");
                outDir.mkdirs();
                String stamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(new java.util.Date());
                if (requestCode == REQ_MERGE) {
                    File out = new File(outDir, "merged_" + stamp + ".pdf");
                    writePages(src, null, out);
                    made.add(out);
                } else if (requestCode == REQ_EXTRACT) {
                    File out = new File(outDir, "pages_" + stamp + ".pdf");
                    writePages(src, range, out);
                    made.add(out);
                } else {
                    made.addAll(toImages(src.get(0), outDir, stamp));
                }
            } catch (Throwable t) {
                error = String.valueOf(t.getMessage());
            }
            final String fErr = error;
            AndroidUtilities.runOnUIThread(() -> {
                busy = false;
                results.addAll(0, made);
                status = !fErr.isEmpty() ? fErr : (made.isEmpty() ? RivT.t("Nothing was made.", "لم يُنشأ شيء.") : RivT.t("Done.", "تم."));
                rebuild();
            });
        });
    }

    /** "1-3,5" -> {1,2,3,5}; null = every page. */
    private static TreeSet<Integer> parseRange(String range) {
        if (range == null) return null;
        TreeSet<Integer> set = new TreeSet<>();
        for (String part : range.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            int dash = part.indexOf('-');
            try {
                if (dash > 0) {
                    int a = Integer.parseInt(part.substring(0, dash).trim());
                    int b = Integer.parseInt(part.substring(dash + 1).trim());
                    for (int i = Math.min(a, b); i <= Math.max(a, b) && i < 5000; i++) set.add(i);
                } else {
                    set.add(Integer.parseInt(part));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return set;
    }

    private static void writePages(ArrayList<File> files, String range, File out) throws Exception {
        TreeSet<Integer> keep = parseRange(range);
        PdfDocument doc = new PdfDocument();
        int number = 1;
        try {
            for (File f : files) {
                try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY); PdfRenderer renderer = new PdfRenderer(fd)) {
                    for (int p = 0; p < renderer.getPageCount(); p++) {
                        // page numbers in "keep" count inside the single file (extract) or are ignored (merge)
                        if (keep != null && !keep.contains(p + 1)) continue;
                        PdfRenderer.Page page = renderer.openPage(p);
                        int w = page.getWidth(), h = page.getHeight();
                        Bitmap bmp = Bitmap.createBitmap(Math.round(w * SCALE), Math.round(h * SCALE), Bitmap.Config.ARGB_8888);
                        bmp.eraseColor(Color.WHITE);
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                        page.close();
                        PdfDocument.Page outPage = doc.startPage(new PdfDocument.PageInfo.Builder(w, h, number++).create());
                        outPage.getCanvas().drawBitmap(bmp, null, new Rect(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG));
                        doc.finishPage(outPage);
                        bmp.recycle();
                    }
                }
            }
            if (number == 1) throw new Exception(RivT.t("No page matched.", "لا توجد صفحة مطابقة."));
            try (FileOutputStream fos = new FileOutputStream(out)) {
                doc.writeTo(fos);
            }
        } finally {
            doc.close();
        }
    }

    private static ArrayList<File> toImages(File pdf, File outDir, String stamp) throws Exception {
        ArrayList<File> made = new ArrayList<>();
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY); PdfRenderer renderer = new PdfRenderer(fd)) {
            int count = Math.min(renderer.getPageCount(), 60);
            for (int p = 0; p < count; p++) {
                PdfRenderer.Page page = renderer.openPage(p);
                Bitmap bmp = Bitmap.createBitmap(Math.round(page.getWidth() * SCALE), Math.round(page.getHeight() * SCALE), Bitmap.Config.ARGB_8888);
                bmp.eraseColor(Color.WHITE);
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();
                File f = new File(outDir, "page_" + stamp + "_" + (p + 1) + ".png");
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
                bmp.recycle();
                made.add(f);
            }
        }
        return made;
    }
}

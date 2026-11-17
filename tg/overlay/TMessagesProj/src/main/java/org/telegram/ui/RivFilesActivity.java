package org.telegram.ui;

import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.RivT;
import org.telegram.messenger.Utilities;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Riv Post: file manager for what Telegram saved on this phone (its Documents, Video, Music, Photos
 * and cache folders). Open, share, rename, delete, zip, unzip, and cut audio. Only these folders
 * are reachable; the rest of the phone's storage is not touched.
 */
public class RivFilesActivity extends RivToolScreen {

    private File dir;          // null = the list of folders
    private File root;         // the folder we started in (we do not go above it)
    private int sort = 0;      // 0 name, 1 newest, 2 biggest
    private boolean busy;

    @Override
    protected String title() {
        return RivT.t("Files", "الملفات");
    }

    private ArrayList<File> roots() {
        ArrayList<File> out = new ArrayList<>();
        int[] types = {FileLoader.MEDIA_DIR_DOCUMENT, FileLoader.MEDIA_DIR_VIDEO, FileLoader.MEDIA_DIR_AUDIO, FileLoader.MEDIA_DIR_IMAGE, FileLoader.MEDIA_DIR_CACHE};
        for (int t : types) {
            try {
                File f = FileLoader.getDirectory(t);
                if (f != null && f.exists() && !out.contains(f)) out.add(f);
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private String rootName(File f) {
        try {
            if (f.equals(FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT))) return RivT.t("Documents", "المستندات");
            if (f.equals(FileLoader.getDirectory(FileLoader.MEDIA_DIR_VIDEO))) return RivT.t("Videos", "الفيديوهات");
            if (f.equals(FileLoader.getDirectory(FileLoader.MEDIA_DIR_AUDIO))) return RivT.t("Music and voice", "الموسيقى والصوت");
            if (f.equals(FileLoader.getDirectory(FileLoader.MEDIA_DIR_IMAGE))) return RivT.t("Photos", "الصور");
            if (f.equals(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE))) return RivT.t("Cache", "الكاش");
        } catch (Throwable ignored) {
        }
        return f.getName();
    }

    @Override
    protected void build() {
        if (dir == null) {
            addHeader(RivT.t("Telegram folders", "مجلدات تيليجرام"));
            ArrayList<File> r = roots();
            for (int i = 0; i < r.size(); i++) {
                final File f = r.get(i);
                addCell(rootName(f), count(f), i != r.size() - 1, () -> {
                    root = f;
                    dir = f;
                    rebuild();
                }, null);
            }
            addInfo(RivT.t("Only files Telegram saved are shown here.", "تظهر هنا الملفات التي حفظها تيليجرام فقط."));
            return;
        }
        addAction("↑  " + (dir.equals(root) ? RivT.t("All folders", "كل المجلدات") : RivT.t("Up", "للأعلى")), true, () -> {
            if (dir.equals(root)) dir = null;
            else dir = dir.getParentFile();
            rebuild();
        });
        String[] sorts = {RivT.t("Name", "الاسم"), RivT.t("Newest", "الأحدث"), RivT.t("Biggest", "الأكبر")};
        addCell(RivT.t("Sort by", "ترتيب حسب"), sorts[sort], false, () -> {
            sort = (sort + 1) % 3;
            rebuild();
        }, null);
        if (busy) addInfo(RivT.t("Working…", "جارٍ العمل…"));
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            addInfo(RivT.t("This folder is empty.", "هذا المجلد فارغ."));
            return;
        }
        ArrayList<File> list = new ArrayList<>(Arrays.asList(files));
        Comparator<File> cmp = (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            if (sort == 1) return Long.compare(b.lastModified(), a.lastModified());
            if (sort == 2) return Long.compare(b.length(), a.length());
            return a.getName().compareToIgnoreCase(b.getName());
        };
        java.util.Collections.sort(list, cmp);
        int shown = 0;
        for (File f : list) {
            if (f.getName().startsWith(".")) continue;
            if (++shown > 400) break;
            final File file = f;
            String sub = f.isDirectory() ? count(f) : size(f.length()) + "  ·  " + when(f.lastModified());
            addCell((f.isDirectory() ? "📁  " : "") + f.getName(), f.isDirectory() ? sub : "", true,
                    () -> {
                        if (file.isDirectory()) {
                            dir = file;
                            rebuild();
                        } else open(file);
                    }, () -> menu(file));
            if (!f.isDirectory()) addText(sub, false).setTextSize(12);
        }
        addInfo(RivT.t("Tap to open. Long-press for share, rename, delete, zip, unzip and cut audio.", "اضغط للفتح. اضغط مطولاً للمشاركة وإعادة التسمية والحذف والضغط وفك الضغط وقص الصوت."));
    }

    private static String count(File d) {
        File[] f = d.listFiles();
        return (f == null ? 0 : f.length) + RivT.t(" items", " عنصر");
    }

    private static String size(long b) {
        if (b < 1024) return b + " B";
        if (b < 1048576) return String.format(Locale.US, "%.0f KB", b / 1024.0);
        if (b < 1073741824L) return String.format(Locale.US, "%.1f MB", b / 1048576.0);
        return String.format(Locale.US, "%.2f GB", b / 1073741824.0);
    }

    private String mimeOf(File f) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(f).toString());
        String m = ext == null ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.US));
        return m == null ? "*/*" : m;
    }

    private void open(File f) {
        try {
            AndroidUtilities.openForView(f, f.getName(), mimeOf(f), getParentActivity(), null, false);
        } catch (Throwable t) {
            toast(RivT.t("No app can open this file.", "لا يوجد تطبيق يفتح هذا الملف."));
        }
    }

    private void share(File f) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mimeOf(f));
            i.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(ctx, ApplicationLoader.getApplicationId() + ".provider", f));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(Intent.createChooser(i, f.getName()));
        } catch (Throwable t) {
            toast(RivT.t("Could not share this file.", "تعذرت مشاركة هذا الملف."));
        }
    }

    private void menu(File f) {
        ArrayList<String> names = new ArrayList<>();
        ArrayList<Runnable> acts = new ArrayList<>();
        if (!f.isDirectory()) {
            names.add(RivT.t("Share", "مشاركة"));
            acts.add(() -> share(f));
        }
        names.add(RivT.t("Rename", "إعادة تسمية"));
        acts.add(() -> ask(RivT.t("Rename", "إعادة تسمية"), "", f.getName(), false, v -> {
            String n = v.trim().replace("/", "_");
            if (n.isEmpty()) return;
            File to = new File(f.getParentFile(), n);
            if (to.exists()) toast(RivT.t("That name is taken.", "هذا الاسم مستخدم."));
            else if (!f.renameTo(to)) toast(RivT.t("Could not rename.", "تعذرت إعادة التسمية."));
            rebuild();
        }));
        names.add(RivT.t("Zip", "ضغط ZIP"));
        acts.add(() -> zip(f));
        if (f.getName().toLowerCase(Locale.US).endsWith(".zip")) {
            names.add(RivT.t("Unzip here", "فك الضغط هنا"));
            acts.add(() -> unzip(f));
        }
        String lower = f.getName().toLowerCase(Locale.US);
        if (lower.endsWith(".m4a") || lower.endsWith(".aac") || lower.endsWith(".mp4") || lower.endsWith(".mp3") || lower.endsWith(".ogg") || lower.endsWith(".opus") || lower.endsWith(".wav")) {
            names.add(RivT.t("Cut audio…", "قص الصوت…"));
            acts.add(() -> presentFragment(new RivAudioCutActivity(f.getAbsolutePath())));
        }
        names.add(RivT.t("Delete", "حذف"));
        acts.add(() -> confirm(f.getName(), RivT.t("Delete this permanently? It cannot be undone.", "حذف هذا نهائياً؟ لا يمكن التراجع."), LocaleController.getString(R.string.Delete), () -> {
            deleteRecursive(f);
            rebuild();
        }));
        choose(f.getName(), names.toArray(new CharSequence[0]), w -> acts.get(w).run());
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] c = f.listFiles();
            if (c != null) for (File x : c) deleteRecursive(x);
        }
        f.delete();
    }

    private void zip(File src) {
        busy = true;
        rebuild();
        Utilities.globalQueue.postRunnable(() -> {
            String msg;
            try {
                File out = new File(src.getParentFile(), src.getName() + ".zip");
                int n = 1;
                while (out.exists()) out = new File(src.getParentFile(), src.getName() + "(" + (n++) + ").zip");
                try (ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
                    addToZip(z, src, src.isDirectory() ? src.getName() + "/" : "", src);
                }
                msg = RivT.t("Created ", "تم إنشاء ") + out.getName();
            } catch (Throwable t) {
                msg = RivT.t("Zip failed: ", "فشل الضغط: ") + t.getMessage();
            }
            final String m = msg;
            AndroidUtilities.runOnUIThread(() -> {
                busy = false;
                toast(m);
                rebuild();
            });
        });
    }

    private void addToZip(ZipOutputStream z, File f, String prefix, File top) throws Exception {
        if (f.isDirectory()) {
            File[] c = f.listFiles();
            if (c != null) for (File x : c) addToZip(z, x, f == top ? prefix : prefix + f.getName() + "/", top);
            return;
        }
        z.putNextEntry(new ZipEntry(top.isDirectory() ? prefix + f.getName() : f.getName()));
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(f))) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) z.write(buf, 0, r);
        }
        z.closeEntry();
    }

    private void unzip(File zip) {
        busy = true;
        rebuild();
        Utilities.globalQueue.postRunnable(() -> {
            String msg;
            try {
                String base = zip.getName().replaceAll("(?i)\\.zip$", "");
                File out = new File(zip.getParentFile(), base);
                int n = 1;
                while (out.exists()) out = new File(zip.getParentFile(), base + "(" + (n++) + ")");
                out.mkdirs();
                String outPath = out.getCanonicalPath() + File.separator;
                int files = 0;
                long total = 0;
                try (ZipInputStream z = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
                    ZipEntry e;
                    while ((e = z.getNextEntry()) != null) {
                        File target = new File(out, e.getName());
                        if (!target.getCanonicalPath().startsWith(outPath)) continue; // never write outside the new folder
                        if (e.isDirectory()) {
                            target.mkdirs();
                            continue;
                        }
                        target.getParentFile().mkdirs();
                        try (BufferedOutputStream o = new BufferedOutputStream(new FileOutputStream(target))) {
                            byte[] buf = new byte[65536];
                            int r;
                            while ((r = z.read(buf)) > 0) {
                                total += r;
                                if (total > 8L * 1024 * 1024 * 1024) throw new IllegalStateException("too large");
                                o.write(buf, 0, r);
                            }
                        }
                        files++;
                    }
                }
                msg = RivT.t("Unzipped ", "تم فك ") + files + RivT.t(" files into ", " ملف إلى ") + out.getName();
            } catch (Throwable t) {
                msg = RivT.t("Unzip failed: ", "فشل فك الضغط: ") + t.getMessage();
            }
            final String m = msg;
            AndroidUtilities.runOnUIThread(() -> {
                busy = false;
                toast(m);
                rebuild();
            });
        });
    }
}

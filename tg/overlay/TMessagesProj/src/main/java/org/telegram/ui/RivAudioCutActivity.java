package org.telegram.ui;

import android.content.Intent;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Environment;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.RivT;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Riv Post: audio cutter. Choose a start and end and save the piece as a new .m4a without
 * re-encoding (no loss). This works for AAC audio (.m4a, .aac, and the sound of .mp4 files).
 * MP3 / OGG / WAV cannot be written without re-encoding, so those are refused clearly.
 */
public class RivAudioCutActivity extends RivToolScreen {

    private static final int REQ_PICK = 7741;
    private String path;
    private double startSec, endSec, durationSec;
    private String status = "";
    private boolean busy;
    private String lastOut;

    public RivAudioCutActivity() {
        this(null);
    }

    public RivAudioCutActivity(String path) {
        this.path = path;
    }

    @Override
    protected String title() {
        return RivT.t("Audio cutter", "قاص الصوت");
    }

    @Override
    public boolean onFragmentCreate() {
        if (path != null) readDuration();
        return super.onFragmentCreate();
    }

    private void readDuration() {
        try {
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            r.setDataSource(path);
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            r.release();
            durationSec = d == null ? 0 : Long.parseLong(d) / 1000.0;
            startSec = 0;
            endSec = durationSec;
        } catch (Throwable t) {
            durationSec = 0;
            status = RivT.t("Could not read this file.", "تعذرت قراءة هذا الملف.");
        }
    }

    private static String mmss(double s) {
        int t = (int) Math.round(s);
        return String.format(Locale.US, "%d:%02d", t / 60, t % 60);
    }

    private static double parse(String v) {
        v = v.trim();
        if (v.contains(":")) {
            String[] p = v.split(":");
            return Integer.parseInt(p[0].trim()) * 60 + Double.parseDouble(p[1].trim());
        }
        return Double.parseDouble(v);
    }

    @Override
    protected void build() {
        addAction(RivT.t("Choose an audio file", "اختر ملفاً صوتياً"), true, () -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("audio/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, title()), REQ_PICK);
        });
        if (path == null) {
            addInfo(RivT.t("Choose a file, or long-press an audio file in Files and pick Cut audio.", "اختر ملفاً، أو اضغط مطولاً على ملف صوتي في الملفات واختر قص الصوت."));
            return;
        }
        addCell(RivT.t("File", "الملف"), new File(path).getName(), true, null, null);
        addCell(RivT.t("Length", "المدة"), mmss(durationSec), true, null, null);
        addCell(RivT.t("Start", "البداية"), mmss(startSec), true, () -> ask(RivT.t("Start (m:ss or seconds)", "البداية (د:ث أو ثوانٍ)"), "0:00", mmss(startSec), false, v -> {
            try {
                startSec = Math.max(0, Math.min(parse(v), durationSec));
            } catch (Throwable ignored) {
            }
            rebuild();
        }), null);
        addCell(RivT.t("End", "النهاية"), mmss(endSec), true, () -> ask(RivT.t("End (m:ss or seconds)", "النهاية (د:ث أو ثوانٍ)"), mmss(durationSec), mmss(endSec), false, v -> {
            try {
                endSec = Math.max(0, Math.min(parse(v), durationSec));
            } catch (Throwable ignored) {
            }
            rebuild();
        }), null);
        addAction(busy ? RivT.t("Cutting…", "جارٍ القص…") : RivT.t("Cut and save", "قص واحفظ"), false, this::cut);
        if (!status.isEmpty()) addInfo(status);
        if (lastOut != null) {
            addAction(RivT.t("Share the cut file", "مشاركة الملف المقصوص"), false, () -> {
                try {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("audio/mp4");
                    i.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(ctx, ApplicationLoader.getApplicationId() + ".provider", new File(lastOut)));
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    ctx.startActivity(Intent.createChooser(i, title()));
                } catch (Throwable t) {
                    toast(String.valueOf(t.getMessage()));
                }
            });
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_PICK || resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        Utilities.globalQueue.postRunnable(() -> {
            try {
                File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "rivcut");
                dir.mkdirs();
                File f = new File(dir, "in_" + System.currentTimeMillis());
                try (InputStream in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(f)) {
                    byte[] buf = new byte[65536];
                    int r;
                    while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    path = f.getAbsolutePath();
                    lastOut = null;
                    status = "";
                    readDuration();
                    rebuild();
                });
            } catch (Throwable t) {
                AndroidUtilities.runOnUIThread(() -> {
                    status = String.valueOf(t.getMessage());
                    rebuild();
                });
            }
        });
    }

    private void cut() {
        if (busy || path == null) return;
        if (endSec - startSec < 0.5) {
            status = RivT.t("The end must be after the start.", "يجب أن تكون النهاية بعد البداية.");
            rebuild();
            return;
        }
        busy = true;
        status = "";
        rebuild();
        final String src = path;
        final long startUs = (long) (startSec * 1e6), endUs = (long) (endSec * 1e6);
        Utilities.globalQueue.postRunnable(() -> {
            String msg;
            String outPath = null;
            MediaExtractor ex = new MediaExtractor();
            MediaMuxer mux = null;
            try {
                ex.setDataSource(src);
                int track = -1;
                MediaFormat fmt = null;
                for (int i = 0; i < ex.getTrackCount(); i++) {
                    MediaFormat f = ex.getTrackFormat(i);
                    String mime = f.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("audio/")) {
                        track = i;
                        fmt = f;
                        break;
                    }
                }
                if (track < 0) throw new IllegalStateException(RivT.t("No audio found.", "لا يوجد صوت."));
                if (!"audio/mp4a-latm".equals(fmt.getString(MediaFormat.KEY_MIME))) {
                    throw new IllegalStateException(RivT.t("Only AAC audio (.m4a, .aac, or the sound of .mp4) can be cut without re-encoding.", "يمكن قص صوت AAC فقط (m4a و aac أو صوت mp4) دون إعادة ترميز."));
                }
                File outDir = ApplicationLoader.applicationContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
                if (outDir == null) outDir = new File(ApplicationLoader.applicationContext.getFilesDir(), "cuts");
                outDir.mkdirs();
                File out = new File(outDir, "cut_" + System.currentTimeMillis() + ".m4a");
                outPath = out.getAbsolutePath();
                mux = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                int dst = mux.addTrack(fmt);
                mux.start();
                ex.selectTrack(track);
                ex.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                ByteBuffer buf = ByteBuffer.allocate(1 << 18);
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                long first = -1;
                while (true) {
                    buf.clear();
                    int size = ex.readSampleData(buf, 0);
                    if (size < 0) break;
                    long t = ex.getSampleTime();
                    if (t > endUs) break;
                    if (t >= startUs) {
                        if (first < 0) first = t;
                        info.set(0, size, t - first, MediaCodec.BUFFER_FLAG_KEY_FRAME);
                        mux.writeSampleData(dst, buf, info);
                    }
                    ex.advance();
                }
                mux.stop();
                mux.release();
                mux = null;
                lastOut = outPath;
                msg = RivT.t("Saved: ", "تم الحفظ: ") + outPath;
            } catch (Throwable t) {
                msg = RivT.t("Could not cut: ", "تعذر القص: ") + t.getMessage();
                try {
                    if (mux != null) mux.release();
                } catch (Throwable ignored) {
                }
            } finally {
                ex.release();
            }
            final String m = msg;
            AndroidUtilities.runOnUIThread(() -> {
                busy = false;
                status = m;
                rebuild();
            });
        });
    }
}

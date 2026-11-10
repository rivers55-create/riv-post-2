package org.webrtc.audio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Riv Post's taps into the call audio: mic gain, voice effects, playing audio into
 * the call, hold, volume / tone for the other side, recording and captions.
 * All audio is 16-bit little-endian PCM.
 */
public final class CallAudioHooks {
  private CallAudioHooks() { }

  public static volatile int recordRate = 48000, recordChannels = 1;
  public static volatile int playRate = 48000, playChannels = 1;

  // ---- settings (set from Kotlin)
  public static volatile float micGain = 1f;        // whisper / boost
  public static volatile float playGain = 1f;       // their volume
  public static volatile float bass = 0f, treble = 0f; // -1..1
  public static volatile int voice = 0;             // 0 normal, 1 deep, 2 high, 3 robot
  public static volatile boolean deafen = false;    // hear nothing
  public static volatile boolean hold = false;      // send only injected audio / silence
  public static volatile boolean injectMix = true;  // mix injected audio with mic
  public static volatile float micLevel = 0f;       // 0..1, for the "you're muted" reminder

  /** Someone who wants the other side's audio (captions). */
  public interface PlayTap { void onPlayout(byte[] pcm, int len, int rate, int channels); }
  public static volatile PlayTap playTap;

  // ---- injected audio: mono shorts at recordRate
  private static final Object injLock = new Object();
  private static short[] inj = new short[0];
  private static int injRead = 0, injWrite = 0, injCount = 0;

  public static void injectClear() { synchronized (injLock) { injRead = injWrite = injCount = 0; } }

  public static int injectQueued() { synchronized (injLock) { return injCount; } }

  /** Adds mono samples at recordRate. Returns how many were accepted. */
  public static int inject(short[] s, int n) {
    synchronized (injLock) {
      if (inj.length == 0) inj = new short[recordRate * 4];
      int free = inj.length - injCount;
      int k = Math.min(free, n);
      for (int i = 0; i < k; i++) { inj[injWrite] = s[i]; injWrite = (injWrite + 1) % inj.length; }
      injCount += k;
      return k;
    }
  }

  private static int takeInject() {
    synchronized (injLock) {
      if (injCount == 0) return Integer.MIN_VALUE;
      short v = inj[injRead]; injRead = (injRead + 1) % inj.length; injCount--;
      return v;
    }
  }

  // ---- voice effect state
  private static final float[] delay = new float[4096];
  private static int dWrite = 0;
  private static float dPhase = 0f;
  private static double ringPhase = 0;

  private static float pitch(float x) {
    float ratio = voice == 1 ? 0.75f : 1.33f;
    int win = 2048;
    delay[dWrite] = x;
    dWrite = (dWrite + 1) & 4095;
    dPhase += 1f - ratio;
    if (dPhase < 0) dPhase += win;
    if (dPhase >= win) dPhase -= win;
    float p2 = dPhase + win / 2f; if (p2 >= win) p2 -= win;
    float a = read(dPhase), b = read(p2);
    float wa = 1f - Math.abs(dPhase - win / 2f) / (win / 2f);
    return a * wa + b * (1f - wa);
  }

  private static float read(float back) {
    float pos = dWrite - 1 - back;
    while (pos < 0) pos += 4096;
    int i = (int) pos; float f = pos - i;
    return delay[i & 4095] * (1 - f) + delay[(i + 1) & 4095] * f;
  }

  static void onRecord(byte[] a, int off, int len) {
    try {
      int ch = Math.max(1, recordChannels);
      long sum = 0; int n = len / 2;
      boolean anyInject = injectQueued() > 0;
      for (int i = 0, k = 0; i < n; i++) {
        int idx = off + i * 2;
        float x = (short) ((a[idx] & 0xff) | (a[idx + 1] << 8));
        sum += (long) (x * x);
        if (hold && !anyInject) x = 0;
        else {
          x *= micGain;
          if (voice == 1 || voice == 2) x = pitch(x);
          else if (voice == 3) { ringPhase += 2 * Math.PI * 60 / recordRate; x = (float) (x * Math.sin(ringPhase)); }
          if (anyInject && (k++ % ch == 0 || ch == 1)) {
            int v = takeInject();
            if (v != Integer.MIN_VALUE) x = (hold || !injectMix) ? v : x * 0.6f + v * 0.8f;
          }
        }
        int s = Math.max(-32768, Math.min(32767, Math.round(x)));
        a[idx] = (byte) s; a[idx + 1] = (byte) (s >> 8);
      }
      if (n > 0) micLevel = (float) Math.min(1.0, Math.sqrt(sum / (double) n) / 6000.0);
      Recorder r = recorder;
      if (r != null) r.mine(a, off, len, recordRate, ch);
    } catch (Throwable ignored) { }
  }

  private static float lowState = 0f, highPrev = 0f, highState = 0f;

  static void onPlayout(byte[] a, int off, int len) {
    try {
      int n = len / 2;
      if (deafen) {
        for (int i = 0; i < len; i++) a[off + i] = 0;
      } else if (playGain != 1f || bass != 0f || treble != 0f) {
        float g = playGain, b = bass, t = treble;
        for (int i = 0; i < n; i++) {
          int idx = off + i * 2;
          float x = (short) ((a[idx] & 0xff) | (a[idx + 1] << 8));
          lowState += 0.03f * (x - lowState);           // ~230 Hz low-pass
          float hp = x - highPrev + 0.8f * highState;   // simple high-pass
          highPrev = x; highState = hp;
          float y = (x + b * lowState + t * hp * 0.7f) * g;
          int s = Math.max(-32768, Math.min(32767, Math.round(y)));
          a[idx] = (byte) s; a[idx + 1] = (byte) (s >> 8);
        }
      }
      Recorder r = recorder;
      if (r != null) r.theirs(a, off, len, playRate, Math.max(1, playChannels));
      PlayTap tap = playTap;
      if (tap != null) {
        byte[] copy = new byte[len];
        System.arraycopy(a, off, copy, 0, len);
        tap.onPlayout(copy, len, playRate, Math.max(1, playChannels));
      }
    } catch (Throwable ignored) { }
  }

  // ---- recording: both sides mixed to a 16 kHz mono WAV
  private static volatile Recorder recorder;

  public static synchronized void startRecording(File f) throws IOException {
    stopRecording();
    recorder = new Recorder(f);
  }

  public static synchronized File stopRecording() {
    Recorder r = recorder;
    recorder = null;
    if (r == null) return null;
    r.close();
    return r.file;
  }

  public static boolean isRecording() { return recorder != null; }

  static final class Recorder {
    static final int RATE = 16000;
    final File file;
    private final LinkedBlockingQueue<short[]> mineQ = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<short[]> theirQ = new LinkedBlockingQueue<>();
    private final Thread writer;
    private volatile boolean running = true;
    private long dataBytes = 0;

    Recorder(File f) throws IOException {
      file = f;
      final OutputStream out = new FileOutputStream(f);
      out.write(new byte[44]);
      writer = new Thread(() -> {
        short[] pending = new short[0]; int pendPos = 0;
        try {
          while (running || !mineQ.isEmpty()) {
            short[] m = mineQ.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (m == null) continue;
            byte[] o = new byte[m.length * 2];
            for (int i = 0; i < m.length; i++) {
              if (pendPos >= pending.length) { short[] t = theirQ.poll(); pending = t != null ? t : new short[0]; pendPos = 0; }
              int v = m[i] + (pendPos < pending.length ? pending[pendPos++] : 0);
              v = Math.max(-32768, Math.min(32767, v));
              o[i * 2] = (byte) v; o[i * 2 + 1] = (byte) (v >> 8);
            }
            out.write(o);
            dataBytes += o.length;
            while (theirQ.size() > 50) theirQ.poll();
          }
          out.close();
          writeHeader();
        } catch (Throwable ignored) {
          try { out.close(); } catch (IOException ignored2) { }
        }
      }, "riv-call-rec");
      writer.start();
    }

    private static short[] down(byte[] a, int off, int len, int rate, int ch) {
      int step = Math.max(1, Math.round(rate / (float) RATE)) * ch;
      int frames = len / 2;
      short[] s = new short[frames / step];
      for (int i = 0; i < s.length; i++) {
        int sum = 0;
        for (int j = 0; j < step; j++) {
          int idx = off + (i * step + j) * 2;
          sum += (short) ((a[idx] & 0xff) | (a[idx + 1] << 8));
        }
        s[i] = (short) (sum / step);
      }
      return s;
    }

    void mine(byte[] a, int off, int len, int rate, int ch) { mineQ.offer(down(a, off, len, rate, ch)); }

    void theirs(byte[] a, int off, int len, int rate, int ch) { theirQ.offer(down(a, off, len, rate, ch)); }

    void close() {
      running = false;
      try { writer.join(2000); } catch (InterruptedException ignored) { }
    }

    private void writeHeader() throws IOException {
      try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
        byte[] h = new byte[44];
        put(h, 0, "RIFF"); le32(h, 4, (int) (36 + dataBytes)); put(h, 8, "WAVE");
        put(h, 12, "fmt "); le32(h, 16, 16); le16(h, 20, 1); le16(h, 22, 1);
        le32(h, 24, RATE); le32(h, 28, RATE * 2); le16(h, 32, 2); le16(h, 34, 16);
        put(h, 36, "data"); le32(h, 40, (int) dataBytes);
        raf.seek(0); raf.write(h);
      }
    }

    private static void put(byte[] h, int o, String s) { for (int i = 0; i < 4; i++) h[o + i] = (byte) s.charAt(i); }
    private static void le32(byte[] h, int o, int v) { for (int i = 0; i < 4; i++) h[o + i] = (byte) (v >> (8 * i)); }
    private static void le16(byte[] h, int o, int v) { h[o] = (byte) v; h[o + 1] = (byte) (v >> 8); }
  }
}

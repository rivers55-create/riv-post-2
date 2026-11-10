package org.thunderdog.challegram.voip;

import androidx.annotation.Keep;

// Java side of the call engine. Native method names and the callbacks below
// are looked up by name from the engine library, so they must not change.
@Keep
@SuppressWarnings("JavaJniMissingFunction")
public class TgCallsController {
  // Read by the native code when the call stops.
  protected final CallConfiguration configuration;
  protected final CallOptions options;
  private final ConnectionStateListener listener;
  private final String version;
  private long nativePtr;

  public TgCallsController(CallConfiguration configuration, CallOptions options, ConnectionStateListener listener, String version) {
    if (configuration.state.encryptionKey.length != 256)
      throw new IllegalArgumentException(Integer.toString(configuration.state.encryptionKey.length));
    this.configuration = configuration;
    this.options = options;
    this.listener = listener;
    this.version = version;
    this.nativePtr = newInstance(version, configuration, options);
    if (this.nativePtr == 0) throw new IllegalStateException("tgcalls failed to start");
  }

  private native long newInstance(String version, CallConfiguration configuration, CallOptions options);
  private native long preferredConnectionId(long ptr);
  private native String lastError(long ptr);
  private native String debugLog(long ptr);
  private native void fetchNetworkStats(long ptr, NetworkStats out);
  private native void processIncomingSignalingData(long ptr, byte[] buffer);
  private native void updateNetworkType(long ptr, int newType);
  private native void updateMicrophoneDisabled(long ptr, boolean isDisabled);
  private native void updateEchoCancellationStrength(long ptr, int strength);
  private native void updateAudioOutputGainControlEnabled(long ptr, boolean isEnabled);
  private native void destroyInstance(long ptr);

  public String getVersion() { return version; }

  public synchronized long getConnectionId() { return nativePtr != 0 ? preferredConnectionId(nativePtr) : 0; }

  public synchronized String getLastError() { return nativePtr != 0 ? lastError(nativePtr) : null; }

  public synchronized void setMicDisabled(boolean disabled) {
    options.isMicDisabled = disabled;
    if (nativePtr != 0) updateMicrophoneDisabled(nativePtr, disabled);
  }

  public synchronized void setNetworkType(int type) {
    options.networkType = type;
    if (nativePtr != 0) updateNetworkType(nativePtr, type);
  }

  public synchronized void handleIncomingSignalingData(byte[] buffer) {
    if (nativePtr != 0) processIncomingSignalingData(nativePtr, buffer);
  }

  public synchronized NetworkStats getStats() {
    NetworkStats out = new NetworkStats();
    if (nativePtr != 0) fetchNetworkStats(nativePtr, out);
    return out;
  }

  public synchronized void destroy() {
    if (nativePtr != 0) {
      destroyInstance(nativePtr);
      nativePtr = 0;
    }
  }

  // Called from native code
  @Keep protected final void handleStateChange(int state) { listener.onConnectionStateChanged(state); }
  @Keep protected final void handleSignalBarsChange(int count) { listener.onSignalBarCountChanged(count); }
  @Keep protected final void handleEmittedSignalingData(byte[] buffer) { listener.onSignallingDataEmitted(buffer); }
  @Keep protected final void handleRemoteMediaStateChange(int audioState, int videoState) { listener.onRemoteMediaStateChanged(audioState, videoState); }
  @Keep protected final void handleAudioLevelChange(float audioLevel) { }
  @Keep protected final void handleStop(NetworkStats totalStats, String debugLog) { listener.onStopped(totalStats, debugLog); }
}

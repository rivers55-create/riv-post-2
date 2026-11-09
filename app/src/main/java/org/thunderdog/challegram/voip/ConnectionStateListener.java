package org.thunderdog.challegram.voip;

public interface ConnectionStateListener {
  void onConnectionStateChanged(int newState);
  void onSignalBarCountChanged(int newCount);
  void onStopped(NetworkStats finalStats, String debugLog);
  void onRemoteMediaStateChanged(int audioState, int videoState);
  void onSignallingDataEmitted(byte[] data);
}

package org.thunderdog.challegram.voip;

import androidx.annotation.Keep;

@Keep
public class CallOptions {
  public int networkType;
  public boolean audioGainControlEnabled;
  public int echoCancellationStrength;
  public boolean isMicDisabled;

  public CallOptions(int networkType, boolean audioGainControlEnabled, int echoCancellationStrength, boolean isMicDisabled) {
    this.networkType = networkType;
    this.audioGainControlEnabled = audioGainControlEnabled;
    this.echoCancellationStrength = echoCancellationStrength;
    this.isMicDisabled = isMicDisabled;
  }
}

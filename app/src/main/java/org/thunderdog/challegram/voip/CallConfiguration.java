package org.thunderdog.challegram.voip;

import androidx.annotation.Keep;
import org.drinkless.tdlib.TdApi;

// Field names must match what the native engine reads.
@Keep
public class CallConfiguration {
  public final TdApi.CallStateReady state;
  public final boolean isOutgoing;
  public final String persistentStateFilePath;
  public final String logFilePath;
  public final String statsLogFilePath;
  public final long packetTimeoutMs;
  public final long connectTimeoutMs;
  public final int dataSavingOption;
  public final boolean forceTcp;
  public final Socks5Proxy proxy;
  public final boolean enableAcousticEchoCanceler;
  public final boolean enableNoiseSuppressor;
  public final boolean enableAutomaticGainControl;
  public final boolean enableStunMarking;
  public final boolean enableH265Encoder, enableH265Decoder;
  public final boolean enableH264Encoder, enableH264Decoder;

  public CallConfiguration(TdApi.CallStateReady state, boolean isOutgoing, String persistentStateFilePath,
                           String logFilePath, String statsLogFilePath, long packetTimeoutMs, long connectTimeoutMs,
                           int dataSavingOption, boolean forceTcp, Socks5Proxy proxy,
                           boolean enableAcousticEchoCanceler, boolean enableNoiseSuppressor, boolean enableAutomaticGainControl,
                           boolean enableStunMarking) {
    this.state = state;
    this.isOutgoing = isOutgoing;
    this.persistentStateFilePath = persistentStateFilePath;
    this.logFilePath = logFilePath;
    this.statsLogFilePath = statsLogFilePath;
    this.packetTimeoutMs = packetTimeoutMs;
    this.connectTimeoutMs = connectTimeoutMs;
    this.dataSavingOption = dataSavingOption;
    this.forceTcp = forceTcp;
    this.proxy = proxy;
    this.enableAcousticEchoCanceler = enableAcousticEchoCanceler;
    this.enableNoiseSuppressor = enableNoiseSuppressor;
    this.enableAutomaticGainControl = enableAutomaticGainControl;
    this.enableStunMarking = enableStunMarking;
    this.enableH265Encoder = true;
    this.enableH265Decoder = true;
    this.enableH264Encoder = true;
    this.enableH264Decoder = true;
  }
}

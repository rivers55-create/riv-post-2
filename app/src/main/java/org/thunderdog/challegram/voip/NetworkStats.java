package org.thunderdog.challegram.voip;

import androidx.annotation.Keep;

@Keep
public class NetworkStats {
  public long bytesSentWifi;
  public long bytesRecvdWifi;
  public long bytesSentMobile;
  public long bytesRecvdMobile;

  public NetworkStats() { }

  public NetworkStats(long bytesSentWifi, long bytesRecvdWifi, long bytesSentMobile, long bytesRecvdMobile) {
    this.bytesSentWifi = bytesSentWifi;
    this.bytesRecvdWifi = bytesRecvdWifi;
    this.bytesSentMobile = bytesSentMobile;
    this.bytesRecvdMobile = bytesRecvdMobile;
  }
}

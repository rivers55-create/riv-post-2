package org.drinkless.tdlib;

import androidx.annotation.Keep;

// Minimal copies of the TDLib call classes that the call engine reads through JNI.
// The engine only compares CONSTRUCTOR values between these classes.
@Keep
public class TdApi {
  @Keep
  public abstract static class Object {
    public abstract int getConstructor();
  }

  @Keep
  public static class CallProtocol extends Object {
    public boolean udpP2p;
    public boolean udpReflector;
    public int minLayer;
    public int maxLayer;
    public String[] libraryVersions;
    public static final int CONSTRUCTOR = -1042830667;
    public CallProtocol(boolean udpP2p, boolean udpReflector, int minLayer, int maxLayer, String[] libraryVersions) {
      this.udpP2p = udpP2p; this.udpReflector = udpReflector; this.minLayer = minLayer; this.maxLayer = maxLayer; this.libraryVersions = libraryVersions;
    }
    @Override public int getConstructor() { return CONSTRUCTOR; }
  }

  @Keep
  public abstract static class CallServerType extends Object { }

  @Keep
  public static class CallServerTypeTelegramReflector extends CallServerType {
    public byte[] peerTag;
    public boolean isTcp;
    public static final int CONSTRUCTOR = 850343189;
    public CallServerTypeTelegramReflector(byte[] peerTag, boolean isTcp) { this.peerTag = peerTag; this.isTcp = isTcp; }
    @Override public int getConstructor() { return CONSTRUCTOR; }
  }

  @Keep
  public static class CallServerTypeWebrtc extends CallServerType {
    public String username;
    public String password;
    public boolean supportsTurn;
    public boolean supportsStun;
    public static final int CONSTRUCTOR = 1250622821;
    public CallServerTypeWebrtc(String username, String password, boolean supportsTurn, boolean supportsStun) {
      this.username = username; this.password = password; this.supportsTurn = supportsTurn; this.supportsStun = supportsStun;
    }
    @Override public int getConstructor() { return CONSTRUCTOR; }
  }

  @Keep
  public static class CallServer extends Object {
    public long id;
    public String ipAddress;
    public String ipv6Address;
    public int port;
    public CallServerType type;
    public static final int CONSTRUCTOR = 1865932695;
    public CallServer(long id, String ipAddress, String ipv6Address, int port, CallServerType type) {
      this.id = id; this.ipAddress = ipAddress; this.ipv6Address = ipv6Address; this.port = port; this.type = type;
    }
    @Override public int getConstructor() { return CONSTRUCTOR; }
  }

  @Keep
  public static class CallStateReady extends Object {
    public CallProtocol protocol;
    public CallServer[] servers;
    public String config;
    public byte[] encryptionKey;
    public String[] emojis;
    public boolean allowP2p;
    public boolean isGroupCallSupported;
    public String customParameters;
    public static final int CONSTRUCTOR = 731619651;
    public CallStateReady(CallProtocol protocol, CallServer[] servers, String config, byte[] encryptionKey, String[] emojis, boolean allowP2p, boolean isGroupCallSupported, String customParameters) {
      this.protocol = protocol; this.servers = servers; this.config = config; this.encryptionKey = encryptionKey;
      this.emojis = emojis; this.allowP2p = allowP2p; this.isGroupCallSupported = isGroupCallSupported; this.customParameters = customParameters;
    }
    @Override public int getConstructor() { return CONSTRUCTOR; }
  }
}

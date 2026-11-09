package org.thunderdog.challegram;

import androidx.annotation.Keep;

// Native helpers exported by the call engine library.
@Keep
public final class N {
  private N() { }
  public static native String[] getTgCallsVersions();
  public static native String toHexString(byte[] array);
}

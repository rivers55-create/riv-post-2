// Riv Post: loads only Telegram's call engine (tgcalls + WebRTC).
#include <jni.h>

int tgvoipOnJNILoad(JavaVM *vm, JNIEnv *env);

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = 0;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) return -1;
    tgvoipOnJNILoad(vm, env);
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
}

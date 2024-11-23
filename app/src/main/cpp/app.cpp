#include <jni.h>

// Write C++ code here.
//
// Do not forget to dynamically load the C++ library into your application.
//
// For instance,
//
// In MainActivity.java:
//    static {
//       System.loadLibrary("app");
//    }
//
// Or, in MainActivity.kt:
//    companion object {
//      init {
//         System.loadLibrary("app")
//      }
//    }

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_app_NativeCode_getVal(JNIEnv *env, jobject thiz) {
    return 2;
}
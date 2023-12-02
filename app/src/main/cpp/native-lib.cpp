#include <jni.h>
#include "SignalDetector.cpp"

extern "C"
JNIEXPORT void JNICALL
Java_com_scscscc_ranging_MyRecorder_cDetectSignalRaw(
        JNIEnv *env, jclass thiz, jdoubleArray data, jdoubleArray reference, jint TB_W0,
        jdouble TB_simThreshold, jintArray ret1, jdoubleArray ret2
) {
    // from java type to c type
    jdouble *data_ = (env)->GetDoubleArrayElements(data, nullptr);
    jdouble *reference_ = (env)->GetDoubleArrayElements(reference, nullptr);

    SignalDetector::SignalInfo ret = SignalDetector::detectSignal(data_,
                                                                  (env)->GetArrayLength(data),
                                                                  reference_,

                                                                  (env)->GetArrayLength(
                                                                          reference), TB_W0,
                                                                  TB_simThreshold);

    env->ReleaseDoubleArrayElements(data, data_, JNI_ABORT);
    env->ReleaseDoubleArrayElements(reference, reference_, JNI_ABORT);

    jint *ret1_ = (env)->GetIntArrayElements(ret1, nullptr);
    jdouble *ret2_ = (env)->GetDoubleArrayElements(ret2, nullptr);
    ret2_[0] = ret.confidence;
    ret1_[0] = ret.status;
    for (int i = 0; i < 3; i++) {
        ret1_[i + 1] = ret.position[i];
        ret2_[i + 1] = ret.similarity[i];
    }

    env->ReleaseIntArrayElements(ret1, ret1_, 0);
    env->ReleaseDoubleArrayElements(ret2, ret2_, 0);

}


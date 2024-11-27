#include <jni.h>
#include <dbscan.hpp>

struct AABB{
    float min[3] = {
            std::numeric_limits<float>::max(),
            std::numeric_limits<float>::max(),
            std::numeric_limits<float>::max()
    };
    float max[3] = {
            std::numeric_limits<float>::lowest(),
            std::numeric_limits<float>::lowest(),
            std::numeric_limits<float>::lowest()
    };
    void update(float x, float y, float z){
        if(x<min[0]) min[0] = x;
        if(y<min[1]) min[1] = y;
        if(z<min[2]) min[2] = z;
        if(x>max[0]) max[0] = x;
        if(y>max[1]) max[1] = y;
        if(z>max[2]) max[2] = z;
    }
};

struct J_AABB{
    jclass m_class_id;
    jmethodID m_update_id;
    jobject m_aabb;
    jfieldID m_sx, m_sy, m_sz, m_bx, m_by, m_bz;

    explicit J_AABB(JNIEnv *env){
        m_class_id = env->FindClass("com/example/app/AABB");
        m_update_id = env->GetMethodID(m_class_id,"update", "(FFF)V");
        m_aabb = env->NewObject( m_class_id, env->GetMethodID(m_class_id,"<init>", "()V"));
        m_sx = env->GetFieldID(m_class_id,"sx","F");
        m_sy = env->GetFieldID(m_class_id,"sy","F");
        m_sz = env->GetFieldID(m_class_id,"sz","F");
        m_bx = env->GetFieldID(m_class_id,"bx","F");
        m_by = env->GetFieldID(m_class_id,"by","F");
        m_bz = env->GetFieldID(m_class_id,"bz","F");
    }
    void update(JNIEnv *env, jfloat x, jfloat y, jfloat z){
        env->CallVoidMethod(m_aabb, m_update_id, x, y, z);
    }
    void set_sx(JNIEnv *env, jfloat sx){
        env->SetFloatField(m_aabb,m_sx,sx);
    }
    void set_sy(JNIEnv *env, jfloat sy){
        env->SetFloatField(m_aabb,m_sy,sy);
    }
    void set_sz(JNIEnv *env, jfloat sz){
        env->SetFloatField(m_aabb,m_sz,sz);
    }
    void set_bx(JNIEnv *env, jfloat bx){
        env->SetFloatField(m_aabb,m_bx,bx);
    }
    void set_by(JNIEnv *env, jfloat by){
        env->SetFloatField(m_aabb,m_by,by);
    }
    void set_bz(JNIEnv *env, jfloat bz){
        env->SetFloatField(m_aabb,m_bz,bz);
    }
    void set_aabb(JNIEnv *env, AABB aabb){
        set_sx(env, aabb.min[0]);
        set_sy(env, aabb.min[1]);
        set_sz(env, aabb.min[2]);
        set_bx(env, aabb.max[0]);
        set_by(env, aabb.max[1]);
        set_bz(env, aabb.max[2]);
    }
};


extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_example_app_NativeCode_cluster(JNIEnv *env, jobject thiz, jobject point_buffer,
                                        jint count, jfloat eps, jint nPts) {
    point3 *p_buf = (point3*)env->GetDirectBufferAddress(point_buffer);
    if( p_buf == nullptr) return nullptr;
    auto p_span = std::span{p_buf, (size_t)count};
    auto clusters = dbscan(p_span, eps, nPts);

    jobjectArray aabb_arr = env->NewObjectArray(
            (jsize)clusters.size(),
            env->FindClass("com/example/app/AABB"),
            nullptr);

    for(int cluster_i=0;cluster_i<clusters.size();++cluster_i){
        auto &cluster = clusters[cluster_i];
        AABB aabb{};
        for(auto ind : cluster){
            aabb.update(p_buf[ind].x, p_buf[ind].y, p_buf[ind].z);
        }
        J_AABB j_aabb{env};
        j_aabb.set_aabb(env,aabb);
        env->SetObjectArrayElement(aabb_arr, cluster_i, j_aabb.m_aabb);
    }

    return aabb_arr;
}
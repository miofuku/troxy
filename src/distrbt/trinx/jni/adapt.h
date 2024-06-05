#ifndef _JNI_ADAPT_H_
#define _JNI_ADAPT_H_

#include <jni.h>
#include "counter/trinx_types.h"

namespace java
{

class ByteArray
{

    JNIEnv      *m_env;
    jbyteArray   m_array;
    uint8_t     *m_elems;
    int          m_mode;

public:

    ByteArray()
    {
        init( NULL, NULL, 0 );
    }

    ByteArray(JNIEnv *env, jbyteArray array, int mode)
    {
        init( env, array, mode );
    }

    void init(JNIEnv *env, jbyteArray array, int mode)
    {
        m_env   = env;
        m_array = array;
        m_elems = array==NULL ? NULL : (uint8_t *) env->GetByteArrayElements( array, NULL );
        m_mode  = mode;
    }

    void release()
    {
        if( m_elems!=NULL )
            m_env->ReleaseByteArrayElements( m_array, (jbyte *) m_elems, m_mode );

        m_elems = NULL;
    }

    ~ByteArray()
    {
        release();
    }

    uint8_t *elements()
    {
        return m_elems;
    }

};


class ByteArrayBlock
{

    ByteArray    m_array;
    uint8_t     *m_pointer;
    size_t       m_size;

public:

    ByteArrayBlock(JNIEnv *env, jbyteArray array, int arraymode, uint32_t offset, size_t size) :
            m_array( env, array, arraymode ),
            m_pointer( m_array.elements()+offset ),
            m_size( size )
    {
    }


    uint8_t *GetPointer()
    {
        return m_pointer;
    }


    size_t GetSize()
    {
        return m_size;
    }

};


class ByteBuffer : public ByteArrayBlock
{

public:

    ByteBuffer(JNIEnv *env, jobject obj, int arraymode) :
            ByteArrayBlock( env, array( env, obj ), arraymode,
                            arrayOffset( env, obj )+position( env, obj ),
                            remaining( env, obj ) )
    {
    }


private:

    static jmethodID    s_array;
    static jmethodID    s_offset;
    static jmethodID    s_position;
    static jmethodID    s_remaining;

public:

    static void InitJNI(JNIEnv *env)
    {
        jclass cls = env->FindClass( "java/nio/ByteBuffer" );
        s_array     = env->GetMethodID( cls, "array", "()[B" );
        s_offset    = env->GetMethodID( cls, "arrayOffset", "()I" );
        s_position  = env->GetMethodID( cls, "position", "()I" );
        s_remaining = env->GetMethodID( cls, "remaining", "()I" );
    }

    static jbyteArray array(JNIEnv *env, jobject obj)
    {
        return (jbyteArray) env->CallObjectMethod( obj, s_array );
    }

    static uint32_t arrayOffset(JNIEnv *env, jobject obj)
    {
        return (uint32_t) env->CallIntMethod( obj, s_offset );
    }

    static uint32_t position(JNIEnv *env, jobject obj)
    {
        return (uint32_t) env->CallIntMethod( obj, s_position );
    }

    static uint32_t remaining(JNIEnv *env, jobject obj)
    {
        return (uint32_t) env->CallIntMethod( obj, s_remaining );
    }

};


class Data : public ByteArrayBlock
{

public:

    Data(JNIEnv *env, jobject obj, int arraymode) :
            ByteArrayBlock( env, array( env, obj ), arraymode,
                            arrayOffset( env, obj ), size( env, obj ) )
    {
    }


private:

    static jmethodID    s_array;
    static jmethodID    s_offset;
    static jmethodID    s_size;

public:

    static void InitJNI(JNIEnv *env)
    {
        jclass cls = env->FindClass( "reptor/distrbt/common/data/Data" );
        s_array  = env->GetMethodID( cls, "array", "()[B" );
        s_offset = env->GetMethodID( cls, "arrayOffset", "()I" );
        s_size   = env->GetMethodID( cls, "size", "()I" );
    }

    static jbyteArray array(JNIEnv *env, jobject obj)
    {
        return (jbyteArray) env->CallObjectMethod( obj, s_array );
    }

    static uint32_t arrayOffset(JNIEnv *env, jobject obj)
    {
        return (uint32_t) env->CallIntMethod( obj, s_offset );
    }

    static uint32_t size(JNIEnv *env, jobject obj)
    {
        return (uint32_t) env->CallIntMethod( obj, s_size );
    }

};

}

#endif /* _JNI_ADAPT_H_ */

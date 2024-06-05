#include <stdexcept>
#include <iostream>
#include <vector>
#include "reptor_distrbt_certify_trusted_JniTrinxImplementation.h"
#include "adapt.h"
#include "counter/trinx_cmds.h"

#ifdef SGX
    #include "sgx/untrusted/SgxTrinx.h"
#elif defined CASH
    #include "cash/counter/CASHCounter.h"
#elif defined DUMMYCOUNTER
    #include "counter/DummyTrinx.h"
#else
    #include "counter/Trinx.h"
#endif


struct tssrec_t
{
    Trinx                        trinx;
    std::vector<java::ByteArray> dataarrays;

    tssrec_t(tssid_t tssid, ctrno_t ncounters, const uint8_t *key, size_t keylen) :
            trinx( tssid, ncounters, key, keylen )
    {
    }
};


JNIEXPORT jlong JNICALL Java_reptor_distrbt_certify_trusted_JniTrinxImplementation_nativeCreate
        (JNIEnv *env, jclass, jstring path, jshort tmid, jint ncounters, jbyteArray key)
{

    #ifdef SGX
        static bool is_init = false;

        if( !is_init )
        {
            const char *cpath = env->GetStringUTFChars( path, JNI_FALSE );
            Trinx::Init( cpath );
            env->ReleaseStringUTFChars( path, cpath );

            is_init = true;
        }
    #endif

    int      ckeylen  = env->GetArrayLength( key );
    uint8_t *ckeybuf  = (uint8_t *) env->GetByteArrayElements( key, JNI_FALSE );

    jlong eid = (long) new tssrec_t( tmid, ncounters, ckeybuf, ckeylen );

    env->ReleaseByteArrayElements( key, (jbyte *) ckeybuf, JNI_ABORT );

    return eid;
}


JNIEXPORT void JNICALL Java_reptor_distrbt_certify_trusted_JniTrinxImplementation_nativeTerminate
        (JNIEnv *env, jclass, jlong eid)
{
    delete (Trinx *) eid;
}


JNIEXPORT jint JNICALL
        Java_reptor_distrbt_certify_trusted_JniTrinxImplementation_nativeNumberOfCounters(JNIEnv *, jclass, jlong eid)
{
    return ((Trinx *) eid)->GetNumberOfCounters();
}


JNIEXPORT jint JNICALL
        Java_reptor_distrbt_certify_trusted_JniTrinxImplementation_nativeCounterCertificateSize(JNIEnv *, jclass, jlong eid)
{
    return ((Trinx *) eid)->GetCounterCertificateSize();
}


JNIEXPORT jint JNICALL
        Java_reptor_distrbt_certify_trusted_JniTrinxImplementation_nativeMacCertificateSize(JNIEnv *, jclass, jlong eid)
{
    return ((Trinx *) eid)->GetMacCertificateSize();
}


JNIEXPORT void JNICALL
        Java_reptor_distrbt_certify_trusted_JniTrinxImplementation_nativeTouch(JNIEnv *, jclass, jlong eid)
{
    ((Trinx *) eid)->Touch();
}


JNIEXPORT jbyte JNICALL
        Java_reptor_distrbt_certify_trusted_JniTrinxImplementation_nativeExecuteCommand(
                JNIEnv *env, jclass, jlong eid, jint cmdid,
                jbyteArray msg, jint msgoff, jint msgsize, jbyteArray cert, jint certoff,
                jshort tssid, jint ctrno, jlong highval, jlong lowval, jlong prevhigh, jlong prevlow)
{
    java::ByteArray msgarray  = java::ByteArray( env, msg, cert==NULL ? 0 : JNI_ABORT );
    java::ByteArray certarray = java::ByteArray( env, cert, 0 );

    certification_command<verify_continuing_counter_body> cmd;

    switch( cmdid )
    {
    case VERIFY_CONTINUING_CERTIFICATE_ID:
        cmd.body.prev.high = prevhigh;
        cmd.body.prev.low  = prevlow;
    case CREATE_CONTINUING_CERTIFICATE_ID:
    case VERIFY_INDEPENDENT_CERTIFICATE_ID:
    case CREATE_INDEPENDENT_CERTIFICATE_ID:
        cmd.body.ctrval.high = highval;
        cmd.body.ctrval.low  = lowval;
        cmd.body.ctrno       = ctrno;
    case VERIFY_TMAC_CERTIFICATE_ID:
    case CREATE_TMAC_CERTIFICATE_ID:
        cmd.body.tssid       = tssid;
    }

    cmd.type( (cmdid_t) cmdid )
       .message( msgarray.elements() + msgoff, msgsize, ( cert==NULL ? msgarray.elements() : certarray.elements() ) + certoff );

    ((Trinx *) eid)->ExecuteCommand( &cmd );

    return cmd.is_verification() ? cmd.get_result() : cmdres_t::NONE;
}


JNIEXPORT void JNICALL
        Java_reptor_distrbt_certify_trusted_JniTrinxImplementation_nativeExecuteSerializedCommand(
                JNIEnv *env, jclass, jlong eid,
                jbyteArray buf, jint bufoff, jbyteArray msg, jbyteArray cert)
{
    java::ByteArray bufarray  = java::ByteArray( env, buf, 0 );
    java::ByteArray msgarray  = java::ByteArray( env, msg, cert==NULL ? 0 : JNI_ABORT );
    java::ByteArray certarray = java::ByteArray( env, cert, 0 );

    certification_header &cmd = *(certification_header *) ( bufarray.elements() + bufoff );
    cmd.init_adjacent_body();
    cmd.msgdata.ptr  = msgarray.elements() + cmd.msgoff;
    cmd.msgoff       = 0;
    cmd.certdata.ptr = ( cert==NULL ? msgarray.elements() : certarray.elements() ) + cmd.certoff;
    cmd.certoff      = 0;

    ((Trinx *) eid)->ExecuteCommand( &cmd );
}


JNIEXPORT void JNICALL
        Java_reptor_distrbt_certify_trusted_JniTrinxImplementation_nativeExecuteBatch(
                JNIEnv *env, jclass, jlong eid, jbyteArray buf, jint bufoff, jint ndata, jobjectArray data)
{
    tssrec_t *tssrec = (tssrec_t *) eid;

    java::ByteArray bufarray  = java::ByteArray( env, buf, 0 );

    if( ndata>0 )
    {
        tssrec->dataarrays.reserve( ndata );

        for( int i=0; i<ndata; i++ )
        {
            jbyteArray elem = (jbyteArray) env->GetObjectArrayElement( data, i );
            tssrec->dataarrays.emplace_back( env, elem, 0 );
        }
    }

    uint8_t *batch = bufarray.elements() + bufoff;
    uint8_t *cmd   = batch + sizeof( cmdid_t );

    while( *((cmdid_t *) cmd)!=EMPTY_ID )
    {
        certification_header *hdr = (certification_header *) cmd;
        hdr->init_adjacent_body();

        hdr->msgdata.ptr  = tssrec->dataarrays[ hdr->msgdata.pointer_value ].elements();
        hdr->certdata.ptr = tssrec->dataarrays[ hdr->certdata.pointer_value ].elements();

        cmd += sizeof(certification_header) + ((certification_header *) cmd)->bodysize;
    }

    ((Trinx *) eid)->ExecuteCommand( batch );

    if( ndata>0 )
        tssrec->dataarrays.clear();
}


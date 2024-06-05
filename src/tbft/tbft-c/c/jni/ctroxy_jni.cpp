/**
 * @author weichbr
 */

#include <cstdio>
#include <cassert>
#include "ctroxy_jni.h"
#include "../common/debug.h"
#include <jni.h>
#include "reptor_tbftc_ctroxy_CTroxy.h"
#include "../common/CTroxy.h"
#include "../native/native_impl.h"
#include "../common/ClientResult.h"
#include "../sgx/untrusted/untrusted_impl.h"

static struct {
	bool is_initialized;

	jclass thr_cls;
	jclass tnr_cls;
	jclass tcr_cls;
	jclass b_cls;
	jclass bb_cls;

	jmethodID thr_set_remep;
	jmethodID thr_set_isfinished;

	jmethodID tnr_set_canprocin;
	jmethodID tnr_set_reqoutbufsize;

	jmethodID tcr_set_lastinv;
	jmethodID tcr_set_reqmsgbufsize;
	jmethodID tcr_get_messagebuf;

	jmethodID bb_array_mid;
	jmethodID b_position_mid;
	jmethodID b_remaining_mid;
	jmethodID b_set_position_mid;
} jni_data = {
		false,
        NULL, NULL, NULL, NULL, NULL,
        NULL, NULL,
        NULL, NULL,
        NULL, NULL, NULL,
        NULL, NULL, NULL, NULL,
};


static void copyHandshakeResult(JNIEnv *env, jobject hsresstr, handshake_result_t &hsres)
{
	debugE("");

	assert(hsresstr != 0);

	env->CallVoidMethod(hsresstr, jni_data.thr_set_remep, (jshort)hsres.remote_id, (jshort)hsres.network_id);
	env->CallVoidMethod(hsresstr, jni_data.thr_set_isfinished, (jboolean)hsres.is_finished);
	env->CallVoidMethod(hsresstr, jni_data.tnr_set_canprocin, (jint)hsres.can_process_inbound);
	env->CallVoidMethod(hsresstr, jni_data.tnr_set_reqoutbufsize, (jint)hsres.req_outbound_buffer_size);

	debugL("");
}

static void copyClientResult(JNIEnv *env, jobject cresstr, client_result_t &cres, bool copylastinv = true)
{
	debugE("");

	assert(cresstr != 0);

	if (copylastinv)
		env->CallVoidMethod(cresstr, jni_data.tcr_set_lastinv, (jlong)cres.lastinv);
	env->CallVoidMethod(cresstr, jni_data.tcr_set_reqmsgbufsize, (jint)cres.reqmsgbuf);
	env->CallVoidMethod(cresstr, jni_data.tnr_set_canprocin, (jint)cres.can_process_inbound);
	env->CallVoidMethod(cresstr, jni_data.tnr_set_reqoutbufsize, (jint)cres.req_outbound_buffer_size);

	debugL("");
}


JNIEXPORT jlong JNICALL Java_reptor_tbftc_ctroxy_CTroxy_initialize(JNIEnv *env, jobject thiz, jbyte repno, jint nhshandls, jint nclients, jint clinooff, jboolean distcontacts, jint invwindow, jint verifiers, jboolean usessl, jbyteArray cert, jbyteArray key)
{
	debugE("");

	// Initialize JNI functions
	if (!jni_data.is_initialized)
	{
		jni_data.thr_cls = env->FindClass("reptor/tbft/TroxyHandshakeResults");
		jni_data.thr_set_remep = env->GetMethodID(jni_data.thr_cls, "setRemoteEndpoint", "(SS)V");
		jni_data.thr_set_isfinished = env->GetMethodID(jni_data.thr_cls, "isFinished", "(Z)V");

		jni_data.tnr_cls = env->FindClass("reptor/tbft/TroxyNetworkResults");
		jni_data.tnr_set_canprocin = env->GetMethodID(jni_data.tnr_cls, "canProcessInboundData", "(I)V");
		jni_data.tnr_set_reqoutbufsize = env->GetMethodID(jni_data.tnr_cls, "setRequiredOutboundBufferSize", "(I)V");

		jni_data.tcr_cls = env->FindClass("reptor/tbft/TroxyClientResults");
		jni_data.tcr_set_lastinv = env->GetMethodID(jni_data.tcr_cls, "setLastFinishedInvocation", "(J)V");
		jni_data.tcr_set_reqmsgbufsize = env->GetMethodID(jni_data.tcr_cls, "setRequiredMessageBufferSize", "(I)V");
		jni_data.tcr_get_messagebuf = env->GetMethodID(jni_data.tcr_cls, "getMessageBuffer", "()Ljava/nio/ByteBuffer;");

		jni_data.b_cls = env->FindClass("java/nio/Buffer");
		jni_data.bb_cls = env->FindClass("java/nio/ByteBuffer");
		jni_data.bb_array_mid = env->GetMethodID(jni_data.bb_cls, "array", "()[B");
		jni_data.b_position_mid = env->GetMethodID(jni_data.b_cls, "position", "()I");
		jni_data.b_set_position_mid = env->GetMethodID(jni_data.b_cls, "position", "(I)Ljava/nio/Buffer;");
		jni_data.b_remaining_mid = env->GetMethodID(jni_data.b_cls, "remaining", "()I");

		jni_data.is_initialized = true;
	}


	troxy_conf_t conf;
	conf.replica_number = (uint8_t) repno;
	conf.client_number_offset = (uint32_t) clinooff;
	conf.distributed_contacts = distcontacts;
	conf.use_app_handshake = false;
	conf.use_ssl = usessl;
	if (usessl)
	{
		conf.certarr = (uint8_t *) env->GetByteArrayElements(cert, NULL);
		conf.certlen = (size_t) env->GetArrayLength(cert);
		conf.keyarr = (uint8_t *) env->GetByteArrayElements(key, NULL);
		conf.keylen = (size_t) env->GetArrayLength(key);
	}
	//conf.cert_key_path = (char *)"/opt/natfit/cert.key";
	//conf.cert_file_path = (char *)"/opt/natfit/cert.pem";
	conf.max_connections = (uint32_t)nclients;
	conf.max_concurrent_handshakes = (uint32_t)nhshandls;
	conf.invocation_window = (uint32_t) invwindow;
	conf.verifiers = (uint32_t) verifiers;

	debugL("");

	#ifndef SGX
	return (jlong)new NativeTroxy(conf);
	#else
	return (jlong)new SGXTroxy(conf);
	#endif
}

JNIEXPORT void JNICALL Java_reptor_tbftc_ctroxy_CTroxy_resetHandshakeNative(JNIEnv *env, jobject thiz, jlong ptroxy, jshort hsno, jboolean clear, jobject thr)
{
	debugE("(%ld, %d, %s, %p)", ptroxy, hsno, clear ? "true" : "false", thr);
	assert(ptroxy != 0);
	assert(hsno >= 0);

	CTroxy *troxy = (CTroxy *)ptroxy;
	handshake_result_t hsres;

	troxy->reset_handshake(hsres, (uint16_t)hsno, clear);
	copyHandshakeResult(env, thr, hsres);
	debugL("");
}

JNIEXPORT void JNICALL Java_reptor_tbftc_ctroxy_CTroxy_acceptNative(JNIEnv *env, jobject thiz, jlong ptroxy, jshort hsno, jobject thr)
{
	debugE("(%ld, %d, %p)", ptroxy, hsno, thr);
	assert(ptroxy != 0);
	assert(hsno >= 0);

	CTroxy *troxy = (CTroxy *)ptroxy;
	handshake_result_t hsres;

	troxy->accept(hsres, (uint16_t)hsno);

	copyHandshakeResult(env, thr, hsres);

	debugL();
}

JNIEXPORT jint JNICALL Java_reptor_tbftc_ctroxy_CTroxy_processHandshakeInboundDataNative(JNIEnv *env, jobject thiz, jlong ptroxy, jshort hsno, jobject src, jint pos, jint rem, jobject thr)
{
	debugE("");

	assert(ptroxy != 0);
	assert(hsno >= 0);

	CTroxy *troxy = (CTroxy *)ptroxy;
	handshake_result_t hsres;

	uint8_t *srcbuf = (uint8_t *)env->GetDirectBufferAddress(src);

	buffer_t buf;
	buf.arr = srcbuf;
	buf.offset = (uint32_t) pos;
	buf.length = (uint32_t) rem;

	troxy->process_handshake_inbound_data(hsres, (uint16_t) hsno, buf);

	copyHandshakeResult(env, thr, hsres);

	debugL("");

	return buf.offset;
}

JNIEXPORT jint JNICALL Java_reptor_tbftc_ctroxy_CTroxy_retrieveHandshakeOutboundData(JNIEnv *env, jobject thiz, jlong ptroxy, jshort hsno, jobject dest, jint pos, jint rem, jobject thr)
{
	debugE("(%ld, %d, %p, %p)", ptroxy, hsno, dest, thr);

	assert(ptroxy != 0);
	assert(hsno >= 0);

	CTroxy *troxy = (CTroxy *)ptroxy;
	handshake_result_t hsres;

	uint8_t *destbuf = (uint8_t *)env->GetDirectBufferAddress(dest);

	buffer_t buf;
	buf.arr = destbuf;
	buf.offset = (uint32_t) pos;
	buf.length = (uint32_t) rem;

	troxy->retrieve_handshake_outbound_data(hsres, (uint16_t)hsno, buf);

	copyHandshakeResult(env, thr, hsres);

	debugL("(%ld, %d, %p, %p)", ptroxy, hsno, dest, thr);

	return buf.offset;
}

JNIEXPORT void JNICALL Java_reptor_tbftc_ctroxy_CTroxy_saveStateNative(JNIEnv *env, jobject thiz, jlong ptroxy, jshort hsno, jobject tcr)
{
	debugE("(%ld, %d)", ptroxy, hsno);

	assert(ptroxy != 0);
	assert(hsno >= 0);

	CTroxy *troxy = (CTroxy *)ptroxy;

	troxy->save_state(hsno);
	client_result_t cres;
	cres.reqmsgbuf = INT_MAX;
	cres.lastinv = 0;
	cres.req_outbound_buffer_size = INT_MAX;
	cres.can_process_inbound = sink_status_t::CAN_PROCESS;

	copyClientResult(env, tcr, cres);

	debugL("");
}

JNIEXPORT void JNICALL Java_reptor_tbftc_ctroxy_CTroxy_openNative(JNIEnv *env, jobject thiz, jlong ptroxy, jshort clino, jobject tcr)
{
	debugE("(%ld, %d, %p)", ptroxy, clino, tcr);

	CTroxy *troxy = (CTroxy *)ptroxy;

	client_result_t cres;

	troxy->open(cres, (uint16_t)clino);

	copyClientResult(env, tcr, cres);

	debugL("");
}

JNIEXPORT void JNICALL Java_reptor_tbftc_ctroxy_CTroxy_initClientNative(JNIEnv *env, jobject thiz, jlong ptroxy, jshort clino)
{
	debugE("(%ld, %d)", ptroxy, clino);

	CTroxy *troxy = (CTroxy *)ptroxy;

	troxy->init_client((uint16_t)clino, nullptr);

	debugL("");
}

JNIEXPORT jint JNICALL Java_reptor_tbftc_ctroxy_CTroxy_processClientInboundDataNative(JNIEnv *env, jobject thiz, jlong ptroxy, jshort clino, jobject src, jint pos, jint rem, jobject dest, jint destpos, jint destrem, jobject tcr)
{
	debugE("(%ld, %d, %p, %p, %p)", ptroxy, clino, src, dest, tcr);

	CTroxy *troxy = (CTroxy *)ptroxy;

	client_result_t cres;


	uint8_t *srcbuf = troxy->getPointer((uint16_t) clino, 0);
	if (__builtin_expect(srcbuf == nullptr, false))
	{
		srcbuf = (uint8_t *)env->GetDirectBufferAddress(src);
		troxy->savePointer((uint16_t) clino, 0, srcbuf);
	}

	uint8_t *destbuf = troxy->getPointer((uint16_t) clino, 1);
	if (__builtin_expect(destbuf == nullptr, false))
	{
		destbuf = (uint8_t *)env->GetDirectBufferAddress(dest);
		troxy->savePointer((uint16_t) clino, 1, destbuf);
	}

	buffer_t _src;
	_src.arr = srcbuf;
	_src.offset = (uint32_t) pos;
	_src.length = (uint32_t) rem;

	buffer_t _dest;
	_dest.arr = destbuf;
	_dest.offset = (uint32_t) destpos;
	_dest.length = (uint32_t) destrem;

	try
	{
		troxy->process_client_inbound_data(cres, (uint16_t) clino, _src, _dest);
	}
	catch (IOException& e)
	{
		env->CallVoidMethod(dest, jni_data.b_set_position_mid, _dest.offset);
		env->ThrowNew(env->FindClass("java/io/IOException"), "Connection closed");
	}

	env->CallVoidMethod(dest, jni_data.b_set_position_mid, _dest.offset);

	copyClientResult(env, tcr, cres);

	debugL("");

	return _src.offset;
}

JNIEXPORT jint JNICALL Java_reptor_tbftc_ctroxy_CTroxy_retrieveClientOutboundDataNative(JNIEnv *env, jobject thiz, jlong ptroxy, jshort clino, jobject dest, jint pos, jint rem, jobject tcr)
{
	debugE("");

	CTroxy *troxy = (CTroxy *)ptroxy;

	client_result_t cres;

	uint8_t *destbuf = troxy->getPointer((uint16_t) clino, 2);
	if (__builtin_expect(destbuf == nullptr, false))
	{
		destbuf = (uint8_t *)env->GetDirectBufferAddress(dest);
		troxy->savePointer((uint16_t) clino, 2, destbuf);
	}
	buffer_t _dest;
	_dest.arr = destbuf;
	_dest.offset = (uint32_t) pos;
	_dest.length = (uint32_t) rem;

	troxy->retrieve_client_outbound_data(cres, (uint16_t) clino, _dest);

	copyClientResult(env, tcr, cres, true);

	debugL("");

	return _dest.offset;
}

JNIEXPORT jint JNICALL Java_reptor_tbftc_ctroxy_CTroxy_handleForwardedRequestNative(JNIEnv *env, jobject thiz, jlong ptroxy, jshort clino, jbyteArray request, jint pos, jint rem, jobject dest, jint destpos, jint destrem, jobject tcr)
{
	debugE("");
	CTroxy *troxy = (CTroxy *)ptroxy;

	client_result_t cres;

	uint8_t *destbuf = troxy->getPointer((uint16_t) clino, 1);
	if (__builtin_expect(destbuf == nullptr, false))
	{
		destbuf = (uint8_t *)env->GetDirectBufferAddress(dest);
		troxy->savePointer((uint16_t) clino, 1, destbuf);
	}

	buffer_t cliresult;
	cliresult.arr = destbuf;
	cliresult.offset = (uint32_t) destpos;
	cliresult.length = (uint32_t) destrem;

	cres.resultbuf = &cliresult;

	verification_result_t result;

	buffer_t _req;
	_req.arr = (uint8_t *) env->GetByteArrayElements(request, NULL);
	_req.offset = (uint32_t) pos;
	_req.length = (uint32_t) rem;

	troxy->handle_forwarded_request(cres, result, (uint16_t) clino, _req);

	if (__builtin_expect(result == verification_result_t::FAIL, false))
	{
		env->ThrowNew(env->FindClass("reptor/distrbt/com/VerificationException"), "Failed to verify request");
	}

	copyClientResult(env, tcr, cres);
	debugL("");
	return cliresult.offset;
}

JNIEXPORT jint JNICALL Java_reptor_tbftc_ctroxy_CTroxy_handleRequestExecutedNative(JNIEnv *env, jobject thiz, jlong ptroxy, jshort clino, jlong invno, jobject src, jint srcpos, jint srcrem, jboolean replyfull, jobject dest, jint destpos, jint destrem, jobject tcr)
{
	debugE("");
	CTroxy *troxy = (CTroxy *)ptroxy;

	client_result_t cres;

	uint8_t *destbuf = troxy->getPointer((uint16_t) clino, 1);
	if (__builtin_expect(destbuf == nullptr, false))
	{
		destbuf = (uint8_t *)env->GetDirectBufferAddress(dest);
		troxy->savePointer((uint16_t) clino, 1, destbuf);
	}

	buffer_t result;
	result.arr = destbuf;
	result.offset = (uint32_t) destpos;
	result.length = (uint32_t) destrem;

	cres.resultbuf = &result;

	jbyteArray barr = (jbyteArray) env->CallObjectMethod(src, jni_data.bb_array_mid);

	buffer_t _rep;
	_rep.arr = (uint8_t *) env->GetByteArrayElements(barr, NULL);
	_rep.offset = (uint32_t) srcpos;
	_rep.length = (uint32_t) srcrem;

	troxy->handle_request_executed(cres, (uint16_t) clino, (uint64_t) invno, _rep, replyfull);

	copyClientResult(env, tcr, cres);
	debugL("");
	return result.offset;
}

JNIEXPORT jint JNICALL Java_reptor_tbftc_ctroxy_CTroxy_handleReplyNative(JNIEnv *env, jobject thiz, jlong ptroxy, jshort clino, jbyteArray reply, jint pos, jint rem, jobject tcr, jint destpos, jint destrem)
{
	debugE("");
	CTroxy *troxy = (CTroxy *)ptroxy;

	client_result_t cres;

	buffer_t _rep;
	_rep.arr = (uint8_t *) env->GetByteArrayElements(reply, NULL);
	_rep.offset = (uint32_t) pos;
	_rep.length = (uint32_t) rem;

	uint8_t *destbuf = troxy->getPointer((uint16_t) clino, 1);

	buffer_t result;
	result.arr = destbuf;
	result.offset = (uint32_t) destpos;
	result.length = (uint32_t) destrem;

	cres.resultbuf = &result;

	troxy->handle_reply(cres, (uint16_t) clino, _rep);

	copyClientResult(env, tcr, cres);

	debugL("");
	return result.offset;
}

JNIEXPORT void JNICALL Java_reptor_tbftc_ctroxy_CTroxy_handleRepliesNative(JNIEnv *env, jobject thiz, jlong ptroxy, jshort clino, jobject replies, jint pos, jint rem, jobject tcr)
{
	debugE("");
	CTroxy *troxy = (CTroxy *)ptroxy;

	client_result_t cres;

	buffer_t _rep;
	_rep.arr = (uint8_t *) env->GetDirectBufferAddress(replies);
	_rep.offset = (uint32_t) pos;
	_rep.length = (uint32_t) rem;

	while(_rep.length > 0 && troxy->handle_reply(cres, (uint16_t) clino, _rep));

	copyClientResult(env, tcr, cres);

	debugL("");
}

JNIEXPORT void JNICALL Java_reptor_tbftc_ctroxy_CTroxy_verifyProposalNative(JNIEnv *env, jobject thiz, jlong ptroxy, jint verifier, jbyteArray arr, jint offset, jint len)
{
	debugE("(%ld, %p)", ptroxy, arr);

	CTroxy *troxy = (CTroxy *)ptroxy;

	verification_result_t result;

	uint8_t *srcbuf = (uint8_t *) env->GetByteArrayElements(arr, NULL);

	buffer_t _src;
	_src.arr = srcbuf;
	_src.offset = (uint32_t) offset;
	_src.length = (uint32_t) len;

	troxy->verify_proposal(result, (uint32_t)verifier, _src);

	if (__builtin_expect(result == verification_result_t::FAIL, false))
	{
		env->ThrowNew(env->FindClass("reptor/distrbt/com/VerificationException"), "Failed to verify proposal");
	}

	debugL("");
}

JNIEXPORT void JNICALL Java_reptor_tbftc_ctroxy_CTroxy_verifyProposalsNative(JNIEnv *env, jobject thiz, jlong ptroxy, jint verifier, jbyteArray arr)
{
	debugE("(%d)", verifier);

	CTroxy *troxy = (CTroxy *)ptroxy;

	verification_result_t result;

	uint8_t *srcbuf = (uint8_t *) env->GetByteArrayElements(arr, NULL);

	buffer_t _src;
	_src.arr = srcbuf;
	_src.offset = (uint32_t) 0;
	_src.length = (uint32_t) env->GetArrayLength(arr);

	troxy->verify_proposals(result, (uint32_t)verifier, _src);

	if (__builtin_expect(result == verification_result_t::FAIL, false))
	{
		env->ThrowNew(env->FindClass("reptor/distrbt/com/VerificationException"), "Failed to verify proposal");
	}

	debugL("");
}

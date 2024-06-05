#ifndef ENCLAVE_T_H__
#define ENCLAVE_T_H__

#include <stdint.h>
#include <wchar.h>
#include <stddef.h>
#include "sgx_edger8r.h" /* for sgx_ocall etc. */

#include "../../common/HandshakeResult.h"
#include "../../common/CTroxy.h"

#include <stdlib.h> /* for size_t */

#define SGX_CAST(type, item) ((type)(item))

#ifdef __cplusplus
extern "C" {
#endif


void ecall_init(troxy_conf_t conf);
void ecall_reset_handshake(handshake_result_t* result, uint16_t hsno, uint8_t clear);
void ecall_accept(handshake_result_t* result, uint16_t hsno);
void ecall_get_handshake_inbound_minimum_buffer_size(int* result, uint16_t hsno);
void ecall_get_handshake_outbound_minimum_buffer_size(int* result, uint16_t hsno);
void ecall_process_handshake_inbound_data(handshake_result_t* result, uint16_t hsno, buffer_t* source);
void ecall_retrieve_handshake_outbound_data(handshake_result_t* result, uint16_t hsno, buffer_t* destination);
void ecall_save_state(short int hsno);
void ecall_open(client_result_t* result, uint16_t clino);
void ecall_init_client(uint16_t clino, buffer_t* sendbuffer);
void ecall_get_client_inbound_minimum_buffer_size(int* result, uint16_t clino);
void ecall_get_client_outbound_minimum_buffer_size(int* result, uint16_t clino);
bool ecall_process_client_inbound_data(client_result_t* result, uint16_t clino, buffer_t* src, buffer_t* dest);
void ecall_retrieve_client_outbound_data(client_result_t* result, uint16_t clino, buffer_t* dst);
void ecall_retrieve_outbound_messages(client_result_t* result, uint16_t clino);
void ecall_handle_forwarded_request(client_result_t* cliresult, bool* result, uint16_t clino, buffer_t* request);
void ecall_handle_request_executed(client_result_t* result, uint16_t clino, uint64_t invno, buffer_t* reply, bool replyfull);
void ecall_handle_reply(client_result_t* result, uint16_t clino, buffer_t* reply);
void ecall_verify_proposal(bool* result, uint32_t verifier, buffer_t* to_verify);
void ecall_verify_proposals(bool* result, uint32_t verifier, buffer_t* to_verify);

sgx_status_t SGX_CDECL ocall_print_string(const char* str);
sgx_status_t SGX_CDECL sgx_oc_cpuidex(int cpuinfo[4], int leaf, int subleaf);
sgx_status_t SGX_CDECL sgx_thread_wait_untrusted_event_ocall(int* retval, const void* self);
sgx_status_t SGX_CDECL sgx_thread_set_untrusted_event_ocall(int* retval, const void* waiter);
sgx_status_t SGX_CDECL sgx_thread_setwait_untrusted_events_ocall(int* retval, const void* waiter, const void* self);
sgx_status_t SGX_CDECL sgx_thread_set_multiple_untrusted_events_ocall(int* retval, const void** waiters, size_t total);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif

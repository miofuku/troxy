#ifndef ENCLAVE_U_H__
#define ENCLAVE_U_H__

#include <stdint.h>
#include <wchar.h>
#include <stddef.h>
#include <string.h>
#include "sgx_edger8r.h" /* for sgx_satus_t etc. */

#include "../../common/HandshakeResult.h"
#include "../../common/CTroxy.h"

#include <stdlib.h> /* for size_t */

#define SGX_CAST(type, item) ((type)(item))

#ifdef __cplusplus
extern "C" {
#endif

void SGX_UBRIDGE(SGX_NOCONVENTION, ocall_print_string, (const char* str));
void SGX_UBRIDGE(SGX_CDECL, sgx_oc_cpuidex, (int cpuinfo[4], int leaf, int subleaf));
int SGX_UBRIDGE(SGX_CDECL, sgx_thread_wait_untrusted_event_ocall, (const void* self));
int SGX_UBRIDGE(SGX_CDECL, sgx_thread_set_untrusted_event_ocall, (const void* waiter));
int SGX_UBRIDGE(SGX_CDECL, sgx_thread_setwait_untrusted_events_ocall, (const void* waiter, const void* self));
int SGX_UBRIDGE(SGX_CDECL, sgx_thread_set_multiple_untrusted_events_ocall, (const void** waiters, size_t total));

sgx_status_t ecall_init(sgx_enclave_id_t eid, troxy_conf_t conf);
sgx_status_t ecall_reset_handshake(sgx_enclave_id_t eid, handshake_result_t* result, uint16_t hsno, uint8_t clear);
sgx_status_t ecall_accept(sgx_enclave_id_t eid, handshake_result_t* result, uint16_t hsno);
sgx_status_t ecall_get_handshake_inbound_minimum_buffer_size(sgx_enclave_id_t eid, int* result, uint16_t hsno);
sgx_status_t ecall_get_handshake_outbound_minimum_buffer_size(sgx_enclave_id_t eid, int* result, uint16_t hsno);
sgx_status_t ecall_process_handshake_inbound_data(sgx_enclave_id_t eid, handshake_result_t* result, uint16_t hsno, buffer_t* source);
sgx_status_t ecall_retrieve_handshake_outbound_data(sgx_enclave_id_t eid, handshake_result_t* result, uint16_t hsno, buffer_t* destination);
sgx_status_t ecall_save_state(sgx_enclave_id_t eid, short int hsno);
sgx_status_t ecall_open(sgx_enclave_id_t eid, client_result_t* result, uint16_t clino);
sgx_status_t ecall_init_client(sgx_enclave_id_t eid, uint16_t clino, buffer_t* sendbuffer);
sgx_status_t ecall_get_client_inbound_minimum_buffer_size(sgx_enclave_id_t eid, int* result, uint16_t clino);
sgx_status_t ecall_get_client_outbound_minimum_buffer_size(sgx_enclave_id_t eid, int* result, uint16_t clino);
sgx_status_t ecall_process_client_inbound_data(sgx_enclave_id_t eid, bool* retval, client_result_t* result, uint16_t clino, buffer_t* src, buffer_t* dest);
sgx_status_t ecall_retrieve_client_outbound_data(sgx_enclave_id_t eid, client_result_t* result, uint16_t clino, buffer_t* dst);
sgx_status_t ecall_retrieve_outbound_messages(sgx_enclave_id_t eid, client_result_t* result, uint16_t clino);
sgx_status_t ecall_handle_forwarded_request(sgx_enclave_id_t eid, client_result_t* cliresult, bool* result, uint16_t clino, buffer_t* request);
sgx_status_t ecall_handle_request_executed(sgx_enclave_id_t eid, client_result_t* result, uint16_t clino, uint64_t invno, buffer_t* reply, bool replyfull);
sgx_status_t ecall_handle_reply(sgx_enclave_id_t eid, client_result_t* result, uint16_t clino, buffer_t* reply);
sgx_status_t ecall_verify_proposal(sgx_enclave_id_t eid, bool* result, uint32_t verifier, buffer_t* to_verify);
sgx_status_t ecall_verify_proposals(sgx_enclave_id_t eid, bool* result, uint32_t verifier, buffer_t* to_verify);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif

#ifndef TRINX_U_H__
#define TRINX_U_H__

#include <stdint.h>
#include <wchar.h>
#include <stddef.h>
#include <string.h>
#include "sgx_edger8r.h" /* for sgx_satus_t etc. */

#include "counter/trinx_types.h"

#include <stdlib.h> /* for size_t */

#define SGX_CAST(type, item) ((type)(item))

#ifdef __cplusplus
extern "C" {
#endif

typedef enum boolean {
	FALSE = 0,
	TRUE = 1,
} boolean;

void SGX_UBRIDGE(SGX_NOCONVENTION, ocall_print_string, (const char* str));
void SGX_UBRIDGE(SGX_NOCONVENTION, ocall_print_value, (const char* str, uint64_t value));

sgx_status_t ecall_init_counter(sgx_enclave_id_t eid, tssid_t tssid, ctrno_t ncounters, const uint8_t* key, size_t keylen);
sgx_status_t ecall_remove_counter(sgx_enclave_id_t eid, tssid_t tssid);
sgx_status_t ecall_get_counter_certificate_size(sgx_enclave_id_t eid, size_t* retval, tssid_t tssid);
sgx_status_t ecall_get_mac_certificate_size(sgx_enclave_id_t eid, size_t* retval, tssid_t tssid);
sgx_status_t ecall_get_number_of_counters(sgx_enclave_id_t eid, size_t* retval, tssid_t tssid);
sgx_status_t ecall_execute_command(sgx_enclave_id_t eid, tssid_t tssid, void* cmd);
sgx_status_t ecall_touch(sgx_enclave_id_t eid, tssid_t tssid);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif

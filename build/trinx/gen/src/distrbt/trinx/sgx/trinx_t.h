#ifndef TRINX_T_H__
#define TRINX_T_H__

#include <stdint.h>
#include <wchar.h>
#include <stddef.h>
#include "sgx_edger8r.h" /* for sgx_ocall etc. */

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


void ecall_init_counter(tssid_t tssid, ctrno_t ncounters, const uint8_t* key, size_t keylen);
void ecall_remove_counter(tssid_t tssid);
size_t ecall_get_counter_certificate_size(tssid_t tssid);
size_t ecall_get_mac_certificate_size(tssid_t tssid);
size_t ecall_get_number_of_counters(tssid_t tssid);
void ecall_execute_command(tssid_t tssid, void* cmd);
void ecall_touch(tssid_t tssid);

sgx_status_t SGX_CDECL ocall_print_string(const char* str);
sgx_status_t SGX_CDECL ocall_print_value(const char* str, uint64_t value);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif

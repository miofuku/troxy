#include "trinx_u.h"
#include <errno.h>

typedef struct ms_ecall_init_counter_t {
	tssid_t ms_tssid;
	ctrno_t ms_ncounters;
	uint8_t* ms_key;
	size_t ms_keylen;
} ms_ecall_init_counter_t;

typedef struct ms_ecall_remove_counter_t {
	tssid_t ms_tssid;
} ms_ecall_remove_counter_t;

typedef struct ms_ecall_get_counter_certificate_size_t {
	size_t ms_retval;
	tssid_t ms_tssid;
} ms_ecall_get_counter_certificate_size_t;

typedef struct ms_ecall_get_mac_certificate_size_t {
	size_t ms_retval;
	tssid_t ms_tssid;
} ms_ecall_get_mac_certificate_size_t;

typedef struct ms_ecall_get_number_of_counters_t {
	size_t ms_retval;
	tssid_t ms_tssid;
} ms_ecall_get_number_of_counters_t;

typedef struct ms_ecall_execute_command_t {
	tssid_t ms_tssid;
	void* ms_cmd;
} ms_ecall_execute_command_t;

typedef struct ms_ecall_touch_t {
	tssid_t ms_tssid;
} ms_ecall_touch_t;

typedef struct ms_ocall_print_string_t {
	char* ms_str;
} ms_ocall_print_string_t;

typedef struct ms_ocall_print_value_t {
	char* ms_str;
	uint64_t ms_value;
} ms_ocall_print_value_t;

static sgx_status_t SGX_CDECL trinx_ocall_print_string(void* pms)
{
	ms_ocall_print_string_t* ms = SGX_CAST(ms_ocall_print_string_t*, pms);
	ocall_print_string((const char*)ms->ms_str);

	return SGX_SUCCESS;
}

static sgx_status_t SGX_CDECL trinx_ocall_print_value(void* pms)
{
	ms_ocall_print_value_t* ms = SGX_CAST(ms_ocall_print_value_t*, pms);
	ocall_print_value((const char*)ms->ms_str, ms->ms_value);

	return SGX_SUCCESS;
}

static const struct {
	size_t nr_ocall;
	void * table[2];
} ocall_table_trinx = {
	2,
	{
		(void*)trinx_ocall_print_string,
		(void*)trinx_ocall_print_value,
	}
};
sgx_status_t ecall_init_counter(sgx_enclave_id_t eid, tssid_t tssid, ctrno_t ncounters, const uint8_t* key, size_t keylen)
{
	sgx_status_t status;
	ms_ecall_init_counter_t ms;
	ms.ms_tssid = tssid;
	ms.ms_ncounters = ncounters;
	ms.ms_key = (uint8_t*)key;
	ms.ms_keylen = keylen;
	status = sgx_ecall(eid, 0, &ocall_table_trinx, &ms);
	return status;
}

sgx_status_t ecall_remove_counter(sgx_enclave_id_t eid, tssid_t tssid)
{
	sgx_status_t status;
	ms_ecall_remove_counter_t ms;
	ms.ms_tssid = tssid;
	status = sgx_ecall(eid, 1, &ocall_table_trinx, &ms);
	return status;
}

sgx_status_t ecall_get_counter_certificate_size(sgx_enclave_id_t eid, size_t* retval, tssid_t tssid)
{
	sgx_status_t status;
	ms_ecall_get_counter_certificate_size_t ms;
	ms.ms_tssid = tssid;
	status = sgx_ecall(eid, 2, &ocall_table_trinx, &ms);
	if (status == SGX_SUCCESS && retval) *retval = ms.ms_retval;
	return status;
}

sgx_status_t ecall_get_mac_certificate_size(sgx_enclave_id_t eid, size_t* retval, tssid_t tssid)
{
	sgx_status_t status;
	ms_ecall_get_mac_certificate_size_t ms;
	ms.ms_tssid = tssid;
	status = sgx_ecall(eid, 3, &ocall_table_trinx, &ms);
	if (status == SGX_SUCCESS && retval) *retval = ms.ms_retval;
	return status;
}

sgx_status_t ecall_get_number_of_counters(sgx_enclave_id_t eid, size_t* retval, tssid_t tssid)
{
	sgx_status_t status;
	ms_ecall_get_number_of_counters_t ms;
	ms.ms_tssid = tssid;
	status = sgx_ecall(eid, 4, &ocall_table_trinx, &ms);
	if (status == SGX_SUCCESS && retval) *retval = ms.ms_retval;
	return status;
}

sgx_status_t ecall_execute_command(sgx_enclave_id_t eid, tssid_t tssid, void* cmd)
{
	sgx_status_t status;
	ms_ecall_execute_command_t ms;
	ms.ms_tssid = tssid;
	ms.ms_cmd = cmd;
	status = sgx_ecall(eid, 5, &ocall_table_trinx, &ms);
	return status;
}

sgx_status_t ecall_touch(sgx_enclave_id_t eid, tssid_t tssid)
{
	sgx_status_t status;
	ms_ecall_touch_t ms;
	ms.ms_tssid = tssid;
	status = sgx_ecall(eid, 6, &ocall_table_trinx, &ms);
	return status;
}


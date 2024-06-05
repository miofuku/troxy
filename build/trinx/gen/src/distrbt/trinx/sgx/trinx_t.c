#include "trinx_t.h"

#include "sgx_trts.h" /* for sgx_ocalloc, sgx_is_outside_enclave */

#include <errno.h>
#include <string.h> /* for memcpy etc */
#include <stdlib.h> /* for malloc/free etc */

#define CHECK_REF_POINTER(ptr, siz) do {	\
	if (!(ptr) || ! sgx_is_outside_enclave((ptr), (siz)))	\
		return SGX_ERROR_INVALID_PARAMETER;\
} while (0)

#define CHECK_UNIQUE_POINTER(ptr, siz) do {	\
	if ((ptr) && ! sgx_is_outside_enclave((ptr), (siz)))	\
		return SGX_ERROR_INVALID_PARAMETER;\
} while (0)


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

static sgx_status_t SGX_CDECL sgx_ecall_init_counter(void* pms)
{
	ms_ecall_init_counter_t* ms = SGX_CAST(ms_ecall_init_counter_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	uint8_t* _tmp_key = ms->ms_key;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_init_counter_t));

	ecall_init_counter(ms->ms_tssid, ms->ms_ncounters, (const uint8_t*)_tmp_key, ms->ms_keylen);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_remove_counter(void* pms)
{
	ms_ecall_remove_counter_t* ms = SGX_CAST(ms_ecall_remove_counter_t*, pms);
	sgx_status_t status = SGX_SUCCESS;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_remove_counter_t));

	ecall_remove_counter(ms->ms_tssid);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_get_counter_certificate_size(void* pms)
{
	ms_ecall_get_counter_certificate_size_t* ms = SGX_CAST(ms_ecall_get_counter_certificate_size_t*, pms);
	sgx_status_t status = SGX_SUCCESS;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_get_counter_certificate_size_t));

	ms->ms_retval = ecall_get_counter_certificate_size(ms->ms_tssid);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_get_mac_certificate_size(void* pms)
{
	ms_ecall_get_mac_certificate_size_t* ms = SGX_CAST(ms_ecall_get_mac_certificate_size_t*, pms);
	sgx_status_t status = SGX_SUCCESS;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_get_mac_certificate_size_t));

	ms->ms_retval = ecall_get_mac_certificate_size(ms->ms_tssid);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_get_number_of_counters(void* pms)
{
	ms_ecall_get_number_of_counters_t* ms = SGX_CAST(ms_ecall_get_number_of_counters_t*, pms);
	sgx_status_t status = SGX_SUCCESS;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_get_number_of_counters_t));

	ms->ms_retval = ecall_get_number_of_counters(ms->ms_tssid);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_execute_command(void* pms)
{
	ms_ecall_execute_command_t* ms = SGX_CAST(ms_ecall_execute_command_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	void* _tmp_cmd = ms->ms_cmd;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_execute_command_t));

	ecall_execute_command(ms->ms_tssid, _tmp_cmd);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_touch(void* pms)
{
	ms_ecall_touch_t* ms = SGX_CAST(ms_ecall_touch_t*, pms);
	sgx_status_t status = SGX_SUCCESS;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_touch_t));

	ecall_touch(ms->ms_tssid);


	return status;
}

SGX_EXTERNC const struct {
	size_t nr_ecall;
	struct {void* ecall_addr; uint8_t is_priv;} ecall_table[7];
} g_ecall_table = {
	7,
	{
		{(void*)(uintptr_t)sgx_ecall_init_counter, 0},
		{(void*)(uintptr_t)sgx_ecall_remove_counter, 0},
		{(void*)(uintptr_t)sgx_ecall_get_counter_certificate_size, 0},
		{(void*)(uintptr_t)sgx_ecall_get_mac_certificate_size, 0},
		{(void*)(uintptr_t)sgx_ecall_get_number_of_counters, 0},
		{(void*)(uintptr_t)sgx_ecall_execute_command, 0},
		{(void*)(uintptr_t)sgx_ecall_touch, 0},
	}
};

SGX_EXTERNC const struct {
	size_t nr_ocall;
	uint8_t entry_table[2][7];
} g_dyn_entry_table = {
	2,
	{
		{0, 0, 0, 0, 0, 0, 0, },
		{0, 0, 0, 0, 0, 0, 0, },
	}
};


sgx_status_t SGX_CDECL ocall_print_string(const char* str)
{
	sgx_status_t status = SGX_SUCCESS;
	size_t _len_str = str ? strlen(str) + 1 : 0;

	ms_ocall_print_string_t* ms = NULL;
	size_t ocalloc_size = sizeof(ms_ocall_print_string_t);
	void *__tmp = NULL;

	ocalloc_size += (str != NULL && sgx_is_within_enclave(str, _len_str)) ? _len_str : 0;

	__tmp = sgx_ocalloc(ocalloc_size);
	if (__tmp == NULL) {
		sgx_ocfree();
		return SGX_ERROR_UNEXPECTED;
	}
	ms = (ms_ocall_print_string_t*)__tmp;
	__tmp = (void *)((size_t)__tmp + sizeof(ms_ocall_print_string_t));

	if (str != NULL && sgx_is_within_enclave(str, _len_str)) {
		ms->ms_str = (char*)__tmp;
		__tmp = (void *)((size_t)__tmp + _len_str);
		memcpy((void*)ms->ms_str, str, _len_str);
	} else if (str == NULL) {
		ms->ms_str = NULL;
	} else {
		sgx_ocfree();
		return SGX_ERROR_INVALID_PARAMETER;
	}
	
	status = sgx_ocall(0, ms);


	sgx_ocfree();
	return status;
}

sgx_status_t SGX_CDECL ocall_print_value(const char* str, uint64_t value)
{
	sgx_status_t status = SGX_SUCCESS;
	size_t _len_str = str ? strlen(str) + 1 : 0;

	ms_ocall_print_value_t* ms = NULL;
	size_t ocalloc_size = sizeof(ms_ocall_print_value_t);
	void *__tmp = NULL;

	ocalloc_size += (str != NULL && sgx_is_within_enclave(str, _len_str)) ? _len_str : 0;

	__tmp = sgx_ocalloc(ocalloc_size);
	if (__tmp == NULL) {
		sgx_ocfree();
		return SGX_ERROR_UNEXPECTED;
	}
	ms = (ms_ocall_print_value_t*)__tmp;
	__tmp = (void *)((size_t)__tmp + sizeof(ms_ocall_print_value_t));

	if (str != NULL && sgx_is_within_enclave(str, _len_str)) {
		ms->ms_str = (char*)__tmp;
		__tmp = (void *)((size_t)__tmp + _len_str);
		memcpy((void*)ms->ms_str, str, _len_str);
	} else if (str == NULL) {
		ms->ms_str = NULL;
	} else {
		sgx_ocfree();
		return SGX_ERROR_INVALID_PARAMETER;
	}
	
	ms->ms_value = value;
	status = sgx_ocall(1, ms);


	sgx_ocfree();
	return status;
}


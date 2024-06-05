#include "enclave_t.h"

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


typedef struct ms_ecall_init_t {
	troxy_conf_t ms_conf;
} ms_ecall_init_t;

typedef struct ms_ecall_reset_handshake_t {
	handshake_result_t* ms_result;
	uint16_t ms_hsno;
	uint8_t ms_clear;
} ms_ecall_reset_handshake_t;

typedef struct ms_ecall_accept_t {
	handshake_result_t* ms_result;
	uint16_t ms_hsno;
} ms_ecall_accept_t;

typedef struct ms_ecall_get_handshake_inbound_minimum_buffer_size_t {
	int* ms_result;
	uint16_t ms_hsno;
} ms_ecall_get_handshake_inbound_minimum_buffer_size_t;

typedef struct ms_ecall_get_handshake_outbound_minimum_buffer_size_t {
	int* ms_result;
	uint16_t ms_hsno;
} ms_ecall_get_handshake_outbound_minimum_buffer_size_t;

typedef struct ms_ecall_process_handshake_inbound_data_t {
	handshake_result_t* ms_result;
	uint16_t ms_hsno;
	buffer_t* ms_source;
} ms_ecall_process_handshake_inbound_data_t;

typedef struct ms_ecall_retrieve_handshake_outbound_data_t {
	handshake_result_t* ms_result;
	uint16_t ms_hsno;
	buffer_t* ms_destination;
} ms_ecall_retrieve_handshake_outbound_data_t;

typedef struct ms_ecall_save_state_t {
	short int ms_hsno;
} ms_ecall_save_state_t;

typedef struct ms_ecall_open_t {
	client_result_t* ms_result;
	uint16_t ms_clino;
} ms_ecall_open_t;

typedef struct ms_ecall_init_client_t {
	uint16_t ms_clino;
	buffer_t* ms_sendbuffer;
} ms_ecall_init_client_t;

typedef struct ms_ecall_get_client_inbound_minimum_buffer_size_t {
	int* ms_result;
	uint16_t ms_clino;
} ms_ecall_get_client_inbound_minimum_buffer_size_t;

typedef struct ms_ecall_get_client_outbound_minimum_buffer_size_t {
	int* ms_result;
	uint16_t ms_clino;
} ms_ecall_get_client_outbound_minimum_buffer_size_t;

typedef struct ms_ecall_process_client_inbound_data_t {
	bool ms_retval;
	client_result_t* ms_result;
	uint16_t ms_clino;
	buffer_t* ms_src;
	buffer_t* ms_dest;
} ms_ecall_process_client_inbound_data_t;

typedef struct ms_ecall_retrieve_client_outbound_data_t {
	client_result_t* ms_result;
	uint16_t ms_clino;
	buffer_t* ms_dst;
} ms_ecall_retrieve_client_outbound_data_t;

typedef struct ms_ecall_retrieve_outbound_messages_t {
	client_result_t* ms_result;
	uint16_t ms_clino;
} ms_ecall_retrieve_outbound_messages_t;

typedef struct ms_ecall_handle_forwarded_request_t {
	client_result_t* ms_cliresult;
	bool* ms_result;
	uint16_t ms_clino;
	buffer_t* ms_request;
} ms_ecall_handle_forwarded_request_t;

typedef struct ms_ecall_handle_request_executed_t {
	client_result_t* ms_result;
	uint16_t ms_clino;
	uint64_t ms_invno;
	buffer_t* ms_reply;
	bool ms_replyfull;
} ms_ecall_handle_request_executed_t;

typedef struct ms_ecall_handle_reply_t {
	client_result_t* ms_result;
	uint16_t ms_clino;
	buffer_t* ms_reply;
} ms_ecall_handle_reply_t;

typedef struct ms_ecall_verify_proposal_t {
	bool* ms_result;
	uint32_t ms_verifier;
	buffer_t* ms_to_verify;
} ms_ecall_verify_proposal_t;

typedef struct ms_ecall_verify_proposals_t {
	bool* ms_result;
	uint32_t ms_verifier;
	buffer_t* ms_to_verify;
} ms_ecall_verify_proposals_t;

typedef struct ms_ocall_print_string_t {
	char* ms_str;
} ms_ocall_print_string_t;

typedef struct ms_sgx_oc_cpuidex_t {
	int* ms_cpuinfo;
	int ms_leaf;
	int ms_subleaf;
} ms_sgx_oc_cpuidex_t;

typedef struct ms_sgx_thread_wait_untrusted_event_ocall_t {
	int ms_retval;
	void* ms_self;
} ms_sgx_thread_wait_untrusted_event_ocall_t;

typedef struct ms_sgx_thread_set_untrusted_event_ocall_t {
	int ms_retval;
	void* ms_waiter;
} ms_sgx_thread_set_untrusted_event_ocall_t;

typedef struct ms_sgx_thread_setwait_untrusted_events_ocall_t {
	int ms_retval;
	void* ms_waiter;
	void* ms_self;
} ms_sgx_thread_setwait_untrusted_events_ocall_t;

typedef struct ms_sgx_thread_set_multiple_untrusted_events_ocall_t {
	int ms_retval;
	void** ms_waiters;
	size_t ms_total;
} ms_sgx_thread_set_multiple_untrusted_events_ocall_t;

static sgx_status_t SGX_CDECL sgx_ecall_init(void* pms)
{
	ms_ecall_init_t* ms = SGX_CAST(ms_ecall_init_t*, pms);
	sgx_status_t status = SGX_SUCCESS;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_init_t));

	ecall_init(ms->ms_conf);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_reset_handshake(void* pms)
{
	ms_ecall_reset_handshake_t* ms = SGX_CAST(ms_ecall_reset_handshake_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	handshake_result_t* _tmp_result = ms->ms_result;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_reset_handshake_t));

	ecall_reset_handshake(_tmp_result, ms->ms_hsno, ms->ms_clear);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_accept(void* pms)
{
	ms_ecall_accept_t* ms = SGX_CAST(ms_ecall_accept_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	handshake_result_t* _tmp_result = ms->ms_result;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_accept_t));

	ecall_accept(_tmp_result, ms->ms_hsno);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_get_handshake_inbound_minimum_buffer_size(void* pms)
{
	ms_ecall_get_handshake_inbound_minimum_buffer_size_t* ms = SGX_CAST(ms_ecall_get_handshake_inbound_minimum_buffer_size_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	int* _tmp_result = ms->ms_result;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_get_handshake_inbound_minimum_buffer_size_t));

	ecall_get_handshake_inbound_minimum_buffer_size(_tmp_result, ms->ms_hsno);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_get_handshake_outbound_minimum_buffer_size(void* pms)
{
	ms_ecall_get_handshake_outbound_minimum_buffer_size_t* ms = SGX_CAST(ms_ecall_get_handshake_outbound_minimum_buffer_size_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	int* _tmp_result = ms->ms_result;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_get_handshake_outbound_minimum_buffer_size_t));

	ecall_get_handshake_outbound_minimum_buffer_size(_tmp_result, ms->ms_hsno);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_process_handshake_inbound_data(void* pms)
{
	ms_ecall_process_handshake_inbound_data_t* ms = SGX_CAST(ms_ecall_process_handshake_inbound_data_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	handshake_result_t* _tmp_result = ms->ms_result;
	buffer_t* _tmp_source = ms->ms_source;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_process_handshake_inbound_data_t));

	ecall_process_handshake_inbound_data(_tmp_result, ms->ms_hsno, _tmp_source);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_retrieve_handshake_outbound_data(void* pms)
{
	ms_ecall_retrieve_handshake_outbound_data_t* ms = SGX_CAST(ms_ecall_retrieve_handshake_outbound_data_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	handshake_result_t* _tmp_result = ms->ms_result;
	buffer_t* _tmp_destination = ms->ms_destination;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_retrieve_handshake_outbound_data_t));

	ecall_retrieve_handshake_outbound_data(_tmp_result, ms->ms_hsno, _tmp_destination);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_save_state(void* pms)
{
	ms_ecall_save_state_t* ms = SGX_CAST(ms_ecall_save_state_t*, pms);
	sgx_status_t status = SGX_SUCCESS;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_save_state_t));

	ecall_save_state(ms->ms_hsno);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_open(void* pms)
{
	ms_ecall_open_t* ms = SGX_CAST(ms_ecall_open_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	client_result_t* _tmp_result = ms->ms_result;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_open_t));

	ecall_open(_tmp_result, ms->ms_clino);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_init_client(void* pms)
{
	ms_ecall_init_client_t* ms = SGX_CAST(ms_ecall_init_client_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	buffer_t* _tmp_sendbuffer = ms->ms_sendbuffer;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_init_client_t));

	ecall_init_client(ms->ms_clino, _tmp_sendbuffer);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_get_client_inbound_minimum_buffer_size(void* pms)
{
	ms_ecall_get_client_inbound_minimum_buffer_size_t* ms = SGX_CAST(ms_ecall_get_client_inbound_minimum_buffer_size_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	int* _tmp_result = ms->ms_result;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_get_client_inbound_minimum_buffer_size_t));

	ecall_get_client_inbound_minimum_buffer_size(_tmp_result, ms->ms_clino);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_get_client_outbound_minimum_buffer_size(void* pms)
{
	ms_ecall_get_client_outbound_minimum_buffer_size_t* ms = SGX_CAST(ms_ecall_get_client_outbound_minimum_buffer_size_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	int* _tmp_result = ms->ms_result;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_get_client_outbound_minimum_buffer_size_t));

	ecall_get_client_outbound_minimum_buffer_size(_tmp_result, ms->ms_clino);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_process_client_inbound_data(void* pms)
{
	ms_ecall_process_client_inbound_data_t* ms = SGX_CAST(ms_ecall_process_client_inbound_data_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	client_result_t* _tmp_result = ms->ms_result;
	buffer_t* _tmp_src = ms->ms_src;
	buffer_t* _tmp_dest = ms->ms_dest;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_process_client_inbound_data_t));

	ms->ms_retval = ecall_process_client_inbound_data(_tmp_result, ms->ms_clino, _tmp_src, _tmp_dest);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_retrieve_client_outbound_data(void* pms)
{
	ms_ecall_retrieve_client_outbound_data_t* ms = SGX_CAST(ms_ecall_retrieve_client_outbound_data_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	client_result_t* _tmp_result = ms->ms_result;
	buffer_t* _tmp_dst = ms->ms_dst;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_retrieve_client_outbound_data_t));

	ecall_retrieve_client_outbound_data(_tmp_result, ms->ms_clino, _tmp_dst);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_retrieve_outbound_messages(void* pms)
{
	ms_ecall_retrieve_outbound_messages_t* ms = SGX_CAST(ms_ecall_retrieve_outbound_messages_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	client_result_t* _tmp_result = ms->ms_result;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_retrieve_outbound_messages_t));

	ecall_retrieve_outbound_messages(_tmp_result, ms->ms_clino);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_handle_forwarded_request(void* pms)
{
	ms_ecall_handle_forwarded_request_t* ms = SGX_CAST(ms_ecall_handle_forwarded_request_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	client_result_t* _tmp_cliresult = ms->ms_cliresult;
	bool* _tmp_result = ms->ms_result;
	buffer_t* _tmp_request = ms->ms_request;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_handle_forwarded_request_t));

	ecall_handle_forwarded_request(_tmp_cliresult, _tmp_result, ms->ms_clino, _tmp_request);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_handle_request_executed(void* pms)
{
	ms_ecall_handle_request_executed_t* ms = SGX_CAST(ms_ecall_handle_request_executed_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	client_result_t* _tmp_result = ms->ms_result;
	buffer_t* _tmp_reply = ms->ms_reply;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_handle_request_executed_t));

	ecall_handle_request_executed(_tmp_result, ms->ms_clino, ms->ms_invno, _tmp_reply, ms->ms_replyfull);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_handle_reply(void* pms)
{
	ms_ecall_handle_reply_t* ms = SGX_CAST(ms_ecall_handle_reply_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	client_result_t* _tmp_result = ms->ms_result;
	buffer_t* _tmp_reply = ms->ms_reply;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_handle_reply_t));

	ecall_handle_reply(_tmp_result, ms->ms_clino, _tmp_reply);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_verify_proposal(void* pms)
{
	ms_ecall_verify_proposal_t* ms = SGX_CAST(ms_ecall_verify_proposal_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	bool* _tmp_result = ms->ms_result;
	buffer_t* _tmp_to_verify = ms->ms_to_verify;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_verify_proposal_t));

	ecall_verify_proposal(_tmp_result, ms->ms_verifier, _tmp_to_verify);


	return status;
}

static sgx_status_t SGX_CDECL sgx_ecall_verify_proposals(void* pms)
{
	ms_ecall_verify_proposals_t* ms = SGX_CAST(ms_ecall_verify_proposals_t*, pms);
	sgx_status_t status = SGX_SUCCESS;
	bool* _tmp_result = ms->ms_result;
	buffer_t* _tmp_to_verify = ms->ms_to_verify;

	CHECK_REF_POINTER(pms, sizeof(ms_ecall_verify_proposals_t));

	ecall_verify_proposals(_tmp_result, ms->ms_verifier, _tmp_to_verify);


	return status;
}

SGX_EXTERNC const struct {
	size_t nr_ecall;
	struct {void* ecall_addr; uint8_t is_priv;} ecall_table[20];
} g_ecall_table = {
	20,
	{
		{(void*)(uintptr_t)sgx_ecall_init, 0},
		{(void*)(uintptr_t)sgx_ecall_reset_handshake, 0},
		{(void*)(uintptr_t)sgx_ecall_accept, 0},
		{(void*)(uintptr_t)sgx_ecall_get_handshake_inbound_minimum_buffer_size, 0},
		{(void*)(uintptr_t)sgx_ecall_get_handshake_outbound_minimum_buffer_size, 0},
		{(void*)(uintptr_t)sgx_ecall_process_handshake_inbound_data, 0},
		{(void*)(uintptr_t)sgx_ecall_retrieve_handshake_outbound_data, 0},
		{(void*)(uintptr_t)sgx_ecall_save_state, 0},
		{(void*)(uintptr_t)sgx_ecall_open, 0},
		{(void*)(uintptr_t)sgx_ecall_init_client, 0},
		{(void*)(uintptr_t)sgx_ecall_get_client_inbound_minimum_buffer_size, 0},
		{(void*)(uintptr_t)sgx_ecall_get_client_outbound_minimum_buffer_size, 0},
		{(void*)(uintptr_t)sgx_ecall_process_client_inbound_data, 0},
		{(void*)(uintptr_t)sgx_ecall_retrieve_client_outbound_data, 0},
		{(void*)(uintptr_t)sgx_ecall_retrieve_outbound_messages, 0},
		{(void*)(uintptr_t)sgx_ecall_handle_forwarded_request, 0},
		{(void*)(uintptr_t)sgx_ecall_handle_request_executed, 0},
		{(void*)(uintptr_t)sgx_ecall_handle_reply, 0},
		{(void*)(uintptr_t)sgx_ecall_verify_proposal, 0},
		{(void*)(uintptr_t)sgx_ecall_verify_proposals, 0},
	}
};

SGX_EXTERNC const struct {
	size_t nr_ocall;
	uint8_t entry_table[6][20];
} g_dyn_entry_table = {
	6,
	{
		{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, },
		{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, },
		{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, },
		{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, },
		{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, },
		{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, },
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

sgx_status_t SGX_CDECL sgx_oc_cpuidex(int cpuinfo[4], int leaf, int subleaf)
{
	sgx_status_t status = SGX_SUCCESS;
	size_t _len_cpuinfo = 4 * sizeof(*cpuinfo);

	ms_sgx_oc_cpuidex_t* ms = NULL;
	size_t ocalloc_size = sizeof(ms_sgx_oc_cpuidex_t);
	void *__tmp = NULL;

	ocalloc_size += (cpuinfo != NULL && sgx_is_within_enclave(cpuinfo, _len_cpuinfo)) ? _len_cpuinfo : 0;

	__tmp = sgx_ocalloc(ocalloc_size);
	if (__tmp == NULL) {
		sgx_ocfree();
		return SGX_ERROR_UNEXPECTED;
	}
	ms = (ms_sgx_oc_cpuidex_t*)__tmp;
	__tmp = (void *)((size_t)__tmp + sizeof(ms_sgx_oc_cpuidex_t));

	if (cpuinfo != NULL && sgx_is_within_enclave(cpuinfo, _len_cpuinfo)) {
		ms->ms_cpuinfo = (int*)__tmp;
		__tmp = (void *)((size_t)__tmp + _len_cpuinfo);
		memcpy(ms->ms_cpuinfo, cpuinfo, _len_cpuinfo);
	} else if (cpuinfo == NULL) {
		ms->ms_cpuinfo = NULL;
	} else {
		sgx_ocfree();
		return SGX_ERROR_INVALID_PARAMETER;
	}
	
	ms->ms_leaf = leaf;
	ms->ms_subleaf = subleaf;
	status = sgx_ocall(1, ms);

	if (cpuinfo) memcpy((void*)cpuinfo, ms->ms_cpuinfo, _len_cpuinfo);

	sgx_ocfree();
	return status;
}

sgx_status_t SGX_CDECL sgx_thread_wait_untrusted_event_ocall(int* retval, const void* self)
{
	sgx_status_t status = SGX_SUCCESS;

	ms_sgx_thread_wait_untrusted_event_ocall_t* ms = NULL;
	size_t ocalloc_size = sizeof(ms_sgx_thread_wait_untrusted_event_ocall_t);
	void *__tmp = NULL;


	__tmp = sgx_ocalloc(ocalloc_size);
	if (__tmp == NULL) {
		sgx_ocfree();
		return SGX_ERROR_UNEXPECTED;
	}
	ms = (ms_sgx_thread_wait_untrusted_event_ocall_t*)__tmp;
	__tmp = (void *)((size_t)__tmp + sizeof(ms_sgx_thread_wait_untrusted_event_ocall_t));

	ms->ms_self = SGX_CAST(void*, self);
	status = sgx_ocall(2, ms);

	if (retval) *retval = ms->ms_retval;

	sgx_ocfree();
	return status;
}

sgx_status_t SGX_CDECL sgx_thread_set_untrusted_event_ocall(int* retval, const void* waiter)
{
	sgx_status_t status = SGX_SUCCESS;

	ms_sgx_thread_set_untrusted_event_ocall_t* ms = NULL;
	size_t ocalloc_size = sizeof(ms_sgx_thread_set_untrusted_event_ocall_t);
	void *__tmp = NULL;


	__tmp = sgx_ocalloc(ocalloc_size);
	if (__tmp == NULL) {
		sgx_ocfree();
		return SGX_ERROR_UNEXPECTED;
	}
	ms = (ms_sgx_thread_set_untrusted_event_ocall_t*)__tmp;
	__tmp = (void *)((size_t)__tmp + sizeof(ms_sgx_thread_set_untrusted_event_ocall_t));

	ms->ms_waiter = SGX_CAST(void*, waiter);
	status = sgx_ocall(3, ms);

	if (retval) *retval = ms->ms_retval;

	sgx_ocfree();
	return status;
}

sgx_status_t SGX_CDECL sgx_thread_setwait_untrusted_events_ocall(int* retval, const void* waiter, const void* self)
{
	sgx_status_t status = SGX_SUCCESS;

	ms_sgx_thread_setwait_untrusted_events_ocall_t* ms = NULL;
	size_t ocalloc_size = sizeof(ms_sgx_thread_setwait_untrusted_events_ocall_t);
	void *__tmp = NULL;


	__tmp = sgx_ocalloc(ocalloc_size);
	if (__tmp == NULL) {
		sgx_ocfree();
		return SGX_ERROR_UNEXPECTED;
	}
	ms = (ms_sgx_thread_setwait_untrusted_events_ocall_t*)__tmp;
	__tmp = (void *)((size_t)__tmp + sizeof(ms_sgx_thread_setwait_untrusted_events_ocall_t));

	ms->ms_waiter = SGX_CAST(void*, waiter);
	ms->ms_self = SGX_CAST(void*, self);
	status = sgx_ocall(4, ms);

	if (retval) *retval = ms->ms_retval;

	sgx_ocfree();
	return status;
}

sgx_status_t SGX_CDECL sgx_thread_set_multiple_untrusted_events_ocall(int* retval, const void** waiters, size_t total)
{
	sgx_status_t status = SGX_SUCCESS;
	size_t _len_waiters = total * sizeof(*waiters);

	ms_sgx_thread_set_multiple_untrusted_events_ocall_t* ms = NULL;
	size_t ocalloc_size = sizeof(ms_sgx_thread_set_multiple_untrusted_events_ocall_t);
	void *__tmp = NULL;

	ocalloc_size += (waiters != NULL && sgx_is_within_enclave(waiters, _len_waiters)) ? _len_waiters : 0;

	__tmp = sgx_ocalloc(ocalloc_size);
	if (__tmp == NULL) {
		sgx_ocfree();
		return SGX_ERROR_UNEXPECTED;
	}
	ms = (ms_sgx_thread_set_multiple_untrusted_events_ocall_t*)__tmp;
	__tmp = (void *)((size_t)__tmp + sizeof(ms_sgx_thread_set_multiple_untrusted_events_ocall_t));

	if (waiters != NULL && sgx_is_within_enclave(waiters, _len_waiters)) {
		ms->ms_waiters = (void**)__tmp;
		__tmp = (void *)((size_t)__tmp + _len_waiters);
		memcpy((void*)ms->ms_waiters, waiters, _len_waiters);
	} else if (waiters == NULL) {
		ms->ms_waiters = NULL;
	} else {
		sgx_ocfree();
		return SGX_ERROR_INVALID_PARAMETER;
	}
	
	ms->ms_total = total;
	status = sgx_ocall(5, ms);

	if (retval) *retval = ms->ms_retval;

	sgx_ocfree();
	return status;
}


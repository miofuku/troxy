#include "enclave_u.h"
#include <errno.h>

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

static sgx_status_t SGX_CDECL enclave_ocall_print_string(void* pms)
{
	ms_ocall_print_string_t* ms = SGX_CAST(ms_ocall_print_string_t*, pms);
	ocall_print_string((const char*)ms->ms_str);

	return SGX_SUCCESS;
}

static sgx_status_t SGX_CDECL enclave_sgx_oc_cpuidex(void* pms)
{
	ms_sgx_oc_cpuidex_t* ms = SGX_CAST(ms_sgx_oc_cpuidex_t*, pms);
	sgx_oc_cpuidex(ms->ms_cpuinfo, ms->ms_leaf, ms->ms_subleaf);

	return SGX_SUCCESS;
}

static sgx_status_t SGX_CDECL enclave_sgx_thread_wait_untrusted_event_ocall(void* pms)
{
	ms_sgx_thread_wait_untrusted_event_ocall_t* ms = SGX_CAST(ms_sgx_thread_wait_untrusted_event_ocall_t*, pms);
	ms->ms_retval = sgx_thread_wait_untrusted_event_ocall((const void*)ms->ms_self);

	return SGX_SUCCESS;
}

static sgx_status_t SGX_CDECL enclave_sgx_thread_set_untrusted_event_ocall(void* pms)
{
	ms_sgx_thread_set_untrusted_event_ocall_t* ms = SGX_CAST(ms_sgx_thread_set_untrusted_event_ocall_t*, pms);
	ms->ms_retval = sgx_thread_set_untrusted_event_ocall((const void*)ms->ms_waiter);

	return SGX_SUCCESS;
}

static sgx_status_t SGX_CDECL enclave_sgx_thread_setwait_untrusted_events_ocall(void* pms)
{
	ms_sgx_thread_setwait_untrusted_events_ocall_t* ms = SGX_CAST(ms_sgx_thread_setwait_untrusted_events_ocall_t*, pms);
	ms->ms_retval = sgx_thread_setwait_untrusted_events_ocall((const void*)ms->ms_waiter, (const void*)ms->ms_self);

	return SGX_SUCCESS;
}

static sgx_status_t SGX_CDECL enclave_sgx_thread_set_multiple_untrusted_events_ocall(void* pms)
{
	ms_sgx_thread_set_multiple_untrusted_events_ocall_t* ms = SGX_CAST(ms_sgx_thread_set_multiple_untrusted_events_ocall_t*, pms);
	ms->ms_retval = sgx_thread_set_multiple_untrusted_events_ocall((const void**)ms->ms_waiters, ms->ms_total);

	return SGX_SUCCESS;
}

static const struct {
	size_t nr_ocall;
	void * table[6];
} ocall_table_enclave = {
	6,
	{
		(void*)enclave_ocall_print_string,
		(void*)enclave_sgx_oc_cpuidex,
		(void*)enclave_sgx_thread_wait_untrusted_event_ocall,
		(void*)enclave_sgx_thread_set_untrusted_event_ocall,
		(void*)enclave_sgx_thread_setwait_untrusted_events_ocall,
		(void*)enclave_sgx_thread_set_multiple_untrusted_events_ocall,
	}
};
sgx_status_t ecall_init(sgx_enclave_id_t eid, troxy_conf_t conf)
{
	sgx_status_t status;
	ms_ecall_init_t ms;
	ms.ms_conf = conf;
	status = sgx_ecall(eid, 0, &ocall_table_enclave, &ms);
	return status;
}

sgx_status_t ecall_reset_handshake(sgx_enclave_id_t eid, handshake_result_t* result, uint16_t hsno, uint8_t clear)
{
	sgx_status_t status;
	ms_ecall_reset_handshake_t ms;
	ms.ms_result = result;
	ms.ms_hsno = hsno;
	ms.ms_clear = clear;
	status = sgx_ecall(eid, 1, &ocall_table_enclave, &ms);
	return status;
}

sgx_status_t ecall_accept(sgx_enclave_id_t eid, handshake_result_t* result, uint16_t hsno)
{
	sgx_status_t status;
	ms_ecall_accept_t ms;
	ms.ms_result = result;
	ms.ms_hsno = hsno;
	status = sgx_ecall(eid, 2, &ocall_table_enclave, &ms);
	return status;
}

sgx_status_t ecall_get_handshake_inbound_minimum_buffer_size(sgx_enclave_id_t eid, int* result, uint16_t hsno)
{
	sgx_status_t status;
	ms_ecall_get_handshake_inbound_minimum_buffer_size_t ms;
	ms.ms_result = result;
	ms.ms_hsno = hsno;
	status = sgx_ecall(eid, 3, &ocall_table_enclave, &ms);
	return status;
}

sgx_status_t ecall_get_handshake_outbound_minimum_buffer_size(sgx_enclave_id_t eid, int* result, uint16_t hsno)
{
	sgx_status_t status;
	ms_ecall_get_handshake_outbound_minimum_buffer_size_t ms;
	ms.ms_result = result;
	ms.ms_hsno = hsno;
	status = sgx_ecall(eid, 4, &ocall_table_enclave, &ms);
	return status;
}

sgx_status_t ecall_process_handshake_inbound_data(sgx_enclave_id_t eid, handshake_result_t* result, uint16_t hsno, buffer_t* source)
{
	sgx_status_t status;
	ms_ecall_process_handshake_inbound_data_t ms;
	ms.ms_result = result;
	ms.ms_hsno = hsno;
	ms.ms_source = source;
	status = sgx_ecall(eid, 5, &ocall_table_enclave, &ms);
	return status;
}

sgx_status_t ecall_retrieve_handshake_outbound_data(sgx_enclave_id_t eid, handshake_result_t* result, uint16_t hsno, buffer_t* destination)
{
	sgx_status_t status;
	ms_ecall_retrieve_handshake_outbound_data_t ms;
	ms.ms_result = result;
	ms.ms_hsno = hsno;
	ms.ms_destination = destination;
	status = sgx_ecall(eid, 6, &ocall_table_enclave, &ms);
	return status;
}

sgx_status_t ecall_save_state(sgx_enclave_id_t eid, short int hsno)
{
	sgx_status_t status;
	ms_ecall_save_state_t ms;
	ms.ms_hsno = hsno;
	status = sgx_ecall(eid, 7, &ocall_table_enclave, &ms);
	return status;
}

sgx_status_t ecall_open(sgx_enclave_id_t eid, client_result_t* result, uint16_t clino)
{
	sgx_status_t status;
	ms_ecall_open_t ms;
	ms.ms_result = result;
	ms.ms_clino = clino;
	status = sgx_ecall(eid, 8, &ocall_table_enclave, &ms);
	return status;
}

sgx_status_t ecall_init_client(sgx_enclave_id_t eid, uint16_t clino, buffer_t* sendbuffer)
{
	sgx_status_t status;
	ms_ecall_init_client_t ms;
	ms.ms_clino = clino;
	ms.ms_sendbuffer = sendbuffer;
	status = sgx_ecall(eid, 9, &ocall_table_enclave, &ms);
	return status;
}

sgx_status_t ecall_get_client_inbound_minimum_buffer_size(sgx_enclave_id_t eid, int* result, uint16_t clino)
{
	sgx_status_t status;
	ms_ecall_get_client_inbound_minimum_buffer_size_t ms;
	ms.ms_result = result;
	ms.ms_clino = clino;
	status = sgx_ecall(eid, 10, &ocall_table_enclave, &ms);
	return status;
}

sgx_status_t ecall_get_client_outbound_minimum_buffer_size(sgx_enclave_id_t eid, int* result, uint16_t clino)
{
	sgx_status_t status;
	ms_ecall_get_client_outbound_minimum_buffer_size_t ms;
	ms.ms_result = result;
	ms.ms_clino = clino;
	status = sgx_ecall(eid, 11, &ocall_table_enclave, &ms);
	return status;
}

sgx_status_t ecall_process_client_inbound_data(sgx_enclave_id_t eid, bool* retval, client_result_t* result, uint16_t clino, buffer_t* src, buffer_t* dest)
{
	sgx_status_t status;
	ms_ecall_process_client_inbound_data_t ms;
	ms.ms_result = result;
	ms.ms_clino = clino;
	ms.ms_src = src;
	ms.ms_dest = dest;
	status = sgx_ecall(eid, 12, &ocall_table_enclave, &ms);
	if (status == SGX_SUCCESS && retval) *retval = ms.ms_retval;
	return status;
}

sgx_status_t ecall_retrieve_client_outbound_data(sgx_enclave_id_t eid, client_result_t* result, uint16_t clino, buffer_t* dst)
{
	sgx_status_t status;
	ms_ecall_retrieve_client_outbound_data_t ms;
	ms.ms_result = result;
	ms.ms_clino = clino;
	ms.ms_dst = dst;
	status = sgx_ecall(eid, 13, &ocall_table_enclave, &ms);
	return status;
}

sgx_status_t ecall_retrieve_outbound_messages(sgx_enclave_id_t eid, client_result_t* result, uint16_t clino)
{
	sgx_status_t status;
	ms_ecall_retrieve_outbound_messages_t ms;
	ms.ms_result = result;
	ms.ms_clino = clino;
	status = sgx_ecall(eid, 14, &ocall_table_enclave, &ms);
	return status;
}

sgx_status_t ecall_handle_forwarded_request(sgx_enclave_id_t eid, client_result_t* cliresult, bool* result, uint16_t clino, buffer_t* request)
{
	sgx_status_t status;
	ms_ecall_handle_forwarded_request_t ms;
	ms.ms_cliresult = cliresult;
	ms.ms_result = result;
	ms.ms_clino = clino;
	ms.ms_request = request;
	status = sgx_ecall(eid, 15, &ocall_table_enclave, &ms);
	return status;
}

sgx_status_t ecall_handle_request_executed(sgx_enclave_id_t eid, client_result_t* result, uint16_t clino, uint64_t invno, buffer_t* reply, bool replyfull)
{
	sgx_status_t status;
	ms_ecall_handle_request_executed_t ms;
	ms.ms_result = result;
	ms.ms_clino = clino;
	ms.ms_invno = invno;
	ms.ms_reply = reply;
	ms.ms_replyfull = replyfull;
	status = sgx_ecall(eid, 16, &ocall_table_enclave, &ms);
	return status;
}

sgx_status_t ecall_handle_reply(sgx_enclave_id_t eid, client_result_t* result, uint16_t clino, buffer_t* reply)
{
	sgx_status_t status;
	ms_ecall_handle_reply_t ms;
	ms.ms_result = result;
	ms.ms_clino = clino;
	ms.ms_reply = reply;
	status = sgx_ecall(eid, 17, &ocall_table_enclave, &ms);
	return status;
}

sgx_status_t ecall_verify_proposal(sgx_enclave_id_t eid, bool* result, uint32_t verifier, buffer_t* to_verify)
{
	sgx_status_t status;
	ms_ecall_verify_proposal_t ms;
	ms.ms_result = result;
	ms.ms_verifier = verifier;
	ms.ms_to_verify = to_verify;
	status = sgx_ecall(eid, 18, &ocall_table_enclave, &ms);
	return status;
}

sgx_status_t ecall_verify_proposals(sgx_enclave_id_t eid, bool* result, uint32_t verifier, buffer_t* to_verify)
{
	sgx_status_t status;
	ms_ecall_verify_proposals_t ms;
	ms.ms_result = result;
	ms.ms_verifier = verifier;
	ms.ms_to_verify = to_verify;
	status = sgx_ecall(eid, 19, &ocall_table_enclave, &ms);
	return status;
}


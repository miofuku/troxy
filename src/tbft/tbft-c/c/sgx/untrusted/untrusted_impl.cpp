/**
 * @author weichbr
 */

#include <string>
#include <unistd.h>
#include <climits>

#include "untrusted_impl.h"
#include "sgx_error.h"
#include "enclave_u.h"
#include "debug.h"
#include "sgx_uae_service.h"

uint64_t initialize_enclave(std::string encl)
{
	sgx_launch_token_t token = {0};
	sgx_status_t ret = SGX_ERROR_UNEXPECTED;
	int updated = 0;
	uint64_t eid;

	/* Step 2: call sgx_create_enclave to initialize an enclave instance */
	/* Debug Support: set 2nd parameter to 1 */
	ret = sgx_create_enclave(encl.c_str(), 1, &token, &updated, &eid, NULL);
	if (ret != SGX_SUCCESS) {
		debug("Error creating enclave: %x\n", ret);
		throw std::exception();
	}

	return eid;
}

SGXTroxy::SGXTroxy(troxy_conf_t &conf) : eid(0)
{
	debugE("");
	char pwd[1000];
	getcwd((char *) pwd, 1000);
	debug("%s\n", pwd);
	eid = initialize_enclave("/home/bijun/git/reptor/src/tbft/tbft-c/c/sgx/enclave.signed.so");
	ecall_init(eid, conf);

	client_pointers = (ptr_data_t *)calloc(conf.max_connections + conf.client_number_offset, sizeof(ptr_data_t));
	sendbuffers = (buffer_t * *)calloc(conf.max_connections + conf.client_number_offset, sizeof(buffer_t *));

	debugL("Created enclave with eid: %lu", eid);
}

void SGXTroxy::reset_handshake(handshake_result_t &result, uint16_t hsno, bool clear)
{
	debugE("");
	tryAgain:
	sgx_status_t s = ecall_reset_handshake(eid, &result, hsno, (uint8_t) clear);
	if (s != SGX_SUCCESS)
	{
		if (s == SGX_ERROR_OUT_OF_TCS)
		{
			printf("WARNING: Out of TCS, trying again!\n");fflush(stdout);
			goto tryAgain;
		}
		debugL("Error during ecall: 0x%x\n", s);
		throw std::exception();
	}
	debugL("");
}


void SGXTroxy::accept(handshake_result_t &result, uint16_t hsno)
{
	debugE("");
	tryAgain:
	sgx_status_t s = ecall_accept(eid, &result, hsno);
	if (s != SGX_SUCCESS)
	{
		if (s == SGX_ERROR_OUT_OF_TCS)
		{
			printf("WARNING: Out of TCS, trying again!\n");fflush(stdout);
			goto tryAgain;
		}
		debugL("Error during ecall: 0x%x\n", s);
		throw std::exception();
	}
	debugL("");
}

void SGXTroxy::get_handshake_inbound_minimum_buffer_size(int *result, uint16_t hsno)
{
	debugE("");
	tryAgain:
	sgx_status_t s = ecall_get_handshake_inbound_minimum_buffer_size(eid, result, hsno);
	if (s != SGX_SUCCESS)
	{
		if (s == SGX_ERROR_OUT_OF_TCS)
		{
			printf("WARNING: Out of TCS, trying again!\n");fflush(stdout);
			goto tryAgain;
		}
		debugL("Error during ecall: 0x%x\n", s);
		throw std::exception();
	}
	debugL("");
}

void SGXTroxy::get_handshake_outbound_minimum_buffer_size(int *result, uint16_t hsno)
{
	debugE("");
	tryAgain:
	sgx_status_t s = ecall_get_handshake_outbound_minimum_buffer_size(eid, result, hsno);
	if (s != SGX_SUCCESS)
	{
		if (s == SGX_ERROR_OUT_OF_TCS)
		{
			printf("WARNING: Out of TCS, trying again!\n");fflush(stdout);
			goto tryAgain;
		}
		debugL("Error during ecall: 0x%x\n", s);
		throw std::exception();
	}
	debugL("");
}

void SGXTroxy::process_handshake_inbound_data(handshake_result_t &result, uint16_t hsno, buffer_t &source)
{
	debugE("");
	tryAgain:
	sgx_status_t s = ecall_process_handshake_inbound_data(eid, &result, hsno, &source);
	if (s != SGX_SUCCESS)
	{
		if (s == SGX_ERROR_OUT_OF_TCS)
		{
			printf("WARNING: Out of TCS, trying again!\n");fflush(stdout);
			goto tryAgain;
		}
		debugL("Error during ecall: 0x%x\n", s);
		throw std::exception();
	}
	debugL("");
}

void SGXTroxy::retrieve_handshake_outbound_data(handshake_result_t &result, uint16_t hsno, buffer_t &destination)
{
	debugE("");
	tryAgain:
	sgx_status_t s = ecall_retrieve_handshake_outbound_data(eid, &result, hsno, &destination);
	if (s != SGX_SUCCESS)
	{
		if (s == SGX_ERROR_OUT_OF_TCS)
		{
			printf("WARNING: Out of TCS, trying again!\n");fflush(stdout);
			goto tryAgain;
		}
		debugL("Error during ecall: 0x%x\n", s);
		throw std::exception();
	}
	debugL("");
}

void SGXTroxy::save_state(short hsno)
{
	debugE("");
	tryAgain:
	sgx_status_t s = ecall_save_state(eid, hsno);
	if (s != SGX_SUCCESS)
	{
		if (s == SGX_ERROR_OUT_OF_TCS)
		{
			printf("WARNING: Out of TCS, trying again!\n");fflush(stdout);
			goto tryAgain;
		}
		debugL("Error during ecall: 0x%x\n", s);
		throw std::exception();
	}
	debugL("");
}

void SGXTroxy::open(client_result_t &result, uint16_t clino)
{
	debugE("");
	tryAgain:
	sgx_status_t s = ecall_open(eid, &result, clino);
	if (s != SGX_SUCCESS)
	{
		if (s == SGX_ERROR_OUT_OF_TCS)
		{
			printf("WARNING: Out of TCS, trying again!\n");fflush(stdout);
			goto tryAgain;
		}
		debugL("Error during ecall: 0x%x\n", s);
		throw std::exception();
	}
	debugL("");
}


void SGXTroxy::init_client(uint16_t clino, buffer_t *sendbuffer)
{
	debugE("");

	if (sendbuffer == nullptr)
	{
		sendbuffer = (buffer_t *) malloc(sizeof(buffer_t));
		sendbuffer->offset = 0;
		sendbuffer->length = SENDBUFFER_SIZE;
		sendbuffer->arr = (uint8_t *) calloc(1, SENDBUFFER_SIZE);
		sendbuffer->arr_length = SENDBUFFER_SIZE;
	}
	sendbuffers[clino] = sendbuffer;

	tryAgain:
	sgx_status_t s = ecall_init_client(eid, clino, sendbuffer);
	if (s != SGX_SUCCESS)
	{
		if (s == SGX_ERROR_OUT_OF_TCS)
		{
			printf("WARNING: Out of TCS, trying again!\n");fflush(stdout);
			goto tryAgain;
		}
		debugL("Error during ecall: 0x%x\n", s);
		throw std::exception();
	}
	debugL("");
}

void SGXTroxy::get_client_inbound_minimum_buffer_size(int *result, uint16_t clino)
{
	debugE("");
	tryAgain:
	sgx_status_t s = ecall_get_client_inbound_minimum_buffer_size(eid, result, clino);
	if (s != SGX_SUCCESS)
	{
		if (s == SGX_ERROR_OUT_OF_TCS)
		{
			printf("WARNING: Out of TCS, trying again!\n");fflush(stdout);
			goto tryAgain;
		}
		debugL("Error during ecall: 0x%x\n", s);
		throw std::exception();
	}
	debugL("");
}

void SGXTroxy::get_client_outbound_minimum_buffer_size(int *result, uint16_t clino)
{
	debugE("");
	tryAgain:
	sgx_status_t s = ecall_get_client_outbound_minimum_buffer_size(eid, result, clino);
	if (s != SGX_SUCCESS)
	{
		if (s == SGX_ERROR_OUT_OF_TCS)
		{
			printf("WARNING: Out of TCS, trying again!\n");fflush(stdout);
			goto tryAgain;
		}
		debugL("Error during ecall: 0x%x\n", s);
		throw std::exception();
	}
	debugL("");
}


void SGXTroxy::process_client_inbound_data(client_result_t &result, uint16_t clino, buffer_t &src, buffer_t &dest)
{
	debugE("");
	bool throwIOe = false;
	tryAgain:
	sgx_status_t s = ecall_process_client_inbound_data(eid, &throwIOe, &result, clino, &src, &dest);
	if (s != SGX_SUCCESS)
	{
		if (s == SGX_ERROR_OUT_OF_TCS)
		{
			printf("WARNING: Out of TCS, trying again!\n");fflush(stdout);
			goto tryAgain;
		}
		debugL("Error during ecall: 0x%x\n", s);
		throw std::exception();
	}
	if (throwIOe)
	{
		throw IOException();
	}
	debugL("");
}

void SGXTroxy::retrieve_client_outbound_data(client_result_t &result, uint16_t clino, buffer_t &dst)
{
	debugE("");

	buffer_t *sendbuffer = sendbuffers[clino];

	result.reqmsgbuf = INT_MAX;
	result.req_outbound_buffer_size = INT_MAX;
	result.can_process_inbound = sink_status_t::CAN_PROCESS;

	if (__builtin_expect(sendbuffer->offset > 0, true))
	{
		debug("Sending buffered stuff\n");

		uint32_t offset = sendbuffer->offset;
		debug("%d bytes\n", offset);
		memcpy(dst.arr + dst.offset, sendbuffer->arr, offset);

		dst.offset += offset;
		sendbuffer->length = sendbuffer->length + offset;
		sendbuffer->offset = 0;

	}

	debugL("");
}

void SGXTroxy::retrieve_outbound_messages(client_result_t &result, uint16_t clino)
{
	debugE("");
	tryAgain:
	sgx_status_t s = ecall_retrieve_outbound_messages(eid, &result, clino);
	if (s != SGX_SUCCESS)
	{
		if (s == SGX_ERROR_OUT_OF_TCS)
		{
			printf("WARNING: Out of TCS, trying again!\n");fflush(stdout);
			goto tryAgain;
		}
		debugL("Error during ecall: 0x%x\n", s);
		throw std::exception();
	}
	debugL("");
}

void SGXTroxy::handle_forwarded_request(client_result_t &cliresult, verification_result_t &result, uint16_t clino, buffer_t &request)
{
	debugE("");
	bool r;
	tryAgain:
	sgx_status_t s = ecall_handle_forwarded_request(eid, &cliresult, &r, clino, &request);
	if (s != SGX_SUCCESS)
	{
		if (s == SGX_ERROR_OUT_OF_TCS)
		{
			printf("WARNING: Out of TCS, trying again!\n");fflush(stdout);
			goto tryAgain;
		}
		debugL("Error during ecall: 0x%x\n", s);
		throw std::exception();
	}
	result = r ? verification_result_t::OK : verification_result_t::FAIL;
	debugL("");
}

void SGXTroxy::handle_request_executed(client_result_t &result, uint16_t clino, uint64_t invno, buffer_t &reply, bool replyfull)
{
	debugE("");
	tryAgain:
	sgx_status_t s = ecall_handle_request_executed(eid, &result, clino, invno, &reply, replyfull);
	if (s != SGX_SUCCESS)
	{
		if (s == SGX_ERROR_OUT_OF_TCS)
		{
			printf("WARNING: Out of TCS, trying again!\n");fflush(stdout);
			goto tryAgain;
		}
		debugL("Error during ecall: 0x%x\n", s);
		throw std::exception();
	}
	debugL("");
}

bool SGXTroxy::handle_reply(client_result_t &result, uint16_t clino, buffer_t &reply)
{
	debugE("");
	tryAgain:
	sgx_status_t s = ecall_handle_reply(eid, &result, clino, &reply);
	if (s != SGX_SUCCESS)
	{
		if (s == SGX_ERROR_OUT_OF_TCS)
		{
			printf("WARNING: Out of TCS, trying again!\n");fflush(stdout);
			goto tryAgain;
		}
		debugL("Error during ecall: 0x%x\n", s);
		throw std::exception();
	}
	return false;
	debugL("");
}

void SGXTroxy::verify_proposal(verification_result_t &result, uint32_t verifier, buffer_t &to_verify)
{
	debugE("");
	bool r;
	tryAgain:
	sgx_status_t s = ecall_verify_proposal(eid, &r, verifier, &to_verify);
	if (s != SGX_SUCCESS)
	{
		if (s == SGX_ERROR_OUT_OF_TCS)
		{
			printf("WARNING: Out of TCS, trying again!\n");fflush(stdout);
			goto tryAgain;
		}
		debugL("Error during ecall: 0x%x\n", s);
		throw std::exception();
	}
	result = r ? verification_result_t::OK : verification_result_t::FAIL;
	debugL("");
}

void SGXTroxy::verify_proposals(verification_result_t &result, uint32_t verifier, buffer_t &to_verify)
{
	debugE("");
	bool r;
	tryAgain:
	sgx_status_t s = ecall_verify_proposals(eid, &r, verifier, &to_verify);
	if (s != SGX_SUCCESS)
	{
		if (s == SGX_ERROR_OUT_OF_TCS)
		{
			printf("WARNING: Out of TCS, trying again!\n");fflush(stdout);
			goto tryAgain;
		}
		debugL("Error during ecall: 0x%x\n", s);
		throw std::exception();
	}
	result = r ? verification_result_t::OK : verification_result_t::FAIL;
	debugL("");
}


void SGXTroxy::savePointer(uint16_t clino, uint8_t type, uint8_t *ptr)
{
	client_pointers[clino].ptr[type] = ptr;
}

uint8_t* SGXTroxy::getPointer(uint16_t clino, uint8_t type)
{
	return client_pointers[clino].ptr[type];
}


void ocall_print_string(const char *b)
{
	printf("%s", b);
	fflush(stdout);
}

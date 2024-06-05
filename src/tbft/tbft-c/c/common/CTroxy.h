/**
 * @author weichbr
 */

#ifndef TBFT_C_CTROXY_H
#define TBFT_C_CTROXY_H

#define SENDBUFFER_SIZE (5000 * 1024)

#include "HandshakeResult.h"
#include "Buffer.h"
#include "ClientResult.h"
#ifndef __cplusplus
#include <stdbool.h>
#endif

#ifdef __cplusplus
enum class verification_result_t
#else
enum verification_result_t
#endif
{
	OK,
	FAIL,
};

typedef struct __troxy_conf
{
	uint8_t replica_number;
	uint32_t client_number_offset;
	bool distributed_contacts;
	uint32_t max_concurrent_handshakes;
	uint32_t max_connections;
	uint32_t invocation_window;
	uint32_t verifiers;
	bool use_ssl;
	uint8_t *certarr;
	size_t certlen;
	uint8_t *keyarr;
	size_t keylen;
	bool use_app_handshake;
} troxy_conf_t;

typedef struct __ptr_data
{
	uint8_t *ptr[3];
} ptr_data_t;

#ifdef __cplusplus

class IOException : public std::exception
{
public:
	IOException() {};
	virtual ~IOException() {};
};

class CTroxy
{
public:
	virtual void reset_handshake(handshake_result_t &result, uint16_t hsno, bool clear) = 0;
	virtual void accept(handshake_result_t &result, uint16_t hsno) = 0;
	virtual void get_handshake_inbound_minimum_buffer_size(int *result, uint16_t hsno) = 0;
	virtual void get_handshake_outbound_minimum_buffer_size(int *result, uint16_t hsno) = 0;
	virtual void process_handshake_inbound_data(handshake_result_t &result, uint16_t hsno, buffer_t &source) = 0;
	virtual void retrieve_handshake_outbound_data(handshake_result_t &result, uint16_t hsno, buffer_t &destination) = 0;
	virtual void save_state(short hsno) = 0;

	virtual void open(client_result_t &result, uint16_t clino) = 0;
	virtual void init_client(uint16_t clino, buffer_t *sendbuffer) = 0;
	virtual void get_client_inbound_minimum_buffer_size(int *result, uint16_t clino) = 0;
	virtual void get_client_outbound_minimum_buffer_size(int *result, uint16_t clino) = 0;
	virtual void process_client_inbound_data(client_result_t &result, uint16_t clino, buffer_t &src, buffer_t &dest) = 0;
	virtual void retrieve_client_outbound_data(client_result_t &result, uint16_t clino, buffer_t &dst) = 0;

	virtual void retrieve_outbound_messages(client_result_t &result, uint16_t clino) = 0;

	virtual void handle_forwarded_request(client_result_t &cliresult, verification_result_t &result, uint16_t clino, buffer_t &request) = 0;
	virtual void handle_request_executed(client_result_t &result, uint16_t clino, uint64_t invno, buffer_t &reply, bool replyfull) = 0;
	virtual bool handle_reply(client_result_t &result, uint16_t clino, buffer_t &reply) = 0;
	virtual void verify_proposal(verification_result_t &result, uint32_t verifier, buffer_t &to_verify) = 0;
	virtual void verify_proposals(verification_result_t &result, uint32_t verifier, buffer_t &to_verify) = 0;

	virtual void savePointer(uint16_t clino, uint8_t type, uint8_t *ptr) = 0;
	virtual uint8_t* getPointer(uint16_t clino, uint8_t type) = 0;
};

#endif

#endif //TBFT_C_CTROXY_H

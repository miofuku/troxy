/**
 * @author weichbr
 */

#ifndef TBFT_C_UNTRUSTED_IMPL_H_H
#define TBFT_C_UNTRUSTED_IMPL_H_H

#include "CTroxy.h"

class SGXTroxy : public CTroxy
{
public:
	SGXTroxy(troxy_conf_t &conf);

	virtual ~SGXTroxy() {};

	virtual void reset_handshake(handshake_result_t &result, uint16_t hsno, bool clear);

	virtual void accept(handshake_result_t &result, uint16_t hsno);

	virtual void get_handshake_inbound_minimum_buffer_size(int *result, uint16_t hsno);

	virtual void get_handshake_outbound_minimum_buffer_size(int *result, uint16_t hsno);

	virtual void process_handshake_inbound_data(handshake_result_t &result, uint16_t hsno, buffer_t &source);

	virtual void retrieve_handshake_outbound_data(handshake_result_t &result, uint16_t hsno, buffer_t &destination);

	virtual void save_state(short hsno);

	virtual void open(client_result_t &result, uint16_t clino);

	virtual void init_client(uint16_t clino, buffer_t *sendbuffer);

	virtual void get_client_inbound_minimum_buffer_size(int *result, uint16_t clino);

	virtual void get_client_outbound_minimum_buffer_size(int *result, uint16_t clino);

	virtual void process_client_inbound_data(client_result_t &result, uint16_t clino, buffer_t &src, buffer_t &dest);

	virtual void retrieve_client_outbound_data(client_result_t &result, uint16_t clino, buffer_t &dst);

	virtual void retrieve_outbound_messages(client_result_t &result, uint16_t clino);

	virtual void handle_forwarded_request(client_result_t &cliresult, verification_result_t &result, uint16_t clino, buffer_t &request);

	virtual void handle_request_executed(client_result_t &result, uint16_t clino, uint64_t invno, buffer_t &reply, bool replyfull);

	virtual bool handle_reply(client_result_t &result, uint16_t clino, buffer_t &reply);

	virtual void verify_proposal(verification_result_t &result, uint32_t verifier, buffer_t &to_verify);

	virtual void verify_proposals(verification_result_t &result, uint32_t verifier, buffer_t &to_verify);

	void savePointer(uint16_t clino, uint8_t type, uint8_t *ptr);
	uint8_t* getPointer(uint16_t clino, uint8_t type);

private:
	uint64_t eid;
	ptr_data_t *client_pointers;
	buffer_t **sendbuffers;
};

#endif //TBFT_C_UNTRUSTED_IMPL_H_H

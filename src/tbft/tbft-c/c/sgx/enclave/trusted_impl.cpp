/**
 * @author weichbr
 *
 * This functions wraps the enclavised native troxy and provides its interface as ECalls.
 * Source buffers need to be copied into the enclave as they might bo modified by an attacker during read.
 * Destination buffers do NOT need to be copied, as they are just written to be the implementation in one go.
 * They shall ONLY be written to and not read from as an attacker might change the contents.
 * Result objects are also not copied as they are only written to.
 */

#include "HandshakeResult.h"
#include "enclave_t.h"
#include "dummies.h"
#include "native_impl.h"

NativeTroxy *troxy = nullptr;

void ecall_init(troxy_conf_t conf)
{
	debugE("");
	troxy = new NativeTroxy(conf);
	debugL("");
}

void ecall_reset_handshake(handshake_result_t *hres, uint16_t hsno, uint8_t clear)
{
	debugE("")
	troxy->reset_handshake(*hres, hsno, clear);
	debugL("");
}

void ecall_accept(handshake_result_t *result, uint16_t hsno)
{
	debugE("")
	troxy->accept(*result, hsno);
	debugL("");
}

void ecall_get_handshake_inbound_minimum_buffer_size(int *result, uint16_t hsno)
{
	debugE("")
	troxy->get_handshake_inbound_minimum_buffer_size(result, hsno);
	debugL("");
}

void ecall_get_handshake_outbound_minimum_buffer_size(int *result, uint16_t hsno)
{
	debugE("")
	troxy->get_handshake_outbound_minimum_buffer_size(result, hsno);
	debugL("");
}

void ecall_process_handshake_inbound_data(handshake_result_t *result, uint16_t hsno, buffer_t *source)
{
	debugE("")

	uint32_t len = source->length;
	uint32_t off = source->offset;

	uint8_t srcarr[len];
	buffer_t _src = {(uint8_t *) &srcarr, len, 0, len};
	memcpy(srcarr, source->arr + off, len);

	troxy->process_handshake_inbound_data(*result, hsno, _src);

	buffer_advance(*source, _src.offset);

	debugL("");
}

void ecall_retrieve_handshake_outbound_data(handshake_result_t *result, uint16_t hsno, buffer_t *destination)
{
	debugE("")
	troxy->retrieve_handshake_outbound_data(*result, hsno, *destination);
	debugL("");
}

void ecall_save_state(short hsno)
{
	debugE("")
	troxy->save_state(hsno);
	debugL("");
}

void ecall_open(client_result_t *result, uint16_t clino)
{
	debugE("");
	troxy->open(*result, clino);
	debugL("");
}

void ecall_init_client(uint16_t clino, buffer_t *sendbuffer)
{
	debugE("");
	troxy->init_client(clino, sendbuffer);
	debugL("");
}

void ecall_get_client_inbound_minimum_buffer_size(int *result, uint16_t clino)
{
	debugE("");
	troxy->get_client_inbound_minimum_buffer_size(result, clino);
	debugL("");
}

void ecall_get_client_outbound_minimum_buffer_size(int *result, uint16_t clino)
{
	debugE("");
	troxy->get_client_outbound_minimum_buffer_size(result, clino);
	debugL("");
}

bool ecall_process_client_inbound_data(client_result_t *result, uint16_t clino, buffer_t *src, buffer_t *dest)
{
	debugE("");

	uint32_t len = src->length;
	uint32_t off = src->offset;

	uint8_t srcarr[len];
	buffer_t _src = {(uint8_t *) &srcarr, len, 0, len};
	memcpy(srcarr, src->arr + off, len);

	try
	{
		troxy->process_client_inbound_data(*result, clino, _src, *dest);
	}
	catch (IOException& e)
	{
		buffer_advance(*src, _src.offset);
		return true;
	}

	buffer_advance(*src, _src.offset);

	debugL("");
	return false;
}

void ecall_retrieve_client_outbound_data(client_result_t *result, uint16_t clino, buffer_t *dst)
{
	debugE("");
	troxy->retrieve_client_outbound_data(*result, clino, *dst);
	debugL("");
}

void ecall_retrieve_outbound_messages(client_result_t *result, uint16_t clino)
{
	debugE("");
	troxy->retrieve_outbound_messages(*result, clino);
	debugL("");
}

void ecall_handle_forwarded_request(client_result_t *cliresult, bool *result, uint16_t clino, buffer_t *request)
{
	debugE("");

	uint32_t len = request->length;
	uint32_t off = request->offset;

	uint8_t srcarr[len];
	buffer_t _src = {(uint8_t *) &srcarr, len, 0, len};
	memcpy(srcarr, request->arr + off, len);

	verification_result_t r;
	troxy->handle_forwarded_request(*cliresult, r, clino, _src);
	*result = (r == verification_result_t::OK);
	debugL("");
}

void ecall_handle_request_executed(client_result_t *result, uint16_t clino, uint64_t invno, buffer_t *reply, bool replyfull)
{
	debugE("");

	uint32_t len = reply->length;
	uint32_t off = reply->offset;

	uint8_t srcarr[len];
	buffer_t _src = {(uint8_t *) &srcarr, len, 0, len};
	memcpy(srcarr, reply->arr + off, len);

	troxy->handle_request_executed(*result, clino, invno, _src, replyfull);
	debugL("");
}

void ecall_handle_reply(client_result_t *result, uint16_t clino, buffer_t *reply)
{
	debugE("");

	uint32_t len = reply->length;
	uint32_t off = reply->offset;

	uint8_t srcarr[len];
	buffer_t _src = {(uint8_t *) &srcarr, len, 0, len};
	memcpy(srcarr, reply->arr + off, len);

	while(_src.length > 0 && troxy->handle_reply(*result, clino, _src));
	debugL("");
}

void ecall_verify_proposal(bool *result, uint32_t verifier, buffer_t *to_verify)
{
	debugE("");
	verification_result_t r;
	troxy->verify_proposal(r, verifier, *to_verify);
	*result = (r == verification_result_t::OK);
	debugL("");
}

void ecall_verify_proposals(bool *result, uint32_t verifier, buffer_t *to_verify)
{
	debugE("");
	verification_result_t r;
	troxy->verify_proposals(r, verifier, *to_verify);
	*result = (r == verification_result_t::OK);
	debugL("");
}

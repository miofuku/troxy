/**
 * @author weichbr
 */

#ifdef SGX
#include "dummies.h"
#endif


#include "native_impl.h"
#include <cassert>
#include <openssl/err.h>
#include <limits.h>
#include "BIO.h"
#include "defaultprotocolparser.h"
#include "httpprotocolparser.h"
#include "cache.h"
#include "../common/Buffer.h"

/**
 * @brief Check OpenSSL version, as we target <1.1.0
 * See https://www.openssl.org/docs/man1.0.2/crypto/SSLeay.html
 */
#ifdef OPENSSL_VERSION_NUMBER
#if OPENSSL_VERSION_NUMBER > 0x10100000
#warning "OpenSSL version >=1.1.0!"
/*#elif OPENSSL_VERSION_NUMBER > 0x10002000
#warning "OpenSSL version >=1.0.2!"
#elif OPENSSL_VERSION_NUMBER > 0x10001000
#warning "OpenSSL version >=1.0.1!"
#elif OPENSSL_VERSION_NUMBER > 0x10000000
#warning "OpenSSL version >=1.0.0!"
*/
#endif
#else
#error "OpenSSL not found!"
#endif


NativeTroxy::NativeTroxy(troxy_conf_t &conf) : replica_number(conf.replica_number),
                                               client_number_offset(conf.client_number_offset),
                                               inv_window(conf.invocation_window),
                                               distributed_contacts(conf.distributed_contacts),
                                               client_pointers(nullptr),
                                               handshakes(conf.max_concurrent_handshakes),
                                               use_app_handshake(conf.use_app_handshake),
                                               connections(conf.max_connections),
                                               use_ssl(conf.use_ssl),
                                               ssl_ctx(nullptr)
{
	debugE("");
	debug("HS: %d\n", conf.max_concurrent_handshakes);
	debug("CS: %d\n", conf.max_connections);
	debug("CO: %d\n", conf.client_number_offset);
	debug("IW: %d\n", conf.invocation_window);
	debug("DC: %s\n", conf.distributed_contacts ? "true" : "false");
	if (conf.use_ssl)
	{
		SSL_library_init();
		OPENSSL_add_all_algorithms_noconf();
		SSL_load_error_strings();
		const SSL_METHOD *method = SSLv23_server_method();
		ssl_ctx = SSL_CTX_new(method);

		if (ssl_ctx == nullptr)
		{
			printf("Could not create context!\n");
			fflush(stdout);
			ERR_print_errors_fp(stdout);
			throw std::exception();
		}

		X509 *cert = nullptr;
		debug("Decoding cert\n");
		if (d2i_X509(&cert, (const unsigned char **) &conf.certarr, conf.certlen) == nullptr)
		{
			printf("Could not decode certificate!\n");
			fflush(stdout);
			ERR_print_errors_fp(stdout);
			throw std::exception();
		}
		debug("Setting cert\n");
		if (SSL_CTX_use_certificate(ssl_ctx, cert) <= 0)
		{
			printf("Could not set certificate!\n");
			fflush(stdout);
			ERR_print_errors_fp(stdout);
			throw std::exception();
		}

		EVP_PKEY *key = nullptr;
		PKCS8_PRIV_KEY_INFO *pkcs = nullptr;
		debug("Decoding key\n");
		if(d2i_PKCS8_PRIV_KEY_INFO(&pkcs, (const unsigned char **) &conf.keyarr, conf.keylen) == nullptr)
		{
			printf("Could not decode key!\n");
			fflush(stdout);
			ERR_print_errors_fp(stdout);
			throw std::exception();
		}
		debug("Setting key\n");
		if ((key = EVP_PKCS82PKEY(pkcs)) == nullptr)
		{
			printf("Could set RSA key!\n");
			fflush(stdout);
			ERR_print_errors_fp(stdout);
			throw std::exception();
		}
		debug("Using key\n");
		if (SSL_CTX_use_PrivateKey(ssl_ctx, key) <= 0)
		{
			printf("Could not set private key!\n");
			fflush(stdout);
			ERR_print_errors_fp(stdout);
			throw std::exception();
		}
		debug("Checking key\n");
		if (!SSL_CTX_check_private_key(ssl_ctx))
		{
			printf("Could not check private key!\n");
			fflush(stdout);
			ERR_print_errors_fp(stdout);
			throw std::exception();
		}
		debug("Disabling session cache\n");
		SSL_CTX_set_session_cache_mode(ssl_ctx, SSL_SESS_CACHE_OFF);
		SSL_CTX_set_options(ssl_ctx, SSL_OP_NO_TICKET);
	}

	hash_function = EVP_sha256();
	hash_size = (uint32_t) EVP_MD_size(hash_function);

	verifiers.reserve(conf.verifiers);
	for (uint32_t i = 0; i <= conf.verifiers; ++i)
	{
		verifiers.push_back(HMAC_CTX());
		HMAC_CTX_init(&verifiers[i]);
		HMAC_Init_ex(&verifiers[i], key, key_size, hash_function, nullptr);
	}

#ifndef SGX
    client_pointers = (ptr_data_t *)calloc(conf.max_connections + conf.client_number_offset, sizeof(ptr_data_t));
#endif

    if (distributed_contacts)
    {
        next_clino = 1;
    }
    else
    {
        next_clino = 3;
    }
    debugL("");
}


void NativeTroxy::reset_handshake(handshake_result_t &result, uint16_t hsno, bool clear)
{
    debugE("(%p, %d, %s)", &result, hsno, clear ? "true" : "false");
    assert(hsno < handshakes.size());

    result.can_process_inbound = sink_status_t::CAN_PROCESS;
    result.is_finished = false;
    result.network_id = 0;
    result.remote_id = -1;
    result.req_outbound_buffer_size = INT_MAX;

    if (!clear)
    {
        debug("Reset without clear\n");
        return;
    }

    handshake_data_t &hsdata = handshakes.at(hsno);

    hsdata.state = handshake_state_t::NONE;
    SSL *prev = hsdata.data.ssl;
    hsdata.data.ssl = nullptr;
    if (prev != nullptr)
    {
        SSL_free(prev);
    }
    hsdata.data.clino = 0;
    hsdata.data.to_recv = INT_MAX;
    hsdata.data.recvbuf = nullptr;
    hsdata.data.sendbuf = (buffer_t *) malloc(sizeof(buffer_t));
    hsdata.data.sendbuf->offset = 0;
    hsdata.data.sendbuf->length = 16 * 1024;
    hsdata.data.sendbuf->arr = (uint8_t *) calloc(16, 1024);
    hsdata.data.sendbuf->arr_length = 16 * 1024;
    hsdata.data.clino = -1;
    debugL("");
}

void NativeTroxy::accept(handshake_result_t &result, uint16_t hsno)
{
    debugE("");
    assert(hsno < handshakes.size());

    handshake_data_t &hsdata = handshakes.at(hsno);
    result.can_process_inbound = sink_status_t::CAN_PROCESS;
    result.is_finished = false;
    result.network_id = 0;
    result.remote_id = -1;
    result.req_outbound_buffer_size = INT_MAX;

    switch (hsdata.state)
    {
        case handshake_state_t::NONE:
        {
            if (use_ssl)
            {
                debug("Creating SSL struct\n");
                assert(ssl_ctx != nullptr);

                // Create new SSL struct
                SSL *ssl = SSL_new(ssl_ctx);
                BIO *bio = BIO_new(&__bio_buffer);
                bio->ptr = (void *)&hsdata.data;
                SSL_set_bio(ssl, bio, bio);
                hsdata.data.ssl = ssl;
                hsdata.data.bio = bio;

                debug("Now in handshake state SSL\n");
                hsdata.state = handshake_state_t::SSL;
                break;
            }

            if (use_app_handshake)
            {
                hsdata.state = handshake_state_t::APP_P1;
                break;
            }
            result.is_finished = true;
            result.remote_id = next_client_number();
            hsdata.data.clino = result.remote_id;
            hsdata.state = handshake_state_t::DONE;
            break;
        }
        case handshake_state_t::SSL:
        case handshake_state_t::APP_P1:
        case handshake_state_t::APP_P2:
        case handshake_state_t::DONE:
            // Somethings wrong, we can't accept, when we are already doing a handshake
            throw std::exception();
    }
    debugL("");
}

void NativeTroxy::process_handshake_inbound_data(handshake_result_t &result, uint16_t hsno, buffer_t &source)
{
    debugE("(%d)", hsno);
    assert(hsno < handshakes.size());

    handshake_data_t &hsdata = handshakes.at(hsno);
    result.can_process_inbound = sink_status_t::JNULL;
    result.is_finished = false;
    result.network_id = 0;
    result.remote_id = hsdata.data.clino;
    result.req_outbound_buffer_size = INT_MAX;

    switch (hsdata.state)
    {
        case handshake_state_t::NONE:
        {
            debug("State NONE, so nothing to receive\n");
            break;
        }
        case handshake_state_t::SSL:
        {
            debug("State SSL, lets process some data!\n");
            hsdata.data.recvbuf = &source;
            hsdata.data.to_recv = INT_MAX;
            debug("Lets do SSL_accept\n");
            int ret = 0;
            ERR_clear_error();
            if ((ret = SSL_accept(hsdata.data.ssl)) != 1)
            {
                int error = SSL_get_error(hsdata.data.ssl, ret);
                if (error == 1)
                {
                    debug("ERROR during SSL_accept\n");
                    unsigned long err = ERR_get_error();
                    char *b = new char[1024];
                    ERR_error_string_n(err, b, 1024);
                    printf("Errorqueue error: %s (%lu)\n", b, err);
                    throw std::exception();
                }
                if (error == 5)
                {
                    // BIO has set what OpenSSL wanted to do, so lets find out
                    debug("OpenSSL could not ");
                    if (hsdata.data.sendbuf->offset > 0)
                    {
                        debug("write\n");
                        result.req_outbound_buffer_size = hsdata.data.sendbuf->offset;
                        result.can_process_inbound = sink_status_t::BLOCKED;
                        break;
                    }
                    if (hsdata.data.to_recv != INT_MAX)
                    {
                        debug("read\n");
                        result.can_process_inbound = sink_status_t::CAN_PROCESS;
                        break;
                    }
                    debug(" do something else\n");
                    throw std::exception();
                }
                debug("Other error happened!\n");
                throw std::exception();
            }
            debug("SSL Handshake done\n");

            result.req_outbound_buffer_size = hsdata.data.sendbuf->offset;

            if (use_app_handshake)
            {
                debug("Continue with APP handshake\n");
                hsdata.state = handshake_state_t::APP_P1;
                result.can_process_inbound = sink_status_t::CAN_PROCESS;
                break;
            }
            debug("All Handshakes done\n");
            hsdata.state = handshake_state_t::DONE;
            result.is_finished = true;
            result.can_process_inbound = sink_status_t::CAN_PROCESS;
            result.remote_id = next_client_number();
            hsdata.data.clino = result.remote_id;
            break;
        }
        case handshake_state_t::APP_P1:
        {
            debug("State APP, lets process some data!\n");
            hsdata.data.recvbuf = &source;
            hsdata.data.to_recv = INT_MAX;
            buffer_t *hsbuf = &source;
            debug("Able to read %d bytes\n", hsbuf->length);
            int read = 0;
            if (use_ssl)
            {
                uint8_t _hs[APP_HANDSHAKE_IN_BYTES];
                buffer_t _hsbuf = {};
                hsbuf = &_hsbuf;
                buffer_construct_from_array(_hsbuf, _hs, APP_HANDSHAKE_IN_BYTES);
                read = readBytesSSL(hsdata.data, _hsbuf);
                if (hsdata.data.sendbuf->offset > 0)
                {
                    // Need to write something
                    result.req_outbound_buffer_size = hsdata.data.sendbuf->offset;
                    result.can_process_inbound = sink_status_t::BLOCKED;
                    debugL("");
                    return;
                }
                else if (read == 0)
                {
                    // Need more data
                    result.can_process_inbound = sink_status_t::CAN_PROCESS;
                    debugL("");
                    return;
                }
            }
            else
            {
                debug("No SSL, so we just read the bytes normally\n");
                // First two bytes are client number
                if (hsbuf->length < APP_HANDSHAKE_IN_BYTES)
                {
                    result.can_process_inbound = sink_status_t::WAIT_FOR_DATA;
                    debug("Need more bytes: %d < %d\n", hsbuf->length, APP_HANDSHAKE_IN_BYTES);
                    break;
                }
                buffer_advance(*hsbuf, APP_HANDSHAKE_IN_BYTES);
            }

            buffer_rewind(*hsbuf, APP_HANDSHAKE_IN_BYTES);

            if (!buffer_is_handshake(*hsbuf))
            {
                // TODO: Somethings wrong. Abort.
                debug("No magic, abort.\n");
            }
            hsdata.data.clino = buffer_as_handshake_get_clino(*hsbuf);
            hexdump((char *) hsbuf->arr+hsbuf->offset, APP_HANDSHAKE_IN_BYTES);
            debug("Client %d connected\n", hsdata.data.clino);
            result.can_process_inbound = sink_status_t::BLOCKED;
            result.remote_id = hsdata.data.clino;
            debug("Need to send %d byte(s) back\n", APP_HANDSHAKE_OUT_BYTES);
            buffer_advance(*hsbuf, APP_HANDSHAKE_IN_BYTES);

            if (use_ssl)
            {
                uint8_t buf[APP_HANDSHAKE_OUT_BYTES] = {0x00};
                buffer_t tmp = {};
                buffer_construct_from_array(tmp, (uint8_t *) &buf, APP_HANDSHAKE_OUT_BYTES);
                int written = writeBytesSSL(hsdata.data, tmp);
                if (written < 0)
                {
                    // Need to write something
                    result.req_outbound_buffer_size = hsdata.data.sendbuf->offset;
                    result.can_process_inbound = sink_status_t::BLOCKED;
                    debugL("");
                    return;
                }
                else if (written == 0)
                {
                    // Need more data
                    result.can_process_inbound = sink_status_t::CAN_PROCESS;
                    debugL("");
                    return;
                }
            }
            else
            {
                buffer_add_byte(*hsdata.data.sendbuf, 0x00);
                result.req_outbound_buffer_size = APP_HANDSHAKE_OUT_BYTES;
            }

            break;
        }
        case handshake_state_t::APP_P2:
            hsdata.state = handshake_state_t::DONE;
            result.is_finished = true;
            break;
        case handshake_state_t::DONE:
            throw std::exception();
    }

    debugL("");
}

void NativeTroxy::retrieve_handshake_outbound_data(handshake_result_t &result, uint16_t hsno, buffer_t &destination)
{
    debugE("");
    assert(hsno < handshakes.size());

    handshake_data_t &hsdata = handshakes.at(hsno);

    result.can_process_inbound = sink_status_t::JNULL; // FIXME: is this right?
    result.is_finished = false;
    result.network_id = 0;
    result.remote_id = hsdata.data.clino;
    result.req_outbound_buffer_size = INT_MAX;

    if (hsdata.data.sendbuf->offset > 0)
    {
        debug("Sending buffered stuff\n");
//		buffer_add_buffer(destination, *hsdata.data.sendbuf);

        uint32_t offset = hsdata.data.sendbuf->offset;
        memcpy(destination.arr + destination.offset, hsdata.data.sendbuf->arr, offset);

        destination.offset += offset;
        hsdata.data.sendbuf->length = hsdata.data.sendbuf->length + offset;
        hsdata.data.sendbuf->offset = 0;

        result.can_process_inbound = sink_status_t::CAN_PROCESS;
        return;
    }

    switch (hsdata.state)
    {
        case handshake_state_t::NONE:
        {
            debug("State NONE, so nothing to send\n");
            break;
        }
        case handshake_state_t::SSL:
        {
            debug("State SSL, lets see if we need to send something\n");
            hsdata.data.recvbuf = nullptr;
            hsdata.data.to_recv = INT_MAX;
            debug("Lets do SSL_accept\n");
            int ret = 0;
            ERR_clear_error();
            if ((ret = SSL_accept(hsdata.data.ssl)) != 1)
            {
                int error = SSL_get_error(hsdata.data.ssl, ret);
                if (error == 1)
                {
                    debug("ERROR\n");
                    unsigned long err = ERR_get_error();
                    char *b = new char[1024];
                    ERR_error_string_n(err, b, 1024);
                    printf("Errorqueue error: %s (%lu)\n", b, err);
                    throw std::exception();
                }
                if (error == 5)
                {
                    // BIO has set what OpenSSL wanted to do, so lets find out
                    debug("OpenSSL could not ");
                    if (hsdata.data.sendbuf->offset > 0)
                    {
                        debug("write\n");
                        result.req_outbound_buffer_size = hsdata.data.sendbuf->offset;
                        result.can_process_inbound = sink_status_t::BLOCKED;
                        break;
                    }
                    if (hsdata.data.to_recv != INT_MAX)
                    {
                        debug("read\n");
                        result.can_process_inbound = sink_status_t::CAN_PROCESS;
                        break;
                    }
                    debug(" do something else\n");
                    throw std::exception();
                }
                debug("Other error happened!\n");
                throw std::exception();
            }
            debug("SSL Handshake done\n");
            if (use_app_handshake)
            {
                debug("Continue with APP handshake\n");
                hsdata.state = handshake_state_t::APP_P1;
                result.can_process_inbound = sink_status_t::CAN_PROCESS;
                break;
            }
            debug("All Handshakes done\n");
            hsdata.state = handshake_state_t::DONE;
            result.is_finished = true;
            result.can_process_inbound = sink_status_t::CAN_PROCESS;
            result.remote_id = __sync_fetch_and_add(&next_clino, 1);
            hsdata.data.clino = result.remote_id;
            break;
        }
        case handshake_state_t::APP_P1:
        case handshake_state_t::APP_P2:
        case handshake_state_t::DONE:
            throw std::exception();
    }
    debugL("");
}

void NativeTroxy::get_handshake_inbound_minimum_buffer_size(int *result, uint16_t hsno)
{
    (void)hsno; // Ignore hsno, it is the same for all
    *result = 1024*16;
}

void NativeTroxy::get_handshake_outbound_minimum_buffer_size(int *result, uint16_t hsno)
{
    (void)hsno; // Ignore hsno, it is the same for all
    *result = 1024*16;
}

void NativeTroxy::save_state(short hsno)
{
    debugE("(%d)", hsno);
    handshake_data_t &hsdata = handshakes.at((unsigned long) hsno);
    connection_data_t &condata = connections.at(hsdata.data.clino - this->client_number_offset);
    condata.data.clino = hsdata.data.clino;
    if (use_ssl)
    {
        condata.data.ssl = hsdata.data.ssl;
        hsdata.data.ssl = nullptr;
        condata.data.bio = hsdata.data.bio;
        condata.data.bio->ptr = &condata.data;
    }
    condata.data.to_recv = INT_MAX;
    condata.data.recvbuf = nullptr;
    free(hsdata.data.sendbuf->arr);
    free(hsdata.data.sendbuf);
    hsdata.data.sendbuf = nullptr;
    debugL("Client %d is now connected", condata.data.clino);
}

void NativeTroxy::open(client_result_t &result, uint16_t clino)
{
    debugE("");
    (void) clino;
    connection_data_t &condata = connections.at(clino - this->client_number_offset);
    condata.i_am_contact = true;
    result.can_process_inbound = sink_status_t::CAN_PROCESS;
    result.req_outbound_buffer_size = INT_MAX;
    result.lastinv = condata.lastinv;
    result.reqmsgbuf = INT_MAX;
    debugL("");
}

void NativeTroxy::init_client(uint16_t clino, buffer_t *sendbuffer)
{
	debugE("");
	connection_data_t &condata = connections.at(clino - this->client_number_offset);
	if (sendbuffer == nullptr)
	{
		condata.data.sendbuf = (buffer_t *) malloc(sizeof(buffer_t));
		condata.data.sendbuf->offset = 0;
		condata.data.sendbuf->length = SENDBUFFER_SIZE;
		condata.data.sendbuf->arr = (uint8_t *) calloc(1, SENDBUFFER_SIZE);
		condata.data.sendbuf->arr_length = SENDBUFFER_SIZE;
	}
	else
	{
		condata.data.sendbuf = sendbuffer;
	}

	condata.lastinv = 0;
	condata.nextinv = 1;

#if USED_PROTOCOL == PROTOCOL_DEFAULT
	condata.pp = new DefaultProtocolParser(this); // Change this to change the used protocol parser
#else
    condata.pp = new HTTPProtocolParser(this); // Change this to change the used protocol parser
#endif

	condata.i_am_contact = false;
	HMAC_CTX_init(&condata.hmac_ctx);
	HMAC_Init_ex(&condata.hmac_ctx, key, key_size, hash_function, NULL);
	condata.collectors.reserve(inv_window);
	for (uint32_t i = 0; i < inv_window; ++i)
	{
		// FIXME: get correct values for threshold and maxprops
		condata.collectors.push_back(new QuorumCollector(client_number_offset, client_number_offset / 2 + 1, client_number_offset));
	}
	condata._lock = 0;
	debugL("");
}

void NativeTroxy::get_client_inbound_minimum_buffer_size(int *result, uint16_t clino)
{
    (void) clino;
    *result = 1024 * 16;
}

void NativeTroxy::get_client_outbound_minimum_buffer_size(int *result, uint16_t clino)
{
    (void) clino;
    *result = SENDBUFFER_SIZE;
}

void NativeTroxy::process_client_inbound_data(client_result_t &result, uint16_t clino, buffer_t &source, buffer_t &dest)
{
    debugE("");

    connection_data_t &condata = connections.at(clino - this->client_number_offset);

    condata.data.recvbuf = &source;
    condata.data.to_recv = INT_MAX;

    result.reqmsgbuf = INT_MAX;
    result.req_outbound_buffer_size = INT_MAX;
    result.can_process_inbound = sink_status_t::CAN_PROCESS;
    result.lastinv = condata.lastinv;

    uint32_t offset = 0;
    uint32_t firstpacket = 0;

    debug("%d bytes are readable\n", source.length);

    while (true)
    {
        int read = condata.pp->parseBuffer(condata, dest);

        if (read < 0)
        {
            result.req_outbound_buffer_size = condata.data.sendbuf->offset;
            debug("Need write\n");
            break;
        }
        if (read == 0)
        {
            result.can_process_inbound = sink_status_t::WAIT_FOR_DATA;
            debug("Need read\n");
            break;
        }

#if WITH_CACHE
        debug("Use cache for read-only optimization\n");debug("bf dest offset: %d\n",dest.offset);
        bool isRead = isReadRequest(clino, static_cast<uint64_t>(buffer_as_packet_get_invocation(dest, offset)));

        buffer_t request = { dest.arr + offset, dest.arr_length - offset, 0, dest.length - offset};
        debug("Request len: %d (0x%x)\n", buffer_as_packet_get_length(request), buffer_as_packet_get_length(request));

        if (isRead)
        {
            debug("READ request, check cache\n");
            if (usereadopt && Cache::getInstance().hasRequest(&request))
            {
                debug("Has cached request, do fast read\n");
            }
            else
            {
                debug("Request not cached, change to write %d, %lu\n", clino, buffer_as_packet_get_invocation(dest, offset));
                buffer_as_packet_change_write(dest, offset);

                uint32_t pkt_len = buffer_as_packet_get_length_on_offset(dest, offset) - hash_size;
                debug("Len: %d (0x%x)\n", pkt_len, pkt_len);

                HMAC_Init_ex(&condata.hmac_ctx, NULL, 0, NULL, NULL);
                HMAC_Update(&condata.hmac_ctx, dest.arr+offset, pkt_len);
                HMAC_Final(&condata.hmac_ctx, dest.arr+offset + pkt_len, NULL);
            }
        }
        else
        {
            debug("Received WRITE request, delete cache\n");
            Cache::getInstance().deleteReply(&request);
        }

        auto invno = static_cast<uint64_t>(buffer_as_packet_get_invocation(dest, offset));
        debug("invno: %lu, nextres: %lu\n", invno, condata.nextres);
        if (((buffer_as_packet_get_flags(dest, offset) & 0x04)==0) && (invno > condata.nextres))
        {
            debug("Pending request\n");
            lock(&condata._lock);
            // HEAP buffer creation:
            condata.pending[invno] = buffer_get_from_buffer_on_offset(dest, firstpacket, static_cast<uint32_t>(read));
            unlock(&condata._lock);
        }
        debug("request to process:\n");
        hexdump(dest.arr, read);

        if (offset==0 || (buffer_as_packet_get_flags(dest, offset) & 0x04)>1)
            firstpacket = dest.offset;

        offset = dest.offset;
        debug("af dest offset: %d\n",dest.offset);
#endif
    }
    debugL("");
}

void NativeTroxy::retrieve_client_outbound_data(client_result_t &result, uint16_t clino, buffer_t &dst)
{
    debugE("");

    connection_data_t &condata = connections.at(clino - this->client_number_offset);

    result.reqmsgbuf = INT_MAX;
    result.req_outbound_buffer_size = INT_MAX;
    result.can_process_inbound = sink_status_t::CAN_PROCESS;
    result.lastinv = condata.lastinv;

    if (__builtin_expect(condata.data.sendbuf->offset > 0, true))
    {
        debug("Sending buffered stuff\n");
//		buffer_add_buffer(destination, *hsdata.data.sendbuf);

        uint32_t offset = condata.data.sendbuf->offset;
        debug("sendbuf offset: %d bytes\n", offset);
        memcpy(dst.arr + dst.offset, condata.data.sendbuf->arr, offset);

        dst.offset += offset;
        condata.data.sendbuf->length = condata.data.sendbuf->length + offset;
        condata.data.sendbuf->offset = 0;

        result.can_process_inbound = sink_status_t::CAN_PROCESS;
    }

    debugL("");
}

void NativeTroxy::retrieve_outbound_messages(client_result_t &result, uint16_t clino)
{
    debugE("");
    debug("!!! NOT IMPLEMENTED YET !!!");
    debugL("");
}

void NativeTroxy::handle_forwarded_request(client_result_t &cliresult, verification_result_t &result, uint16_t clino, buffer_t &request)
{
    debugE("(%d, %ld)", clino, buffer_as_request_get_invocation(request));

    result = verification_result_t::OK;
    connection_data_t &condata = connections.at(clino - this->client_number_offset);

    cliresult.reqmsgbuf = INT_MAX;
    cliresult.req_outbound_buffer_size = INT_MAX;
    cliresult.can_process_inbound = sink_status_t::CAN_PROCESS;
    cliresult.lastinv = condata.lastinv;

    hexdump(request.arr+request.offset, request.length);

    uint16_t sender = buffer_as_request_get_sender(request);

    if (__builtin_expect(sender != clino, false))
    {
        debug("Client number does not match request sender: %d != %d\n", clino, sender);
        result = verification_result_t ::FAIL;
        return;
    }

    if (__builtin_expect(!verify_message(request, &condata.hmac_ctx), false))
    {
        debug("Message could not be verified!\n");
        result = verification_result_t ::FAIL;
        return;
    }

    buffer_t *req = &request;
    if ((buffer_as_request_get_flags(request) & 0x04)==0)
    {
#if WITH_CACHE
        if ((buffer_as_request_get_flags(request) & 0x02)==0)
        {
            debug("Received WRITE request, delete cache\n");
            Cache::getInstance().deleteReply(req);
        }
#endif

        if (buffer_as_request_get_invocation(request) <= condata.nextinv)
        {
            debug("Invoke forwarded WRITE request\n");
            __sync_fetch_and_add(&condata.nextinv, 1);
        }
    }
    else
    {
#if WITH_CACHE
        debug("Create reply from cache for fast read\n");
        HashRep *rep = Cache::getInstance().getReply(req);
        auto invno = static_cast<uint64_t>(buffer_as_request_get_invocation(request));

        buffer_t reply = {nullptr, 0, 0, 0};
        if (rep != nullptr)
        {
            debug("Cached reply found\n");
            reply.arr = (uint8_t *) rep->__hash;
            reply.offset = 0;
            reply.length = HASHLEN;
        }
        else
        {
            debug("Cache not found\n");
        }

        uint32_t start = cliresult.resultbuf->offset;

        buffer_create_reply_into_buffer(*cliresult.resultbuf, replica_number, clino, invno, true, true, reply, -1, hash_size);

        uint32_t pkt_len = buffer_as_packet_get_length_on_offset(*cliresult.resultbuf, start) - hash_size;
        debug("Len: %d (0x%x)\n", pkt_len, pkt_len);

        HMAC_Init_ex(&condata.hmac_ctx, NULL, 0, NULL, NULL);
        HMAC_Update(&condata.hmac_ctx, cliresult.resultbuf->arr+start, pkt_len);
        HMAC_Final(&condata.hmac_ctx, cliresult.resultbuf->arr+start + pkt_len, NULL);

        hexdump(cliresult.resultbuf->arr+start, pkt_len+hash_size);

        buffer_advance(*cliresult.resultbuf, hash_size);

        if (rep != nullptr)
        {
            cliresult.lastinv = invno;
        }

#endif
    }
    debugL("(%d, %ld)", clino, buffer_as_request_get_invocation(request));
}

void NativeTroxy::handle_request_executed(client_result_t &result, uint16_t clino, uint64_t invno, buffer_t &reply, bool replyfull)
{
#ifdef SGX
    debugE("(%d, %llu)", clino, invno);
#else
    debugE("(%d, %lu)", clino, invno);
#endif

    connection_data_t &condata = connections.at(clino - this->client_number_offset);

    result.reqmsgbuf = INT_MAX;
    result.req_outbound_buffer_size = INT_MAX;
    result.can_process_inbound = sink_status_t::CAN_PROCESS;
    result.lastinv = condata.lastinv;

    debug("Got reply (%s READ):\n", replyfull ? "IS" : "NOT");
    hexdump(reply.arr+reply.offset, reply.length);

    if (__builtin_expect(condata.lastinv > (int64_t)invno, false))
    {
        // This is stale, so ignore it
        debug("Got stale request, ignoring, lastinv: %lu, invno: %lu\n", condata.lastinv, invno);
        return;
    }

    if (condata.i_am_contact)
    {
        debug("I'm the contact for this request\n");
        // Create reply and handle it locally

        buffer_t *rep = buffer_create_reply(replica_number, clino, invno, true, replyfull, reply, -1, hash_size);

        hexdump(rep->arr, rep->arr_length);
        buffer_set_offset(*rep, 0);
        handle_reply(result, clino, *rep);
        buffer_destroy(rep);
    }
    else
    {
        debug("I'm NOT the contact for this request\n");
        // Need to create and certify reply and send it over

        uint32_t start = result.resultbuf->offset;

        buffer_create_reply_into_buffer(*result.resultbuf, replica_number, clino, invno, true, replyfull, reply, -1, hash_size); // abuse replyfull, as execspec

        uint32_t pkt_len = buffer_as_packet_get_length_on_offset(*result.resultbuf, start) - hash_size;
        debug("Len: %d (0x%x)\n", pkt_len, pkt_len);

        HMAC_Init_ex(&condata.hmac_ctx, NULL, 0, NULL, NULL);
        HMAC_Update(&condata.hmac_ctx, result.resultbuf->arr+start, pkt_len);
        HMAC_Final(&condata.hmac_ctx, result.resultbuf->arr+start + pkt_len, NULL);

        hexdump(result.resultbuf->arr+start, pkt_len+hash_size);

        buffer_advance(*result.resultbuf, hash_size);

        result.lastinv = invno;
    }

    debugL("(%d, %llu)", clino, invno);
}

bool NativeTroxy::handle_reply(client_result_t &result, uint16_t clino, buffer_t &replies)
{

	debugE("(%d)", clino);

	connection_data_t &condata = connections.at(clino - this->client_number_offset);

	result.reqmsgbuf = INT_MAX;
	result.req_outbound_buffer_size = INT_MAX;
	result.can_process_inbound = sink_status_t::CAN_PROCESS;
	result.lastinv = condata.lastinv;

	if (replies.length < 4)
	{
		debug("This can't possibly a reply, not enough bytes: %d < %d\n", replies.length, 4);
		return false;
	}

	uint32_t replength = buffer_as_packet_get_length(replies);
	if (replies.length < replength)
	{
		debug("Not a complete reply: %d < %d\n", replies.length, replength);
		return false;
	}
	buffer_t reply = {replies.arr, 0, replies.offset, replength};

	debug("Got reply: %d, %ld\n", clino, buffer_as_reply_get_invocation(reply));
	hexdump(reply.arr+reply.offset, reply.length);
	int16_t sender = buffer_as_reply_get_sender(reply);
    bool is_prop = (buffer_as_reply_get_flags(reply) & 0x01) == 0x01;
	bool is_spec = (buffer_as_reply_get_flags(reply) & 0x02) > 1;
	auto invno = static_cast<uint64_t>(buffer_as_reply_get_invocation(reply));
	uint16_t payload_len = buffer_as_reply_get_payload_length(reply);
	QuorumCollector *qc = condata.collectors[invno % inv_window];

	if (invno <= static_cast<uint64_t>(condata.lastinv) || (is_spec && !isReadRequest(clino, invno)))
	{
		debugL("Got a stale reply, ignoring, lastinv: %lu, invno: %lu\n", condata.lastinv, invno);
		return false;
	}
	else if (is_spec)
	{
		lock(&condata._lock);
		auto it = condata.pending.find(static_cast<uint64_t>(invno));
        unlock(&condata._lock);
		if (invno<condata.nextres || it!=condata.pending.end())
		{
			debugL("Got a stale reply, ignoring, lastinv: %lu, invno: %lu\n", condata.lastinv, invno);
			return false;
		}
	}

	if (__builtin_expect(qc->is_already_known(sender, &reply), false))
	{
		debugL("Message is already known\n");
		return true;
	}

	if (__builtin_expect(sender != replica_number && !verify_message(reply, &condata.hmac_ctx), false))
	{
		debugL("Message could not be verified\n");
		return true;
	}

	// Make copy of reply
	buffer_t *msg = nullptr;
	try
	{
		msg = buffer_construct_from_buffer(reply); // malloc
	}
	catch (std::exception& e)
	{
		printf("Could not allocate memory for reply!\n");fflush(stdout);
		throw std::exception();
	}
	//printf("Buffer @ %p, arr @ %p\n", msg, (void *) msg->arr);fflush(stdout);
	buffer_t vote = {nullptr, 0, 0, 0};

	if (payload_len > 0)
	{
		vote.arr = msg->arr;
		vote.offset = REPLY_PAYLOAD_OFFSET;
		vote.length = payload_len;
	}

    bool stable = qc->add_vote(sender, msg, vote, is_prop);

    debug("Quorum is now %s\n", stable ? "STABLE" : "NOT STABLE");

#if WITH_CACHE
    if (!is_spec && stable)
    {
        debug("Write replies stable\n");
        buffer_t *clirep = qc->get_leading_proposal();
        int written = condata.pp->sendReply(condata, *clirep);
        if (written < 0)
        {
            // Not enough space in send buffer, need to send out stuff there first
            // FIXME: implement error handling for this
            throw std::exception();
        }
        if (written == 0)
        {
            // Need to read first, should not happen
            throw std::exception();
        }

        if (isReadRequest(clino, invno))
        {
            lock(&condata._lock);
            buffer_t *req = condata.reqs[invno];
            Hash *hreq = new Hash(req);
            unlock(&condata._lock);
            if (req == nullptr)
            {
                debug("Read-request from store was NULL, should not happen!\n");
                throw std::exception();
            }
            debug("Request stable, insert reply to cache\n");
            Cache::getInstance().insertReply(hreq, clirep);
            deleteRequest(clino, invno);
        }

        debug("WRITE invno %d, nextres %lu\n", invno, condata.nextres);
        if (invno==condata.nextres)
        {
            debug("Check pending and complete\n");
            condata.lastinv = invno;
            condata.nextres++;
            getResend(clino, result, condata.nextres);
        }
        else if (invno > condata.nextres)
        {
            debug("Push into complete queue: client %ld, invno %ld, nextres %ld\n", clino, invno, condata.nextres);
            condata.complete.push(invno);
        }

        result.req_outbound_buffer_size = condata.data.sendbuf->offset;
        result.can_process_inbound = sink_status_t::CAN_PROCESS;

        result.lastinv = invno;
        qc->clear();

        buffer_advance(replies, replength);
    }
    else if (is_spec)
    {
        debug("Fast read replies\n");
        buffer_advance(replies, replength);

        lock(&condata._lock);
        buffer_t *r = condata.reqs[invno];
        unlock(&condata._lock);
        if (r == nullptr)
        {
            debug("Read-request from store was NULL, should not happen!\n");
            throw std::exception();
        }

        if (qc->add_fastread(sender, replica_number, msg, vote) || (qc->isComplete()&&qc->get_num_of_votes()==2))
        {
            if (!qc->isleadernull() && Cache::getInstance().matchReply(r, qc->getfastread()))
            {
                debug("Successful fast read\n");
                buffer_t *clirep = qc->getfastread();
                int written = condata.pp->sendReply(condata, *clirep);
                if (written < 0)
                {
                    // Not enough space in send buffer, need to send out stuff there first
                    // FIXME: implement error handling for this
                    throw std::exception();
                }
                if (written == 0)
                {
                    // Need to read first, should not happen
                    throw std::exception();
                }
                result.req_outbound_buffer_size = condata.data.sendbuf->offset;
                result.can_process_inbound = sink_status_t::CAN_PROCESS;
                result.lastinv = invno;

                qc->clear();

                if (isReadRequest(clino, (uint64_t) invno))
                {
                    debug("Stable quorum for read request, deleting it\n");
                    // delete read req
                    deleteRequest(clino, (uint64_t) invno);
                }

                if (invno==condata.nextres)
                {
                    condata.nextres++;
                    getResend(clino, result, condata.nextres);
                }
                else if (invno > condata.nextres)
                {
                    debug("Push into complete queue: client %ld, invno %ld, nextres %ld\n", clino, invno, condata.nextres);
                    condata.complete.push(invno);
                }
            }
            else
            {
                debug("Failed fast read\n");
                lock(&condata._lock);
                isconflict++;
                if (usereadopt && isconflict*100/readrequest>2)
                {
                    debug("Conflict rate is higher than 2%%!\n");
                    usereadopt = false;
                }
                unlock(&condata._lock);
                if (!isReadRequest(clino, (uint64_t) invno))
                {
                    debug("Unstable quorum without matching read-request in store, should not happen!\n");
                    throw std::exception();
                }

                qc->clear();

                auto len = buffer_as_packet_get_length(*r);
                HMAC_Init_ex(&condata.hmac_ctx, NULL, 0, NULL, NULL);
                HMAC_Update(&condata.hmac_ctx, r->arr, len - hash_size);
                HMAC_Final(&condata.hmac_ctx, r->arr + (len - hash_size), NULL);

                debug("invno: %d, nextres: %lu\n", invno, condata.nextres);
                if (invno==condata.nextres)
                {
                    buffer_add_buffer(*result.resultbuf, *r);
                    condata.nextres++;
                    getResend(clino, result, condata.nextres);
                }
                else if (invno > condata.nextres)
                {
                    debug("Add into pending: %d, %lu\n", invno, condata.nextres);
                    buffer_t *request = buffer_construct_from_buffer(*r);
                    lock(&condata._lock);
                    condata.pending[invno] = request;
                    unlock(&condata._lock);
                }

                // delete read req
                Cache::getInstance().deleteReply(r);
            }
        }
    }
#else
	if (stable)
	{
		buffer_t *clirep = qc->get_leading_proposal();
		int written = condata.pp->sendReply(condata, *clirep);
		if (written < 0)
		{
			// Not enough space in send buffer, need to send out stuff there first
			// FIXME: implement error handling for this
			throw std::exception();
		}
		if (written == 0)
		{
			// Need to read first, should not happen
			throw std::exception();
		}
		result.req_outbound_buffer_size = condata.data.sendbuf->offset;
		result.can_process_inbound = sink_status_t::CAN_PROCESS;
		condata.lastinv = invno;
		result.lastinv = invno;
		qc->clear();
		if (isReadRequest(clino, (uint64_t) invno))
		{
			debug("Stable quorum for read request, deleting it\n");
			// delete read req
			deleteRequest(clino, (uint64_t) invno);
		}
	}

	buffer_advance(replies, replength);

	// Read opt, no stable quorum, need to generate a new "write" request
	debug("Different votes: %d\n", qc->get_num_of_votes());
	if (!stable && qc->get_num_of_votes() == 3)
	{
		debug("UNStable quorum for read request, re-adding request and deleting it\n");
		// Protocol parser already saved the request as write

		if (!isReadRequest(clino, (uint64_t) invno))
		{
			debug("Unstable quorum without matching read-request in store, should not happen!\n");
			throw std::exception();
		}

		qc->clear();

		lock(&condata._lock);
		buffer_t *r = condata.reqs[invno];
		unlock(&condata._lock);
		if (r == nullptr)
		{
			debug("Read-request from store was NULL, should not happen!\n");
			throw std::exception();
		}
		auto len = buffer_as_packet_get_length(*r);
		HMAC_Init_ex(&condata.hmac_ctx, NULL, 0, NULL, NULL);
		HMAC_Update(&condata.hmac_ctx, r->arr, len - hash_size);
		HMAC_Final(&condata.hmac_ctx, r->arr + (len - hash_size), NULL);

		buffer_add_buffer(*result.resultbuf, *r);
		// delete read req
		deleteRequest(clino, (uint64_t) invno);
	}
#endif

	debugL("(%d)", clino);
	return false;
}

void NativeTroxy::verify_proposal(verification_result_t &result, uint32_t verifier, buffer_t &to_verify)
{
    // We get only prototype messages here, so we can parse them
    debugE("(%d)", verifier);

    hexdump(to_verify.arr + to_verify.offset, to_verify.length);

#if WITH_CACHE
    uint16_t clino = buffer_as_request_get_sender(to_verify);
    bool writeflag = (buffer_as_request_get_flags(to_verify) & 0x02)==0;
    debug("Verify %s request: %d %lu\n", writeflag ? "WRITE" : "READ", clino, buffer_as_request_get_invocation(to_verify));
    if (writeflag && (clino % 3 != replica_number))
    {
        buffer_t *writereq = (buffer_t *)malloc(sizeof(buffer_t) + to_verify.length);
        writereq->length = to_verify.length;
        writereq->offset = 0;
        writereq->arr_length = to_verify.length;
        writereq->arr = (uint8_t *)(writereq + 1);
        memcpy(writereq->arr, to_verify.arr + to_verify.offset, to_verify.length);
        hexdump(writereq->arr, writereq->length);
        debug("Received WRITE request, delete cache\n");
        Cache::getInstance().deleteReply(writereq);
        free(writereq);
    }
#endif

    HMAC_CTX *ctx = &verifiers[verifier];

    if (__builtin_expect(!verify_message(to_verify, ctx), false))
    {
        debug("Hashes are not equal!\n");
        result = verification_result_t::FAIL;
        return;
    }

    debug("Hashes are equal!\n");
    result = verification_result_t::OK;

    debugL("");
}

void NativeTroxy::verify_proposals(verification_result_t &result, uint32_t verifier, buffer_t &to_verify)
{
    debugE("(%d)", verifier);

    hexdump(to_verify.arr + to_verify.offset, to_verify.length);

    while (to_verify.length > 0)
    {
        uint32_t pkt_len = buffer_as_packet_get_length(to_verify);

        buffer_t tmp = {};
        tmp.arr = to_verify.arr;
        tmp.offset = to_verify.offset;
        tmp.length = pkt_len;

        verify_proposal(result, verifier, tmp);

        buffer_advance(to_verify, pkt_len);

        if (result == verification_result_t::FAIL)
            return;
    }


    result = verification_result_t::OK;

    debugL("");
}

void NativeTroxy::savePointer(uint16_t clino, uint8_t type, uint8_t *ptr)
{
    client_pointers[clino].ptr[type] = ptr;
}

uint8_t* NativeTroxy::getPointer(uint16_t clino, uint8_t type)
{
    return client_pointers[clino].ptr[type];
}

void NativeTroxy::addReadRequest(uint16_t clino, uint64_t invno, buffer_t *req)
{
	debugE("%d, %lu", clino, invno);
	connection_data_t &condata = connections.at(clino - this->client_number_offset);
	lock(&condata._lock);
	condata.read_reqs.insert(invno);
	condata.reqs[invno] = req;
    readrequest++;
	unlock(&condata._lock);
	debugL("%d, %lu", clino, invno);
}

bool NativeTroxy::isReadRequest(uint16_t clino, uint64_t invno)
{
	debugE("%d, %lu", clino, invno);
	connection_data_t &condata = connections.at(clino - this->client_number_offset);
	lock(&condata._lock);
	auto e = condata.read_reqs.find(invno);
	bool result = e != condata.read_reqs.end();
	unlock(&condata._lock);
	debugL("%d, %lu", clino, invno);
	return result;
}
void NativeTroxy::deleteRequest(uint16_t clino, uint64_t invno)
{
	debugE("%d, %lu", clino, invno);
	connection_data_t &condata = connections.at(clino - this->client_number_offset);
	lock(&condata._lock);
	condata.read_reqs.erase(invno);
	buffer_t *req = condata.reqs[invno];
	condata.reqs.erase(invno);
    unlock(&condata._lock);
    free(req);
	debugL("%d, %lu", clino, invno);
}

void NativeTroxy::lock(uint8_t *lock)
{
	debugE();
	uint8_t exp = 0;
	while (__atomic_compare_exchange_n(lock, &exp, (uint8_t)1, false, __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST) == false)
	{
		exp = 0;
	}
	debugL();
}

void NativeTroxy::unlock(uint8_t *lock)
{
	debugE();
	__atomic_store_n(lock, 0, __ATOMIC_SEQ_CST);
	debugL();
}

uint16_t NativeTroxy::next_client_number()
{
    if (distributed_contacts)
    {
        return replica_number + (__sync_fetch_and_add(&next_clino, 1) * client_number_offset);
    }

    return __sync_fetch_and_add(&next_clino, 1);
}

bool NativeTroxy::verify_message(buffer_t &to_verify, HMAC_CTX *ctx)
{
    debugE("");

    uint32_t length = buffer_as_packet_get_length(to_verify);
    assert(hash_size < length);
    length -= hash_size;

    int8_t my_hash[hash_size];
    uint8_t *their_hash = to_verify.arr + to_verify.offset + length;

    if (__builtin_expect(ctx == nullptr, false))
    {
        HMAC(hash_function, key, key_size, to_verify.arr + to_verify.offset, length, (unsigned char *) &my_hash, nullptr);
    }
    else
    {
        HMAC_Init_ex(ctx, NULL, 0, NULL, NULL);
        HMAC_Update(ctx, to_verify.arr+to_verify.offset, length);
        HMAC_Final(ctx, (unsigned char *) &my_hash, NULL);
    }

    debug("Our hash:\n");
    hexdump(my_hash, hash_size);

    if (memcmp(my_hash, their_hash, hash_size) != 0)
    {
        debug("Hashes are not equal!\n");
        return false;
    }

    debug("Hashes are equal!\n");
    debugL("");
    return true;
}

void NativeTroxy::add_hmac_to_request(HMAC_CTX *ctx, buffer_t &req, uint32_t start, uint32_t pkt_len)
{
    debugE("");

    HMAC_Init_ex(ctx, NULL, 0, NULL, NULL);
    HMAC_Update(ctx, req.arr + start, pkt_len);
    HMAC_Final(ctx, req.arr + start + pkt_len, NULL);

    debugL("");
}

int NativeTroxy::readBytesSSL(shared_data_t &sdata, buffer_t &dest, uint32_t toread)
{
    debugE("(%p, %p)", sdata.ssl, (void*)dest.arr);
    debug("Did SSL handshake prior, so using SSL functions\n");
    int read = 0;
    ERR_clear_error();
    if ((read = SSL_read(sdata.ssl, dest.arr+dest.offset, toread)) <= 0)
    {
        int error = SSL_get_error(sdata.ssl, read);
        if (error == 1)
        {
            debug("ERROR during SSL_read\n");
            unsigned long err = ERR_get_error();
            char *b = new char[1024];
            ERR_error_string_n(err, b, 1024);
            printf("Errorqueue error: %s (%lu)\n", b, err);
            throw std::exception();
        }
        if (error == 5)
        {
            // BIO has set what OpenSSL wanted to do, so lets find out
            debug("OpenSSL could not ");
            if (sdata.sendbuf->offset > 0)
            {
                debug("write\n");
                debugL("");
                return -1;
            }
            if (sdata.to_recv != INT_MAX)
            {
                debug("read\n");
                debugL("");
                return 0;
            }
            debug(" do something else\n");
            throw std::exception();
        }
        if (error == SSL_ERROR_ZERO_RETURN)
        {
            debug("Connection aborted by other side!\n");
            throw IOException();
        }
        debug("Other error happened!\n");
        throw std::exception();
    }
    buffer_advance(dest, (uint32_t) read);
    debugL("");
    return read;
}

int NativeTroxy::readBytesSSL(shared_data_t &sdata, buffer_t &dest)
{
    return readBytesSSL(sdata, dest, dest.length);
}

int NativeTroxy::sendBuffer(shared_data_t &sdata, buffer_t &src)
{
    debugE("");
    int written = 0;
    if (use_ssl)
    {
        written = writeBytesSSL(sdata, src);
    }
    else
    {
        if (sdata.sendbuf->length < src.length)
        {
            debug("Not enough space to write: %d < %d\n", sdata.sendbuf->length, src.length);
            //sdata.to_send = src.length;
            return -1;
        }
        memcpy(sdata.sendbuf->arr + sdata.sendbuf->offset, src.arr + src.offset, src.length);
        buffer_advance(*sdata.sendbuf, src.length);
        written = src.length;
        sdata.sendbuf->offset += written;
    }
    debugL("Written: %d", written);
    return written;
}

int NativeTroxy::readBuffer(shared_data_t &sdata, buffer_t &dest, uint32_t len)
{
    debugE("");
    int read = 0;
    if (use_ssl)
    {
        read = readBytesSSL(sdata, dest, len);
        if (read < 0)
        {
            // Need to write something
            debugL("Need write");
            return read;
        }
        else if (read == 0)
        {
            // Need more data
            debugL("Need read");
            return read;
        }
    }
    else
    {
        if (sdata.recvbuf->length < len)
        {
            debugL("Need more bytes: %d < %d\n", sdata.recvbuf->length, len);
            return 0;
        }
        memcpy(dest.arr+dest.offset, sdata.recvbuf->arr+sdata.recvbuf->offset, len);
        buffer_advance(*sdata.recvbuf, len);
    }
    debugL("");
    return read;
}

int NativeTroxy::writeBytesSSL(shared_data_t &sdata, buffer_t &src, uint32_t towrite)
{
    debugE("");
    debug("Did SSL handshake prior, so using SSL functions\n");
    int written = 0;
    ERR_clear_error();
    if ((written = SSL_write(sdata.ssl, src.arr+src.offset, towrite)) <= 0)
    {
        int error = SSL_get_error(sdata.ssl, written);
        if (error == 1)
        {
            debug("ERROR during SSL_write\n");
            unsigned long err = ERR_get_error();
            char *b = new char[1024];
            ERR_error_string_n(err, b, 1024);
            printf("Errorqueue error: %s (%lu)\n", b, err);
            throw std::exception();
        }
        if (error == 5)
        {
            // BIO has set what OpenSSL wanted to do, so lets find out
            debug("OpenSSL could not ");
            if (sdata.sendbuf->offset > 0)
            {
                debug("write\n");
                debugL("");
                return -1;
            }
            if (sdata.to_recv != INT_MAX)
            {
                debug("read\n");
                debugL("");
                return 0;
            }
            debug(" do something else\n");
            throw std::exception();
        }
        debug("Other error happened!\n");
        throw std::exception();
    }
    debugL("");
    return written;
}

int NativeTroxy::writeBytesSSL(shared_data_t &sdata, buffer_t &src)
{
    return writeBytesSSL(sdata, src, src.length);
}

void NativeTroxy::getResend(uint16_t clino, client_result_t &result, uint64_t current)
{
	debugE("(%d, %lu)", clino, current);
	connection_data_t &condata = connections.at(clino - this->client_number_offset);
	while (true)
	{
        current = clearComplete(clino, current);
        lock(&condata._lock);
        auto it = condata.pending.find(current);
        unlock(&condata._lock);
        if (it != condata.pending.end())
        {
            debug("Resend pending request: %d, %lu\n", clino, condata.nextres);
            buffer_t *b = it->second;
            buffer_add_buffer(*result.resultbuf, *b);
            lock(&condata._lock);
            condata.pending.erase(it);
            buffer_destroy(b);
            condata.nextres++;
            unlock(&condata._lock);
            current = condata.nextres;
        }
        else
            break;
	}
	debugL("(%d, %lu)", clino, current);
}

uint64_t NativeTroxy::clearComplete(uint16_t clino, uint64_t next)
{
	debugE("(%d, %lu)", clino, next);
	connection_data_t &condata = connections.at(clino - this->client_number_offset);
    while (!condata.complete.empty())
    {
        debug("complete front %lu, next %lu\n", condata.complete.top(), next);
        if (condata.complete.top() == next)
        {
            debug("Pop from complete queue: client %d, complete %lu, next %lu\n", clino, condata.complete.top(), next);
            lock(&condata._lock);
            condata.complete.pop();
            condata.nextres++;
            unlock(&condata._lock);
            next++;
        }
        else
            break;
    }
	debugL("(%d, %lu)", clino, next);
    return next;
}

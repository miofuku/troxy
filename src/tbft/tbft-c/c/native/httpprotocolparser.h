/**
 * @author weichbr
 */

#ifndef TBFT_C_HTTPPROTOCOLPARSER_H
#define TBFT_C_HTTPPROTOCOLPARSER_H

#include "protocolparser.h"
#include "debug.h"
#include "native_impl.h"

enum class HTTPMethod
{
    UNKNOWN = 0,
    GET = 1,
    HEAD,
    POST,
    PUT,
    DELETE,
    CONNECT,
    OPTIONS,
    TRACE,
    PATCH
};

#ifdef SGX
extern "C" void *memmem(const void *h0, size_t k, const void *n0, size_t l);
#endif

class HTTPProtocolParser : public ProtocolParser
{
public:
	HTTPProtocolParser(NativeTroxy *troxy) : ProtocolParser(troxy), header_len(0) {}
	virtual ~HTTPProtocolParser() {}
	virtual int parseBuffer(connection_data_t &condata, buffer_t &dest)
	{
		debugE("");

		HTTPMethod method = HTTPMethod::UNKNOWN;
		bool has_body = false;

		// HTTP works like this:
		// METHOD URL\r\n
		// Header: Value\r\n
		// \r\n
		// Optional Body\r\n
		// \r\n
		//
		// The body is present for POST, PUT, CONNECT and PATCH and is optional for OPTIONS
		// You cannot infer the length of the header from anything in the header. The length of the body is specified in the Content-Length header.
		// You need to parse until you find \r\n

		// NOTE: _tmp and pkt are different buffer_t objects but use the same backing array
		// This is done, because troxy->readBuffer() changes the offset but the buffer_as_request functions expect the offset to indicate packet start.
		// One buffer_t would work, but then we would need to constantly shift the offset around and using two objects prevents this.
		// In short: we write to _tmp but read the packet from pkt with both buffers having the same backing array
		debug("Starting array at %u\n", written);
		buffer_t _tmp = {(uint8_t *) &arr, ARR_SIZE, written, ARR_SIZE - written};

		// Only retrieve header, if we don't already have it
		if (header_len == 0)
		{
			// Lets check if there is something in the SSL buffer:
			int avail = SSL_pending(condata.data.ssl);
			if (avail == 0 && written == 0)
			{
				// We need to get some bytes, so lets request seven bytes, as the longest HTTP method is 7 bytes (CONNECT and OPTIONS)
				// If we get a GET/PUT on /, the shortest possible, then we will read "GET /\r\n" which is also 7 bytes, so that works out nicely
				int read = troxy->readBuffer(condata.data, _tmp, 7);
				if (read <= 0)
				{
					// Could not read, probably no data available.
					debugL("");
					return read;
				}
				if (_tmp.offset < 7)
				{
					// Could not read 7 bytes
					debugL("");
					return 0;
				}
				written = _tmp.offset;
			}

			// _tmp should now contains at least seven bytes.
			// Check which method we got
			method = getMethod(_tmp.arr);

			if (method == HTTPMethod::UNKNOWN)
			{
				// Did not read a HTTP Method, something went wrong.
				debug("Excepted a HTTP method, but got something else:\n")
				hexdump(_tmp.arr, 7);
				throw std::exception();
			}
			debug("Got a HTTP request\n");

			// POST, PUT, CONNECT and PATCH have a body, for OPTIONS it is optional
			has_body = method == HTTPMethod::POST || method == HTTPMethod::PUT || method == HTTPMethod::CONNECT || method == HTTPMethod::PATCH;

			// Now find the \r\n\r\n. It is just a loop that takes out a byte at a time and checks, whether the last 4 bytes are \r\n\r\n. That indicates the end of the header
			while (!foundRNRN(_tmp))
			{
				int read = troxy->readBuffer(condata.data, _tmp, 1);
				if (read <= 0)
				{
					// Could not read, probably no data available.
					debugL("");
					return read;
				}
				written = _tmp.offset;
			}

			// Header is now complete
			debug("HTTP Header:\n");
			hexdump(_tmp.arr, _tmp.offset);
			header_len = _tmp.offset;
		}

		// Lets see if we need a body. We ignore the OPTIONS method for now.

		if (has_body)
		{
			// find the length of the body in the Content-Length header
			uint32_t len = getContentLength(_tmp);
			debug("Additonal content with length %u\n", len);
			debug("Already have %u bytes\n", (_tmp.offset - header_len));
			if (len == (_tmp.offset - header_len))
			{
				// already have enough bytes.
				written = _tmp.offset;
				goto req;
			}
			int read = troxy->readBuffer(condata.data, _tmp, len - (_tmp.offset - header_len));
			if (read <= 0)
			{
				// Could not read, probably no data available.
				debugL("");
				return read;
			}
			written = _tmp.offset;
		}

		//==============================================
req:;
		bool read_req = false;

#if WITH_CACHE
		if (method == HTTPMethod::HEAD || method == HTTPMethod::GET)
		{
			read_req = true;
		}
#endif

		// Other parsers need to create a Reptor-Request first like so:
		uint8_t _reqarr[ARR_SIZE];
		buffer_t request = {(uint8_t *) _reqarr, ARR_SIZE, 0, ARR_SIZE};
		uint16_t sender = (uint16_t) condata.data.clino;
		uint64_t invno = __sync_fetch_and_add(&condata.nextinv, 1);
		buffer_t payload = {_tmp.arr, _tmp.offset, 0, _tmp.offset};
		buffer_create_request_into_buffer(request, sender, invno, false, read_req, read_req, payload, troxy->get_hash_size());

		// And add the HMAC like so:
		buffer_set_offset(request, 0);
		uint32_t pkt_len = buffer_as_packet_get_length(request) - troxy->get_hash_size();
		troxy->add_hmac_to_request(&condata.hmac_ctx, request, 0, pkt_len);

		// Then restrict the length of the request buffer:
		request.length = buffer_as_packet_get_length(request);

		debug("Finished ReptorHTTP-Request:\n");
		hexdump(request.arr, request.length);

		// And then write to to the destination buffer:
		buffer_add_buffer(dest, request);
		written = 0;
		header_len = 0;

#if WITH_CACHE
		if (read_req)
		{
			debug("Read request, saving again\n");
			buffer_t *readreq = (buffer_t *)malloc(sizeof(buffer_t) + request.length);
			readreq->length = request.length;
			readreq->offset = 0;
			readreq->arr_length = request.length;
			readreq->arr = (uint8_t *)(readreq + 1);
			memcpy(readreq->arr, request.arr, request.length);
			buffer_as_request_set_flags(*readreq, 0x2);
			troxy->addReadRequest((uint16_t) condata.data.clino, invno, readreq);
		}
#endif

		debugL("");
		return 1;
	}

	virtual int sendReply(connection_data_t &condata, buffer_t &reply)
	{
		debugE("Sending reply to client %d", condata.data.clino);

		// reply is already correctly formatted (because the client expects Reptor-Replys), so just send it.
		// In other protocols, the payload has to be extracted and send, e.g. like so:
		uint8_t *payload = buffer_as_reply_get_payload(reply);
		uint16_t payload_length = buffer_as_reply_get_payload_length(reply);
		buffer_t pkt = {payload, payload_length, 0, payload_length};
		debugL("");
		return troxy->sendBuffer(condata.data, pkt);
	}

private:
    uint32_t header_len;
    HTTPMethod getMethod(uint8_t *arr)
    {
        if (strncmp("GET", (const char *) arr, 3) == 0)
        {
            return HTTPMethod::GET;
        }
        if (strncmp("HEAD", (const char *) arr, 4) == 0)
        {
            return HTTPMethod::HEAD;
        }
        if (strncmp("POST", (const char *) arr, 4) == 0)
        {
            return HTTPMethod::POST;
        }
        if (strncmp("PUT", (const char *) arr, 3) == 0)
        {
            return HTTPMethod::PUT;
        }
        if (strncmp("DELETE", (const char *) arr, 6) == 0)
        {
            return HTTPMethod::DELETE;
        }
        if (strncmp("CONNECT", (const char *) arr, 7) == 0)
        {
            return HTTPMethod::CONNECT;
        }
        if (strncmp("OPTIONS", (const char *) arr, 7) == 0)
        {
            return HTTPMethod::OPTIONS;
        }
        if (strncmp("TRACE", (const char *) arr, 5) == 0)
        {
            return HTTPMethod::TRACE;
        }
        if (strncmp("PATCH", (const char *) arr, 5) == 0)
        {
            return HTTPMethod::PATCH;
        }
        return HTTPMethod::UNKNOWN;
    }

    bool foundRNRN(buffer_t &buf)
    {
        return memcmp("\r\n\r\n", buf.arr + buf.offset - 4, 4) == 0;
    }

    uint32_t getContentLength(buffer_t &buf)
    {
        // Need to find the Content-Length header
        uint32_t len = 0;
        uint8_t *pos = (uint8_t *) memmem(buf.arr, buf.offset, "Content-Length: ", 16);
        pos += 16;
        // pos now points to the start of the length number
        len = (uint32_t) strtol((const char *) pos, NULL, 10);
        return len;
    }
};


#endif //TBFT_C_HTTPPROTOCOLPARSER_H

/**
 * @author weichbr
 */

#ifndef TBFT_C_DEFAULTPROTOCOLPARSER_H
#define TBFT_C_DEFAULTPROTOCOLPARSER_H

#include "protocolparser.h"
#include "debug.h"

class DefaultProtocolParser : public ProtocolParser
{
public:
	DefaultProtocolParser(NativeTroxy *troxy) : ProtocolParser(troxy) {}
	virtual ~DefaultProtocolParser() {}
	virtual int parseBuffer(connection_data_t &condata, buffer_t &dest)
	{
		debugE("");
		// To avoid mallocs, we just use a large array on the stack:

		// NOTE: _tmp and pkt are different buffer_t objects but use the same backing array
		// This is done, because troxy->readBuffer() changes the offset but the buffer_as_request functions expect the offset to indicate packet start.
		// One buffer_t would work, but then we would need to constantly shift the offset around and using two objects prevents this.
		// In short: we write to _tmp but read the packet from pkt with both buffers having the same backing array
		buffer_t _tmp = {(uint8_t *) &arr, ARR_SIZE, written, ARR_SIZE - written};
		buffer_t pkt = {(uint8_t *) &arr, ARR_SIZE, 0, ARR_SIZE};

		// First, read 4 bytes
		if (_tmp.offset < 4)
		{
			int read = troxy->readBuffer(condata.data, _tmp, 4);
			if (read <= 0)
			{
				debugL("");
				return read;
			}
			if (_tmp.offset < 4)
			{
				needed = (uint32_t) (4 - _tmp.offset);
				debugL("Read %d bytes but need %d, resuming with next packet", _tmp.offset, 4);
				return 0;
			}
			else
			{
				needed = 0;
			}
			written = _tmp.offset;
		}
#if DEBUG
		else
		{
			debug("Skipping header read\n");
		}
#endif
		debug("Got length\n");
		hexdump(_tmp.arr, 4);
		uint32_t length = buffer_as_packet_get_length(pkt);
		uint32_t pkt_len = length; // Packet length including first 4 bytes

		// Read the rest
		int read = troxy->readBuffer(condata.data, _tmp, needed > 0 ? needed : length - 4);
		if (read <= 0)
		{
			debugL("");
			return read;
		}
		debug("Packet now %d bytes long\n", _tmp.offset);
		written = _tmp.offset;
		if (_tmp.offset < length)
		{
			needed = length - _tmp.offset;
			debugL("Read %d bytes (now at %d) but need %d (%d more needed), resuming with next packet", read, written, length, needed);
			return 0;
		}
		else
		{
			needed = 0;
		}
		debug("Got remaining data\n");
		hexdump(_tmp.arr, length);

		// The DefaultProtocolParser is special, as it already gets Reptor-Requests from the Client
		// so it only has to change the clino and invno and add its HMAC as shown below.

		// Change client number
		buffer_as_request_set_clino(pkt, (uint16_t) condata.data.clino);
		uint64_t invno = __sync_fetch_and_add(&condata.nextinv, 1);
		buffer_as_request_set_invno(pkt, invno);

		// Add HMAC
		length += troxy->get_hash_size();
		buffer_as_packet_set_length(pkt, length);

		//buffer_as_request_set_flags(pkt, 0x2); // Uncomment this to force requests with read-opt

		troxy->add_hmac_to_request(&condata.hmac_ctx, _tmp, 0, pkt_len);

		debug("Added HMAC\n");
		hexdump(_tmp.arr, length);

		// Restrict pkt buffer to the packet length because buffer_add_buffer copies pkt.length bytes.
		pkt.length = length;

		// Write to to the real destination
		buffer_add_buffer(dest, pkt);

		// Check if read-req and store it again
		uint8_t flags = buffer_as_request_get_flags(pkt);
		if ((flags & 0x04) > 1)
		{
			debug("Read request, saving again\n");
			buffer_t *readreq = (buffer_t *)malloc(sizeof(buffer_t) + pkt.length);
			readreq->length = pkt.length;
			readreq->offset = 0;
			readreq->arr_length = pkt.length;
			readreq->arr = (uint8_t *)(readreq + 1);
			memcpy(readreq->arr, pkt.arr, pkt.length);
			buffer_as_request_set_flags(*readreq, 0x2);
			troxy->addReadRequest((uint16_t) condata.data.clino, invno, readreq);
		}

		debug("Request %lu passed to replica\n", invno);

		written = 0;
		needed = 0;


		// Other parsers need to create a Reptor-Request first like so:
//		uint8_t _reqarr[ARR_SIZE];
//		buffer_t request = {(uint8_t *) _reqarr, ARR_SIZE, 0, ARR_SIZE};
//		uint16_t sender = (uint16_t) condata.data.clino;
//		uint64_t invno = __sync_fetch_and_add(&condata.nextinv, 1);
//		buffer_t payload = {<pointer to payload array>, <length of payload>, 0, <length of payload>};
//		buffer_create_request_into_buffer(request, sender, invno, false, false, false, payload, troxy->get_hash_size());

		// And add the HMAC like so:
//		buffer_set_offset(request, 0);
//		uint32_t pkt_len = buffer_as_packet_get_length(request) - troxy->get_hash_size();
//		troxy->add_hmac_to_request(&condata.hmac_ctx, request, 0, pkt_len);

		// Then restrict the length of the request buffer:
//		request.length = buffer_as_packet_get_length(request);

		// And then write to to the destination buffer:
//		buffer_add_buffer(dest, request);

		debugL("");
		return length;
	}

	virtual int sendReply(connection_data_t &condata, buffer_t &reply)
	{
		debugE("Sending reply to client %d", condata.data.clino);

		// reply is already correctly formatted (because the client expects Reptor-Replys), so just send it.
		// In other protocols, the payload has to be extracted and send, e.g. like so:
//		uint8_t *payload = buffer_as_reply_get_payload(reply);
//		uint16_t payload_length = buffer_as_reply_get_payload_length(reply);
//		buffer_t pkt = {payload, payload_length, 0, payload_length};
//		return troxy->sendBuffer(condata.data, pkt);

		debugL("");
		return troxy->sendBuffer(condata.data, reply);
	}
};

#endif //TBFT_C_DEFAULTPROTOCOLPARSER_H

/**
 * @author weichbr
 */

#ifndef TBFT_C_PROTOCOLPARSER_H_H
#define TBFT_C_PROTOCOLPARSER_H_H

#include <Buffer.h>
//#include "native_impl.h"

#define ARR_SIZE (35*1024)

class NativeTroxy;
typedef struct __connection_data connection_data_t;

class ProtocolParser
{
public:
	ProtocolParser(NativeTroxy *troxy) : troxy(troxy), needed(0), written(0) {}
	virtual ~ProtocolParser() {}

	/**
	 * @brief Creates a Reptor-Request packet containing the client App-Request.
	 * The App-Request can be obtained by calling by reading the client socket like so:
	 * troxy->readBuffer(condata.data, mybuf, mylen) where mybuf is a buffer_t and mylen indicates how many bytes to read.
	 * @param condata Connection information identifying the client
	 * @param dest The buffer that shall contain the Reptor-Request packet in the end.
	 * WARNING: In enclave mode, dest is located in untrusted memory! Only write to it, never read from it!
	 * @return Either a negative number, zero, or a positive number:
	 * < 0: Indicates, that we want to write data to the socket, e.g. to signal the client to send more data
	 * = 0: Indicates, that we want to read more data from the socket, e.g. because the client send a large packet and not everything arrived yet.
	 * > 0: Indicates, how many bytes have been read from condata.data.recvbuf
	 */
	virtual int parseBuffer(connection_data_t &condata, buffer_t &dest) = 0;

	/**
	 * @brief Send a App-Reply to the client.
	 * The App-Reply can be sent to the client like so:
	 * troxy->sendBuffer(condata.data, mybuf) where mybuf is a buffer_t whose offset and length members are set to the beginning and length of the packet, respectively.
	 * @param condata Connection information identifying the client
	 * @param reply The Reptor-Reply packet that shall be converted into a App-Reply
	 * @return Either a negative number, zero, or a positive number:
	 * < 0: Indicates, that the send buffer has not enough space left for the App-Reply we want to send.
	 * = 0: Indicates, that we need to read something from the client before sending something out.
	 * > 0: Indicates, how many bytes have been written to the send buffer.
	 */
	virtual int sendReply(connection_data_t &condata, buffer_t &reply) = 0;

protected:
	NativeTroxy *troxy;
	uint8_t arr[ARR_SIZE];
	uint32_t needed;
	uint32_t written;
};

#endif //TBFT_C_PROTOCOLPARSER_H_H

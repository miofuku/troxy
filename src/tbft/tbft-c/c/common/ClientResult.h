/**
 * @author weichbr
 */

#ifndef TBFT_C_CLIENTRESULT_H
#define TBFT_C_CLIENTRESULT_H

#include <stdint.h>
#include "HandshakeResult.h"

typedef struct __client_result
{
	int32_t reqmsgbuf;
	int64_t lastinv;
	sink_status_t can_process_inbound;
	int32_t req_outbound_buffer_size;
	buffer_t *resultbuf;
} client_result_t;


#endif //TBFT_C_CLIENTRESULT_H

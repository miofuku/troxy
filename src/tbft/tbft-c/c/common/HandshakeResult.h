/**
 * @author weichbr
 */

#ifndef TBFT_C_HANDSHAKERESULT_H
#define TBFT_C_HANDSHAKERESULT_H

#include <stdint.h>
#ifndef __cplusplus
#include <stdbool.h>
#endif

#ifdef __cplusplus
typedef enum class __sink_status
#else
typedef enum __sink_status
#endif
{
	JNULL = -1,
	CAN_PROCESS = 0,
	WAIT_FOR_DATA = 1,
	BLOCKED = 2
} sink_status_t;

typedef struct __handshake_result
{
	bool is_finished;
	int16_t remote_id;
	int16_t network_id;
	sink_status_t can_process_inbound;
	int32_t req_outbound_buffer_size;
} handshake_result_t;

#endif //TBFT_C_HANDSHAKERESULT_H

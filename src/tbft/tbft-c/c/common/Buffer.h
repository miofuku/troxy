/**
 * @author weichbr
 */

#ifndef TBFT_C_BUFFER_H
#define TBFT_C_BUFFER_H

#include <stdint.h>
#include <string.h>
#include <stdlib.h>

#define REQUEST_TYPE (0x00)
#define REPLY_TYPE (0x01)

#define LENGTH_OFFSET       (0)
#define TYPE_OFFSET         (LENGTH_OFFSET + 4)

#define REQUEST_SENDER_OFFSET (TYPE_OFFSET + 1)
#define REQUEST_INVOCATION_OFFSET (REQUEST_SENDER_OFFSET + 2)
#define REQUEST_FLAGS_OFFSET (REQUEST_INVOCATION_OFFSET + 8)
#define REQUEST_PAYLOAD_LENGTH_OFFSET (REQUEST_FLAGS_OFFSET + 1)
#define REQUEST_PAYLOAD_OFFSET        (REQUEST_PAYLOAD_LENGTH_OFFSET + 2)

#define REPLY_SENDER_OFFSET (TYPE_OFFSET + 1)
#define REPLY_REQUESTER_OFFSET (REPLY_SENDER_OFFSET + 2)
#define REPLY_INVOCATION_OFFSET (REPLY_REQUESTER_OFFSET + 2)
#define REPLY_CONTACT_OFFSET    (REPLY_INVOCATION_OFFSET + 8)
#define REPLY_FLAGS_OFFSET      (REPLY_CONTACT_OFFSET + 1)
#define REPLY_PAYLOAD_LENGTH_OFFSET (REPLY_FLAGS_OFFSET + 1)
#define REPLY_PAYLOAD_OFFSET        (REPLY_PAYLOAD_LENGTH_OFFSET + 2)

typedef struct __buffer
{
	uint8_t *arr;
	uint32_t arr_length;
	uint32_t offset;
	uint32_t length;
} buffer_t;

#ifdef __cplusplus

#include <exception>
#include <cassert>

static inline void buffer_construct_from_array_with_offset(buffer_t &buf, uint8_t *arr, uint32_t arr_length, uint32_t offset);
static inline void buffer_construct_from_array(buffer_t &buf, uint8_t *arr, uint32_t arr_length);
static inline buffer_t *buffer_construct_from_buffer(buffer_t &src);
static inline buffer_t *buffer_construct_from_buffer_on_offset(buffer_t &src, uint32_t offset);
static inline buffer_t *buffer_get_from_buffer_on_offset(buffer_t &src, uint32_t firstpacket, uint32_t offset);

static inline buffer_t *buffer_create_reply(uint16_t sender, uint16_t requester, uint64_t invno, bool isfull, bool execdspec, buffer_t &result, int8_t contactno, uint32_t hash_len);
static inline void buffer_create_reply_into_buffer(buffer_t &buf, uint16_t sender, uint16_t requester, uint64_t invno, bool isfull, bool execdspec, buffer_t &result, int8_t contactno, uint32_t hash_len);
static inline void buffer_create_request_into_buffer(buffer_t &buf, uint16_t sender, uint64_t invno, bool ispanic, bool isread, bool useroopt, buffer_t &payload, uint32_t hash_len);
static inline void buffer_destroy(buffer_t *buf);

static inline void buffer_advance(buffer_t &buf, uint32_t len);
static inline void buffer_rewind(buffer_t &buf, uint32_t len);
static inline void buffer_set_offset(buffer_t &buf, uint32_t offset);

static inline void buffer_add_byte(buffer_t &buf, int8_t val);
static inline void buffer_add_short(buffer_t &buf, int16_t val);
static inline void buffer_add_int(buffer_t &buf, int32_t val);
static inline void buffer_add_long(buffer_t &buf, int64_t val);
static inline void buffer_add_buffer(buffer_t &buf, buffer_t &val);

// Generic Packet
static inline uint32_t buffer_as_packet_get_length(buffer_t &buf);
static inline uint32_t buffer_as_packet_get_length_on_offset(buffer_t &buf, uint32_t offset);
static inline void buffer_as_packet_set_length(buffer_t &buf, uint32_t len);
static inline int64_t buffer_as_packet_get_invocation(buffer_t &buf, uint32_t offset);
static inline void buffer_as_packet_change_write(buffer_t &buf, uint32_t offset);
static inline uint8_t buffer_as_packet_get_flags(buffer_t &buf, uint32_t offset);

// Handshake
static inline bool buffer_is_handshake(buffer_t &buf);
static inline int16_t buffer_as_handshake_get_clino(buffer_t &buf);

// Request
static inline int64_t buffer_as_request_get_invocation(buffer_t &buf);
static inline uint16_t buffer_as_request_get_sender(buffer_t &buf);
static inline uint8_t buffer_as_request_get_flags(buffer_t &buf);
static inline uint16_t buffer_as_request_get_payload_length(buffer_t &buf);
static inline uint8_t *buffer_as_request_get_payload(buffer_t &buf);
static inline void buffer_as_request_set_clino(buffer_t &buf, uint16_t clino);
static inline void buffer_as_request_set_invno(buffer_t &buf, int64_t invno);
static inline void buffer_as_request_set_flags(buffer_t &buf, uint8_t flags);

// Reply
static inline bool buffer_is_reply(buffer_t &buf);
static inline int64_t buffer_as_reply_get_invocation(buffer_t &buf);
static inline int16_t buffer_as_reply_get_sender(buffer_t &buf);
static inline uint8_t buffer_as_reply_get_flags(buffer_t &buf);
static inline uint16_t buffer_as_reply_get_payload_length(buffer_t &buf);
static inline uint8_t *buffer_as_reply_get_payload(buffer_t &buf);

static inline bool buffer_equals(buffer_t *a, buffer_t *b);

// Implementations follow


void buffer_construct_from_array_with_offset(buffer_t &buf, uint8_t *arr, uint32_t arr_length, uint32_t offset)
{
	assert(arr != nullptr);
	assert(arr_length > 0);
	assert(offset <= arr_length);
	buf.arr = arr;
	buf.arr_length = arr_length;
	buf.offset = offset;
	buf.length = arr_length - offset;
}

void buffer_construct_from_array(buffer_t &buf, uint8_t *arr, uint32_t arr_length)
{
	buffer_construct_from_array_with_offset(buf, arr, arr_length, 0);
}

buffer_t *buffer_construct_from_buffer(buffer_t &src)
{
	buffer_t *buf = (buffer_t *) calloc(1, sizeof(buffer_t));
	if (buf == nullptr)
	{
		throw std::exception();
	}
	if (src.length == 0)
	{
		return buf;
	}
	uint32_t len = src.length;
	buf->arr = (uint8_t *) malloc(len);
	if (buf->arr == nullptr)
	{
		throw std::exception();
	}
	buf->arr_length = len;
	buf->length = len;
	buf->offset = 0;
	memcpy(buf->arr, src.arr+src.offset, len);
	return buf;
}

buffer_t *buffer_construct_from_buffer_on_offset(buffer_t &src, uint32_t offset)
{
	buffer_t *buf = (buffer_t *) calloc(1, sizeof(buffer_t));
	if (buf == nullptr)
	{
		throw std::exception();
	}
	if (src.length == 0)
	{
		return buf;
	}
	uint32_t len = src.length;
	buf->arr = (uint8_t *) malloc(len);
	if (buf->arr == nullptr)
	{
		throw std::exception();
	}
	buf->arr_length = len;
	buf->length = len;
	buf->offset = 0;
	memcpy(buf->arr, src.arr+offset, len);
	return buf;
}

buffer_t *buffer_get_from_buffer_on_offset(buffer_t &src, uint32_t firstpacket, uint32_t read)
{
	buffer_t *buf = (buffer_t *) calloc(1, sizeof(buffer_t));
	if (buf == nullptr)
	{
		throw std::exception();
	}
	if (src.length == 0)
	{
		return buf;
	}
	uint32_t len = read;
	buf->arr = (uint8_t *) malloc(len);
	if (buf->arr == nullptr)
	{
		throw std::exception();
	}
	buf->arr_length = len;
	buf->length = len;
	buf->offset = 0;
	memcpy(buf->arr, src.arr+firstpacket, len);

	src.offset = src.offset - read;
	memset(src.arr+firstpacket, 0, read);

	return buf;
}

buffer_t *buffer_create_reply(uint16_t sender, uint16_t requester, uint64_t invno, bool isfull, bool execdspec, buffer_t &result, int8_t contactno, uint32_t hash_len)
{
	buffer_t *rep = (buffer_t *) malloc(sizeof(buffer_t));

	uint32_t len = 4 + 1 + 2 + 2 + 8 + 1 + 1 + 2 + result.length + hash_len; // len + type + sender + requester + invno + contact + flags + resultlen + result + hash
	rep->arr = (uint8_t *) calloc(1, len);
	rep->offset = 0;
	rep->length = len;
	rep->arr_length = len;

	// Common Header
	buffer_add_int(*rep, len);
	buffer_add_byte(*rep, REPLY_TYPE);

	// Reply Header
	buffer_add_short(*rep, sender);
	buffer_add_short(*rep, requester);
	buffer_add_long(*rep, (int64_t) invno);
	buffer_add_byte(*rep, contactno);
	uint8_t flags = 0x00;
	if (isfull)
		flags |= 0x01;
	if (execdspec)
		flags |= 0x02;
	buffer_add_byte(*rep, flags);

	// Payload
	buffer_add_short(*rep, (int16_t) result.length);
	buffer_add_buffer(*rep, result);

	return rep;
}

static inline void buffer_create_reply_into_buffer(buffer_t &buf, uint16_t sender, uint16_t requester, uint64_t invno, bool isfull, bool execdspec, buffer_t &result, int8_t contactno, uint32_t hash_len)
{
	uint32_t len = 4 + 1 + 2 + 2 + 8 + 1 + 1 + 2 + result.length + hash_len; // len + type + sender + requester + invno + contact + flags + resultlen + result + hash

	// Common Header
	buffer_add_int(buf, len);
	buffer_add_byte(buf, REPLY_TYPE);

	// Reply Header
	buffer_add_short(buf, sender);
	buffer_add_short(buf, requester);
	buffer_add_long(buf, (int64_t) invno);
	buffer_add_byte(buf, contactno);
	uint8_t flags = 0x00;
	if (isfull)
		flags |= 0x01;
	if (execdspec)
		flags |= 0x02;
	buffer_add_byte(buf, flags);

	// Payload
	buffer_add_short(buf, (int16_t) result.length);
	buffer_add_buffer(buf, result);
}

static inline void buffer_create_request_into_buffer(buffer_t &buf, uint16_t sender, uint64_t invno, bool ispanic, bool isread, bool useroopt, buffer_t &payload, uint32_t hash_len)
{
	uint32_t len = 4 + 1 + 2 + 8 + 1 + 2 + payload.length + hash_len;

	// Common Header
	buffer_add_int(buf, len);
	buffer_add_byte(buf, REQUEST_TYPE);

	// Request header
	buffer_add_short(buf, sender);
	buffer_add_long(buf, invno);
	uint8_t flags = 0x00;
	if (ispanic)
		flags = 0x01;
	else if (isread)
		flags = 0x02;
	if (useroopt)
		flags |= 0x04;
	buffer_add_byte(buf, flags);

	// Payload
	buffer_add_short(buf, (int16_t) payload.length);
	buffer_add_buffer(buf, payload);
}

void buffer_destroy(buffer_t *buf)
{
	if (buf->arr != nullptr)
	{
		free(buf->arr);
	}
	free(buf);
}

void buffer_advance(buffer_t &buf, uint32_t len)
{
	if (buf.length < len)
	{
		// Not enough space to advance buffer
		throw std::exception();
	}
	buf.length -= len;
	buf.offset += len;
}

void buffer_rewind(buffer_t &buf, uint32_t len)
{
	assert(buf.offset >= len);
	buf.length += len;
	buf.offset -= len;
}

void buffer_set_offset(buffer_t &buf, uint32_t offset)
{
	if (offset > buf.offset)
	{
		buffer_advance(buf, offset - buf.offset);
	}
	else if (offset < buf.offset)
	{
		buffer_rewind(buf, buf.offset - offset);
	}
}

uint32_t buffer_as_packet_get_length(buffer_t &buf)
{
	assert(buf.length >= 4);
	return buffer_as_packet_get_length_on_offset(buf, buf.offset);
}

uint32_t buffer_as_packet_get_length_on_offset(buffer_t &buf, uint32_t offset)
{
	return buf.arr[offset + LENGTH_OFFSET + 0] << 24 |
	       buf.arr[offset + LENGTH_OFFSET + 1] << 16 |
	       buf.arr[offset + LENGTH_OFFSET + 2] << 8 |
	       buf.arr[offset + LENGTH_OFFSET + 3] << 0;
}

void buffer_as_packet_set_length(buffer_t &buf, uint32_t len)
{
	assert(buf.length >= 4);
	buf.arr[buf.offset + LENGTH_OFFSET + 0] = (uint8_t) ((len & 0xff000000) >> 24);
	buf.arr[buf.offset + LENGTH_OFFSET + 1] = (uint8_t) ((len & 0x00ff0000) >> 16);
	buf.arr[buf.offset + LENGTH_OFFSET + 2] = (uint8_t) ((len & 0x0000ff00) >> 8);
	buf.arr[buf.offset + LENGTH_OFFSET + 3] = (uint8_t) ((len & 0x000000ff) >> 0);
}

int64_t buffer_as_packet_get_invocation(buffer_t &buf, uint32_t offset)
{
	return (int64_t)(buf.arr[offset + REQUEST_INVOCATION_OFFSET + 0]) << 56 |
		   (int64_t)(buf.arr[offset + REQUEST_INVOCATION_OFFSET + 1]) << 48 |
		   (int64_t)(buf.arr[offset + REQUEST_INVOCATION_OFFSET + 2]) << 40 |
		   (int64_t)(buf.arr[offset + REQUEST_INVOCATION_OFFSET + 3]) << 32 |
		   buf.arr[offset + REQUEST_INVOCATION_OFFSET + 4] << 24 |
		   buf.arr[offset + REQUEST_INVOCATION_OFFSET + 5] << 16 |
		   buf.arr[offset + REQUEST_INVOCATION_OFFSET + 6] << 8 |
		   buf.arr[offset + REQUEST_INVOCATION_OFFSET + 7] << 0;
}

void buffer_as_packet_change_write(buffer_t &buf, uint32_t offset)
{
	buf.arr[offset + REQUEST_FLAGS_OFFSET] &= 0x2;
}

uint8_t buffer_as_packet_get_flags(buffer_t &buf, uint32_t offset)
{
	return buf.arr[offset + REQUEST_FLAGS_OFFSET];
}

void buffer_add_byte(buffer_t &buf, int8_t val)
{
	buf.arr[buf.offset + 0] = (uint8_t) val;
	buffer_advance(buf, 1);
}

void buffer_add_short(buffer_t &buf, int16_t val)
{
	buf.arr[buf.offset + 0] = (uint8_t) ((val & 0xff00) >> 8);
	buf.arr[buf.offset + 1] = (uint8_t) ((val & 0x00ff) >> 0);
	buffer_advance(buf, 2);
}

void buffer_add_int(buffer_t &buf, int32_t val)
{
	buf.arr[buf.offset + 0] = (uint8_t) ((val & 0xff000000) >> 24);
	buf.arr[buf.offset + 1] = (uint8_t) ((val & 0x00ff0000) >> 16);
	buf.arr[buf.offset + 2] = (uint8_t) ((val & 0x0000ff00) >> 8);
	buf.arr[buf.offset + 3] = (uint8_t) ((val & 0x000000ff) >> 0);
	buffer_advance(buf, 4);
}

void buffer_add_long(buffer_t &buf, int64_t val)
{
	buf.arr[buf.offset + 0] = (uint8_t) ((val & 0xff00000000000000) >> 56);
	buf.arr[buf.offset + 1] = (uint8_t) ((val & 0x00ff000000000000) >> 48);
	buf.arr[buf.offset + 2] = (uint8_t) ((val & 0x0000ff0000000000) >> 40);
	buf.arr[buf.offset + 3] = (uint8_t) ((val & 0x000000ff00000000) >> 32);
	buf.arr[buf.offset + 4] = (uint8_t) ((val & 0x00000000ff000000) >> 24);
	buf.arr[buf.offset + 5] = (uint8_t) ((val & 0x0000000000ff0000) >> 16);
	buf.arr[buf.offset + 6] = (uint8_t) ((val & 0x000000000000ff00) >> 8);
	buf.arr[buf.offset + 7] = (uint8_t) ((val & 0x00000000000000ff) >> 0);
	buffer_advance(buf, 8);
}

void buffer_add_buffer(buffer_t &buf, buffer_t &val)
{
	if (val.length == 0)
	{
		return;
	}
	uint32_t len = val.length;
	memcpy(buf.arr+buf.offset, val.arr+val.offset, val.length);
	buffer_advance(buf, len);
}

bool buffer_is_handshake(buffer_t &buf)
{
	return buf.arr[buf.offset] == 0x42;
}

int16_t buffer_as_handshake_get_clino(buffer_t &buf)
{
	return buf.arr[buf.offset + 1] << 8 | buf.arr[buf.offset + 2] << 0;
}

int64_t buffer_as_request_get_invocation(buffer_t &buf)
{
	return (int64_t)(buf.arr[buf.offset + REQUEST_INVOCATION_OFFSET + 0]) << 56 |
		   (int64_t)(buf.arr[buf.offset + REQUEST_INVOCATION_OFFSET + 1]) << 48 |
		   (int64_t)(buf.arr[buf.offset + REQUEST_INVOCATION_OFFSET + 2]) << 40 |
		   (int64_t)(buf.arr[buf.offset + REQUEST_INVOCATION_OFFSET + 3]) << 32 |
		   buf.arr[buf.offset + REQUEST_INVOCATION_OFFSET + 4] << 24 |
		   buf.arr[buf.offset + REQUEST_INVOCATION_OFFSET + 5] << 16 |
		   buf.arr[buf.offset + REQUEST_INVOCATION_OFFSET + 6] << 8 |
		   buf.arr[buf.offset + REQUEST_INVOCATION_OFFSET + 7] << 0;
}

uint16_t buffer_as_request_get_sender(buffer_t &buf)
{
	return buf.arr[buf.offset + REQUEST_SENDER_OFFSET + 0] << 8 |
	       buf.arr[buf.offset + REQUEST_SENDER_OFFSET + 1] << 0;
}

uint8_t buffer_as_request_get_flags(buffer_t &buf)
{
	return buf.arr[buf.offset + REQUEST_FLAGS_OFFSET];
}

uint16_t buffer_as_request_get_payload_length(buffer_t &buf)
{
	return buf.arr[buf.offset + REQUEST_PAYLOAD_LENGTH_OFFSET + 0] << 8 |
	       buf.arr[buf.offset + REQUEST_PAYLOAD_LENGTH_OFFSET + 1] << 0;
}

uint8_t *buffer_as_request_get_payload(buffer_t &buf)
{
	return buf.arr + buf.offset + REQUEST_PAYLOAD_OFFSET;
}

void buffer_as_request_set_clino(buffer_t &buf, uint16_t clino)
{
	buf.arr[buf.offset + REQUEST_SENDER_OFFSET + 0] = (uint8_t) ((clino & 0xff00) >> 8);
	buf.arr[buf.offset + REQUEST_SENDER_OFFSET + 1] = (uint8_t) ((clino & 0x00ff) >> 0);
}

void buffer_as_request_set_invno(buffer_t &buf, int64_t invno)
{
	buf.arr[buf.offset + REQUEST_INVOCATION_OFFSET + 0] = (uint8_t) ((invno & 0xff00000000000000) >> 56);
	buf.arr[buf.offset + REQUEST_INVOCATION_OFFSET + 1] = (uint8_t) ((invno & 0x00ff000000000000) >> 48);
	buf.arr[buf.offset + REQUEST_INVOCATION_OFFSET + 2] = (uint8_t) ((invno & 0x0000ff0000000000) >> 40);
	buf.arr[buf.offset + REQUEST_INVOCATION_OFFSET + 3] = (uint8_t) ((invno & 0x000000ff00000000) >> 32);
	buf.arr[buf.offset + REQUEST_INVOCATION_OFFSET + 4] = (uint8_t) ((invno & 0x00000000ff000000) >> 24);
	buf.arr[buf.offset + REQUEST_INVOCATION_OFFSET + 5] = (uint8_t) ((invno & 0x0000000000ff0000) >> 16);
	buf.arr[buf.offset + REQUEST_INVOCATION_OFFSET + 6] = (uint8_t) ((invno & 0x000000000000ff00) >> 8);
	buf.arr[buf.offset + REQUEST_INVOCATION_OFFSET + 7] = (uint8_t) ((invno & 0x00000000000000ff) >> 0);
}

void buffer_as_request_set_flags(buffer_t &buf, uint8_t flags)
{
	buf.arr[buf.offset + REQUEST_FLAGS_OFFSET] = flags;
}

bool buffer_is_reply(buffer_t &buf)
{
	return buf.arr[buf.offset + TYPE_OFFSET] == REPLY_TYPE;
}

int64_t buffer_as_reply_get_invocation(buffer_t &buf)
{
	return (int64_t)(buf.arr[buf.offset + REPLY_INVOCATION_OFFSET + 0]) << 56 |
	       (int64_t)(buf.arr[buf.offset + REPLY_INVOCATION_OFFSET + 1]) << 48 |
	       (int64_t)(buf.arr[buf.offset + REPLY_INVOCATION_OFFSET + 2]) << 40 |
	       (int64_t)(buf.arr[buf.offset + REPLY_INVOCATION_OFFSET + 3]) << 32 |
	       buf.arr[buf.offset + REPLY_INVOCATION_OFFSET + 4] << 24 |
	       buf.arr[buf.offset + REPLY_INVOCATION_OFFSET + 5] << 16 |
	       buf.arr[buf.offset + REPLY_INVOCATION_OFFSET + 6] << 8 |
	       buf.arr[buf.offset + REPLY_INVOCATION_OFFSET + 7] << 0;
}

int16_t buffer_as_reply_get_sender(buffer_t &buf)
{
	return buf.arr[buf.offset + REPLY_SENDER_OFFSET + 0] << 8 |
	       buf.arr[buf.offset + REPLY_SENDER_OFFSET + 1] << 0;
}

uint8_t buffer_as_reply_get_flags(buffer_t &buf)
{
	return buf.arr[buf.offset + REPLY_FLAGS_OFFSET];
}

uint16_t buffer_as_reply_get_payload_length(buffer_t &buf)
{
	return buf.arr[buf.offset + REPLY_PAYLOAD_LENGTH_OFFSET + 0] << 8 |
	       buf.arr[buf.offset + REPLY_PAYLOAD_LENGTH_OFFSET + 1] << 0;
}

uint8_t *buffer_as_reply_get_payload(buffer_t &buf)
{
	return buf.arr + buf.offset + REPLY_PAYLOAD_OFFSET;
}

bool buffer_equals(buffer_t *a, buffer_t *b)
{
	if (a == b || (a == nullptr && b == nullptr))
		return true;

	if (a != nullptr && b == nullptr)
		return false;

	if (a == nullptr && b != nullptr)
		return false;

	if (a->length != b->length)
		return false;

	// Length of 0 is equal
	if (a->length == 0)
		return true;

	return memcmp(a->arr+a->offset, b->arr+b->offset, a->length) == 0;
}


#endif

#endif //TBFT_C_BUFFER_H

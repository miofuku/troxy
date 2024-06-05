/**
 * @author weichbr
 */

#ifndef TBFT_C_BIO_H
#define TBFT_C_BIO_H

#include <openssl/bio.h>
#include "../native/native_impl.h"
#include "debug.h"

static int create(BIO *bio)
{
	bio->init = 1;
	bio->shutdown = 0;
	bio->num = 0;
	return 1;
}

static int destroy(BIO *bio)
{
	return 1;
}

static long ctrl(BIO *bio, int cmd, long, void *)
{
	switch (cmd)
	{
		case BIO_CTRL_FLUSH:
			return 1;
		default:
			break;
	}
	return 0;
}

static int read(BIO *bio, char *dest, int dest_len)
{
	debugE("(%p, %p, %d)", bio, (void *)dest, dest_len);
	if (dest_len <= 0)
	{
		debugL("Read request with length zero or negative");
		return 0;
	}
	shared_data_t *sdata = (shared_data_t *)bio->ptr;
	buffer_t *src = sdata->recvbuf;
	if (src == nullptr)
	{
		debugL("No recvbuffer available, might be called from write");
		sdata->to_recv = dest_len;
		return 0;
	}
	if (src->length < (uint32_t)dest_len)
	{
		debugL("Not enough to read: %d < %d", src->length, dest_len);
		sdata->to_recv = dest_len;
		return 0;
	}
	memcpy(dest, src->arr+src->offset, (size_t) dest_len);

	debug("Copied:\n");
	hexdump((char *)src->arr+src->offset, (uint32_t) dest_len);

	src->offset += dest_len;
	src->length -= dest_len;
	debugL("");
	return dest_len;
}

static int write(BIO *bio, const char *src, int src_len)
{
	debugE("(%p, %p, %d)", bio, (void *)src, src_len);
	if (src_len <= 0)
	{
		debugL("Write request with length zero or negative");
		return 0;
	}
	shared_data_t *sdata = (shared_data_t *)bio->ptr;
	buffer_t *dest = sdata->sendbuf;
	if (dest == nullptr)
	{
		debugL("No sendbuffer available, should never happen!");
		assert(0);
	}
	if (dest->length < (uint32_t)src_len)
	{
		debugL("Not enough to write: %d < %d", dest->length, src_len);
		return 0;
	}
	memcpy(dest->arr+dest->offset, src, (size_t) src_len);

	debug("Copied:\n");
	hexdump((char *)dest->arr+dest->offset, (uint32_t) src_len);

	dest->offset += src_len;
	dest->length -= src_len;

	debugL("");
	return src_len;
}

BIO_METHOD __bio_buffer = {
		BIO_TYPE_SOURCE_SINK | BIO_TYPE_DESCRIPTOR, // Type ???
		"Troxy buffer", // Name
		write, // int (*bwrite)(BIO *, const char *, int);
		read, // int (*bread)(BIO *, char *, int);
		NULL, // int (*bputs)(BIO *, const char *);
		NULL, // int (*bgets)(BIO *, char *, int);
		ctrl, // long (*ctrl)(BIO *, int, long, void *);
		create, // int (*create)(BIO *);
		destroy, // int (*destroy)(BIO *);
		NULL, // long (*callback_ctrl)(BIO *, int, bio_info_cb *);
};

#endif //TBFT_C_BIO_H

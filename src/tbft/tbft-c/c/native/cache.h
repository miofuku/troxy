//
// Created by bli
//

#ifndef TBFT_C_CACHE_H
#define TBFT_C_CACHE_H


#include <map>
#include "Buffer.h"
#ifdef SGX
#include "dummies.h"
#endif
#include "debug.h"
#include <openssl/sha.h>

#include "rwlock.h"

#define HASHFUNC SHA1
#define HASHLEN SHA_DIGEST_LENGTH

class Hash
{
public:
    class HashComparator
	{
	public:
		bool operator() (const Hash *lhs, const Hash *rhs) const
		{
#if DEBUG
			debug("Comparing hashes %p and %p\n", lhs, rhs);
			bool smaller = memcmp(lhs->__hash, rhs->__hash, HASHLEN) < 0;
			debug("Left is %s than right\n", smaller ? "smaller" : "bigger");
			return smaller;
#else
			return memcmp(lhs->__hash, rhs->__hash, HASHLEN) < 0;
#endif
		}
	};

	explicit Hash(buffer_t *buf) : __hash()
	{
		debugE("");
		if (buf == nullptr)
		{
			debug("Hash request is empty\n");
			return;
		}
		if (buffer_as_request_get_payload_length(*buf) == 0)
		{
			debug("Hash payload is empty, hashing nothing\n");
			return;
		}
		HASHFUNC(buffer_as_request_get_payload(*buf), buffer_as_request_get_payload_length(*buf), __hash);
		debugL("");
	};
	~Hash() = default;

private:
	uint8_t  __hash[HASHLEN];

};

class HashRep
{
public:
	explicit HashRep(buffer_t *buf) : __hash()
	{
		debugE("");
		if (buf == nullptr)
		{
			debug("Hash reply is empty\n");
			return;
		}
		if (buffer_as_reply_get_payload_length(*buf) == 0)
		{
			debug("HashRep payload is empty, hashing nothing\n");
			return;
		}
		HASHFUNC(buffer_as_reply_get_payload(*buf), buffer_as_reply_get_payload_length(*buf), __hash);
		debugL("");
	};
	~HashRep() = default;
	uint8_t  __hash[HASHLEN];
};

class Cache
{
public:
    static Cache& getInstance()
	{
		static Cache instance;
		return instance;
	}

	void insertReply(Hash *request, buffer_t *reply);
	void deleteReply(buffer_t *request);
	bool hasRequest(buffer_t *request);
	bool matchReply(buffer_t *request, buffer_t *reply);
	HashRep *getReply(buffer_t *request);

private:
	std::map<Hash *, HashRep *, Hash::HashComparator> __msgs;
	rwlock_t __lock;

	Cache() : __lock({}) {};
	Cache(Cache const&);
	void operator=(Cache const&);

};


#endif //TBFT_C_CACHE_H

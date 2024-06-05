//
// Created by bli
//

#include "cache.h"

bool Cache::hasRequest(buffer_t *request)
{
	debugE("");
	Hash h(request);
	read_lock(&__lock);
	auto it = __msgs.find(&h);
	if ((it == __msgs.end()))
	{
		read_unlock(&__lock);
		debug("Cache to this request not found\n");
		debugL("");
		return false;
	}
	read_unlock(&__lock);
	debug("Cache entry found\n");
	debugL("");
	return true;
}

void Cache::deleteReply(buffer_t *request)
{
	debugE("");
	Hash h(request);
	write_lock(&__lock);
	auto it = __msgs.find(&h);
	if ((it != __msgs.end()))
	{
		debug("Found cache entry, delete\n");
		Hash *req = it->first;
		HashRep *rep = it->second;
		__msgs.erase(it);
		delete rep;
		delete req;
		debug("Deleted cache entry\n");
	}
	write_unlock(&__lock);
	debug("Deleted already\n");
	debugL("");
}

void Cache::insertReply(Hash *hreq, buffer_t *reply)
{
	debugE("");
	debug("Insert cache entry\n");
	auto *hrep = new HashRep(reply);
	write_lock(&__lock);
	__msgs[hreq] = hrep;
	write_unlock(&__lock);
	debugL("");
}

bool Cache::matchReply(buffer_t *request, buffer_t *reply)
{
	debugE("");
	Hash h(request);
	HashRep hrep(reply);
	read_lock(&__lock);
	auto it = __msgs.find(&h);
	if ((it == __msgs.end()))
	{
		read_unlock(&__lock);
		debug("Cache to this request not found\n");
		debugL("");
		return false;
	}
	read_unlock(&__lock);
	debug("Cache entry found\n");
	if (memcmp(it->second, &hrep, HASHLEN)==0)
	{
		debug("Match reply cache\n");
		debugL("");
		return true;
	}
	debug("Reply does not match hash\n");
	debugL("");
	return false;
}

HashRep *Cache::getReply(buffer_t *request)
{
	debugE("");
	Hash h(request);
	read_lock(&__lock);
	auto it = __msgs.find(&h);
	if ((it != __msgs.end()))
	{
		debug("Found cache entry\n");
		HashRep *hash = it->second;
		read_unlock(&__lock);
		debugL("");
		return hash;
	}
	read_unlock(&__lock);
	debugL("");
	return nullptr;
}

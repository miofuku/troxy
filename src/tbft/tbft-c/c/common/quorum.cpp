/**
 * @author $author$
 */

#ifdef SGX
#include "dummies.h"
#endif


#include "quorum.h"
#include "../native/cache.h"

QuorumCollector::QuorumCollector(int nvoters, int threshold, int maxprops) : threshold(threshold), msgstore(nvoters), proposals(maxprops), votes(maxprops), voter_to_vote(nvoters)
{
//	debugE("(%d, %d, %d)", nvoters, threshold, maxprops);
	clear();

//	debugL("");
}

QuorumCollector::~QuorumCollector()
{
	debug("Destructing QuorumCollector\n");
}

bool QuorumCollector::is_quorum_stable()
{
	debug("%d >= %d && %s\n", votes.get_leading_count(), threshold, proposals.contains_key(votes.get_leading_class()) ? "true" : "false");
	return votes.get_leading_count() >= threshold && proposals.contains_key(votes.get_leading_class());
}

buffer_t *QuorumCollector::get_leading_proposal()
{
	if (msgstore.size() == 0)
		//throw tbftexception("No proposal has been added yet.");
		throw std::exception();

	return proposals.get(votes.get_leading_class());
}

bool QuorumCollector::is_already_known(int voter, buffer_t *msg)
{
	debug("Got buffer %p from voter %d\n", msg, voter);
	buffer_t *curmsg = msgstore.get(voter);
	debug("Retrieved current message, got %p\n", curmsg);

	if (curmsg == NULL)
	{
		debug("No message from this voter yet\n");
		return false;
	}

	debug("Comparing messages\n");
	if (buffer_equals(curmsg, msg))
	{
		debug("Messages are equal\n");
		return true;
	}

	debug("Found a message but that is not equal to the one we were given!\n");
	return false;
	//throw tbftexception("Message found but not equal to given one!");
//	throw std::exception();
}

bool QuorumCollector::add_vote(int voter, buffer_t *msg, buffer_t vote, bool is_prop)
{
	if (msg == NULL)
	{
		debug("Message is null!\n");
		//throw tbftexception("Cannot add null message to voter");
		throw std::exception();
	}

	debug("Putting message into store\n");
	msgstore.put(voter, msg);

	debug("Adding vote\n");
	int vc = votes.add_vote(vote);

	debug("Setting voter class %d\n", vc);
	voter_to_vote[voter] = vc;

	debug("Checking whether message is proposal and if it is known\n");
	if (is_prop && !proposals.contains_key(vc))
	{
		debug("Adding message as proposal\n");
		proposals.put(vc, msg);
	}

	debug("Returning\n");
	return is_quorum_stable();
}

bool QuorumCollector::add_fastread(int voter, int repno, buffer_t *msg, buffer_t vote)
{
	count++;

	if (iscomplete || count>2)
	{
		debug("Already complete\n");
		clear();
		return false;
	}

	if (msg == NULL)
	{
		debug("Message is null!\n");
		//throw tbftexception("Cannot add null message to voter");
		throw std::exception();
	}

	if (voter==repno)
	{
		debug("Got local reply\n");
		fastread = msg;
	}
	else
	{
		if (buffer_as_reply_get_payload_length(*msg)==HASHLEN)
		{
			debug("Get cached reply\n");
			fastreadhash = buffer_as_reply_get_payload(*msg);
		}
		else if (buffer_as_reply_get_payload_length(*msg)!=0)
		{
			count--;
			debug("Not required reply\n");
			return false;
		}
		else
		{
			debug("Get null reply cache\n");
			nullleaderhash = true;
		}

	}


	if (fastread==nullptr || (fastreadhash==nullptr && !nullleaderhash))
	{
		debug("Quorum incomplete\n");
		return false;
	}
	else
	{
		iscomplete = true;
		HashRep *rephash = new HashRep(fastread);
		if (nullleaderhash || memcmp(fastreadhash, (uint8_t *) rephash, HASHLEN)!=0)
		{
			debug("Hash does not match reply\n");
			delete rephash;
			return false;
		}
		else
		{
			debug("Hash matches reply\n");
			delete rephash;
			return true;
		}
	}
}

int QuorumCollector::get_num_of_votes()
{
	return count;
}

buffer_t *QuorumCollector::getfastread()
{
	return fastread;
}

bool QuorumCollector::isComplete()
{
	return iscomplete;
}

bool QuorumCollector::isleadernull()
{
	return nullleaderhash;
}

void QuorumCollector::clear()
{
	// We need to free all messages and votes
	// Since all messages are in msgstore, we only need to free there
	// Freeing the pointers in proposals isn't necessary, since it contains the same pointers as msgstore

	std::for_each(msgstore.get_vector().begin(), msgstore.get_vector().end(), [](buffer_t* b)
	{
		if (b)
		{
			//printf("Destroyed buffer @ %p\n", b);fflush(stdout);
			buffer_destroy(b);
		}
	});
	msgstore.clear();
	proposals.clear();

	// Votes must not be cleared with buffer_destroy, as their internal pointer points to the whole reply contained in msgstore
	//std::for_each(votes.get_slots().get_vector().begin(), votes.get_slots().get_vector().end(), [](buffer_t* b) { if (b) free(b); });
	votes.clear();

	std::fill(voter_to_vote.begin(), voter_to_vote.end(), -1);
	// No need to destroy fastread here, as it is also saved in the msgstore
	fastread = nullptr;
	fastreadhash = nullptr;
	nullleaderhash = false;
	count = 0;
	iscomplete = false;
}

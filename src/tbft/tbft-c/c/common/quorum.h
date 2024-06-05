/**
 * @author $author$
 */

#ifndef TBFT_C_QUORUM_H
#define TBFT_C_QUORUM_H

#include "debug.h"
#include "Buffer.h"

#include <cstddef>
#include <vector>
#include <stdint.h>
#include "slots.h"
#include "votes.h"
#include <stdio.h>

class QuorumCollector
{
public:
	QuorumCollector(int nvoters, int threshold, int maxprops);
	virtual ~QuorumCollector();
	bool is_quorum_stable();
	buffer_t *get_leading_proposal();
	bool is_already_known(int voter, buffer_t *msg);
	bool add_vote(int voter, buffer_t *msg, buffer_t vote, bool is_prop);
	void clear();
	int get_num_of_diff_votes() { return votes.get_diff_votes(); };
	bool add_fastread(int voter, int repno, buffer_t *msg, buffer_t vote);
	int get_num_of_votes();
	buffer_t *getfastread();
	bool isComplete();
	bool isleadernull();

private:
	int threshold;
	Slots<buffer_t> msgstore;
	Slots<buffer_t> proposals;
	Votes<buffer_t> votes;
	std::vector<int> voter_to_vote;
	int count;
	buffer_t *fastread;
	uint8_t *fastreadhash;
	bool iscomplete;
	bool nullleaderhash;

};



#endif //TBFT_C_QUORUM_H

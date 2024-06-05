/**
 * @author $author$
 */

#ifndef TBFT_C_VOTES_H
#define TBFT_C_VOTES_H

#include "debug.h"
#include <cstddef>
#include <vector>
#include <cstring>
#include "slots.h"
#include "Buffer.h"

class Comparable
{
public:
	virtual bool equals(void *other) = 0;
	virtual ~Comparable() {};
};

template<class T> class Votes
{
public:
	Votes(size_t capacity) : votes(new T[capacity]), capacity(capacity), filled(0), vote_counts(new int[capacity]), leading_vote(0)
	{
		clear();
	};
	virtual ~Votes()
	{
		debug("Destructing Votes\n");
		delete[] vote_counts;
		delete[] votes;
	};
	int add_vote(T &vote)
	{
		int clsidx = classIndex(vote);

		int cnt = ++(vote_counts[clsidx]);
#if DEBUG
		int prevcnt = vote_counts[clsidx];
#endif
		debug("Class index of vote: %d\n", clsidx);
		debug("Count of class: %d -> %d\n", prevcnt, cnt);

		if (clsidx != leading_vote && cnt > get_leading_count())
		{
			leading_vote = clsidx;
			debug("Leading vote is now %d\n", clsidx);
		}

		return clsidx;
	};
	int get_leading_count() { return vote_counts[leading_vote]; };
	int get_leading_class() { return leading_vote; };
	int get_diff_votes() { return filled; }
	void clear()
	{
		memset(votes, 0, capacity * sizeof(T));
		filled = 0;
		memset(vote_counts, 0, capacity * sizeof(int));
		leading_vote = 0;
		count = 0;
	};

	int add_fastread(T &vote)
	{
		count++;
		int clsidx = classIndex(vote);
		int cnt = ++(vote_counts[clsidx]);

		if (count == 2 && cnt == 2)
		{
			return clsidx;
		}

		return 0;
	}

private:
	T *votes;
	size_t capacity;
	int filled;
	int *vote_counts;
	int leading_vote;

	int count;

	//int classIndex(T *vote) { (void)vote; throw std::exception(); };
	int classIndex(buffer_t &vote)
	{
		for (int i = 0; i < filled; ++i)
		{
			buffer_t &cls = votes[i];

			if (buffer_equals(&vote, &cls))
			{
				return i;
			}
		}

		int idx = filled;

		votes[idx] = vote;

		++filled;

		return idx;
	};
};

#endif //TBFT_C_VOTES_H

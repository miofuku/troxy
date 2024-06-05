/**
 * @author $author$
 */

#ifndef TBFT_C_SLOTS_H
#define TBFT_C_SLOTS_H


#include <stddef.h>
#include <vector>
#include <algorithm>
#include "debug.h"

template<class T> class Slots {
public:
	Slots(size_t capacity) : _size(0), capacity(capacity), slots(capacity) {};
	virtual ~Slots() {};
	T *get(int key)
	{
		if (key < 0 || (size_t)key >= capacity)
		{
			return NULL;
		}
		return this->slots[key];
	};
	T *put(int key, T *val)
	{
		if (key < 0 || (size_t)key >= this->capacity)
		{
			//throw tbftexception("key is out-of-bounds");
			throw std::exception();
		}

		if (val == NULL)
		{
			return this->removeKey(key);
		}

		T *old = this->get(key);

		this->slots[key] = val;

		if (old == NULL)
		{
			this->_size++;
		}

		return old;
	};
	T *removeKey(int key)
	{
		T *old = this->get(key);

		this->slots[key] = NULL;

		if (old != NULL)
		{
			this->_size--;
		}

		return 0;
	};
	void clear()
	{
//		debug("clearing %ld / %ld\n", this->_size, capacity);
		if (this->_size == 0)
			return;

		std::fill(slots.begin(), slots.end(), (T*)NULL);

		this->_size = 0;
	};
	bool contains_key(int key) { return this->slots[key] != NULL; };
	size_t size() { return this->_size; };
	std::vector<T*>& get_vector() { return slots; };

private:
	size_t _size;
	size_t capacity;
	std::vector<T*> slots;
};

#endif //TBFT_C_SLOTS_H

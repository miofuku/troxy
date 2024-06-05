#ifndef _COUNTER_COUNTER_T_H_
#define _COUNTER_COUNTER_T_H_

#include "trinx_types.h"


struct __attribute__ ((packed)) counter_t : public counter_value_t
{

    static counter_t &for_value(counter_value_t &value)
    {
        return *((counter_t *) &value);
    }

    static const counter_t *for_value(const counter_value_t *value)
    {
        return (const counter_t *) value;
    }

    counter_t(void) : counter_t( 0, 0 )
    {
    }

    counter_t(uint64_t high, uint64_t low)
    {
        this->high = high;
        this->low  = low;
    }

    counter_t(const counter_t &other) : counter_t( other.high, other.low )
    {
    }


    explicit counter_t(const counter_value_t &other) : counter_t( other.high, other.low )
    {
    }


    void set(uint64_t high, uint64_t low)
    {
        this->high = high;
        this->low  = low;
    }


    counter_t & operator=(const counter_t &other)
    {
        high = other.high;
        low  = other.low;

        return *this;
    }

    counter_t & operator=(const counter_value_t &other)
    {
        high = other.high;
        low  = other.low;

        return *this;
    }

    bool operator==(const counter_t &other) const
    {
        return high==other.high && low==other.low;
    }

    bool operator!=(const counter_t &other) const
    {
        return high!=other.high || low!=other.low;
    }

    bool operator<(const counter_t &other) const
    {
        return high<other.high || ( high==other.high && low<other.low );
    }

    bool operator>(const counter_t &other) const
    {
        return other < *this;
    }

    bool operator<=(const counter_t &other) const
    {
        return high<other.high || ( high==other.high && low<=other.low );
    }

    bool operator>=(const counter_t &other) const
    {
        return other <= *this;
    }

};

#endif

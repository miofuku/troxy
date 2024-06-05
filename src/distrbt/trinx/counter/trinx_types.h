#ifndef _COUNTER_TRINX_TYPES_H_
#define _COUNTER_TRINX_TYPES_H_

#include <stdint.h>

typedef uint16_t tssid_t;
typedef uint32_t ctrno_t;

typedef struct counter_value_t
{
    uint64_t high;
    uint64_t low;
} counter_value_t;

#endif /* _COUNTER_TRINX_TYPES_H_ */

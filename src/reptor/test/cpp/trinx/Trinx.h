#ifndef _TEST_TRINX_TRINX_H_
#define _TEST_TRINX_TRINX_H_

#ifdef SGX
    #include "sgx/untrusted/SgxTrinx.h"
#elif defined CASH
    #include "cash/counter/CASHCounter.h"
#elif defined DUMMYCOUNTER
    #include "counter/DummyTrinx.h"
#else
    #include "counter/Trinx.h"
#endif


static const uint8_t KEY[] = { "secret" };

static const size_t MAC_CERTIFICATE_SIZE     = 32;
static const size_t COUNTER_CERTIFICATE_SIZE = 32;


#endif /* _TEST_TRINX_TRINX_H_ */

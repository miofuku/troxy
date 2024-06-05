#ifndef _CASH_COUNTER_CASHCOUNTER_H_
#define _CASH_COUNTER_CASHCOUNTER_H_

#include <stdexcept>

#include "counter/trinx_types.h"
#include "counter/trinx_cmds.h"
#include "counter/counter_t.h"


class Trinx
{

    static const size_t COUNTER_CERTIFICATE_SIZE = 32;

    const tssid_t m_id;

    FILE *m_cash;

public:

    Trinx(tssid_t id, ctrno_t ncounters, const uint8_t *key, size_t keylen);

    ~Trinx();

    tssid_t GetID() const
    {
        return m_id;
    }

    ctrno_t GetNumberOfCounters() const
    {
        return 1;
    }

    size_t GetCounterCertificateSize() const
    {
        return COUNTER_CERTIFICATE_SIZE;
    }

    size_t GetMacCertificateSize() const
    {
        return -1;
    }

    void CreateIndependentCertificate(const uint8_t *msg, size_t msglen, ctrno_t ctrno, counter_t ctrval,
                                      uint8_t *certbuf);

    bool VerifyIndependentCertificate(const uint8_t *msg, size_t msglen, const uint8_t *certbuf,
                                      tssid_t tmid, ctrno_t ctrno, counter_t ctrval);

    void ExecuteCommand(void *cmd)
    {
        throw std::logic_error( "Unsupported operation" );
    }

    void Touch() const
    {
    }

};


#endif /* _CASH_COUNTER_CASHCOUNTER_H_ */

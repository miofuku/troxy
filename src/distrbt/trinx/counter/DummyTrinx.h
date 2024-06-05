#ifndef _COUNTER_DUMMYTRINX_H_
#define _COUNTER_DUMMYTRINX_H_

#include <stddef.h>
#include <stdint.h>

#include "HMac.h"
#include "trinx_types.h"
#include "trinx_cmds.h"
#include "counter_t.h"


class Trinx
{

    const tssid_t  m_id;
    const ctrno_t m_ncounters;

public:

    Trinx(tssid_t id, ctrno_t ncounters, const uint8_t *key, size_t keylen)
            : m_id( id ), m_ncounters( ncounters )
    {
    }

    tssid_t GetID() const
    {
        return m_id;
    }

    ctrno_t GetNumberOfCounters() const
    {
        return m_ncounters;
    }

    size_t GetCounterCertificateSize() const
    {
        return HMac::SIZE;
    }

    size_t GetMacCertificateSize() const
    {
        return HMac::SIZE;
    }

    void ExecuteCommand(void *cmd)
    {
        if( trinx::is_verification( *(cmdid_t *) cmd ) )
            ((certification_header *) cmd)->set_result( cmdres_t::CERTIFICATE_VALID );
    }

    void Touch() const
    {
    }

};


#endif /* _COUNTER_DUMMYTRINX_H_ */

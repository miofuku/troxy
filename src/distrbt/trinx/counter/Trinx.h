#ifndef _COUNTER_TRINX_H_
#define _COUNTER_TRINX_H_

#include <stddef.h>
#include <stdint.h>

#include "HMac.h"
#include "trinx_types.h"
#include "trinx_cmds.h"
#include "counter_t.h"


class Trinx
{

    const tssid_t m_id;

    HMac         m_hmac;
    ctrno_t      m_ncounters;
    counter_t *  m_counters;

    void CheckCounter(ctrno_t ctrno) const;
    void CheckCounters(ctrno_t nctrs, const ctrno_t *ctrnos) const;

    void ProcessBatch(uint8_t *cmd);
    void VerifyCertificate(certification_header &cmd);
    void CreateTrustedGroupCertificate(certification_header &cmd);
    void CreateTrustedMacCertificate(certification_header &cmd);
    void CreateIndependentCounterCertificate(certification_header &cmd);
    void CreateContinuingCounterCertificate(certification_header &cmd);

    void StartMac(const certification_header &cmd);
    void CreateMac(const certification_header &cmd, uint32_t bodysize);
    void CheckTssID(tssid_t tssid) const;
    void UpdateIndependentCounter(create_independent_counter_body &body);
    void UpdateContinuingCounter(create_continuing_counter_body &body);

public:

    Trinx(tssid_t id, ctrno_t ncounters, const uint8_t *key, size_t keylen);

    ~Trinx();

    tssid_t GetID() const;
    ctrno_t GetNumberOfCounters() const;
    size_t  GetCounterCertificateSize() const;
    size_t  GetMacCertificateSize() const;

    void ExecuteCommand(void *cmd);

    void Touch() const;

};


inline tssid_t Trinx::GetID() const
{
    return m_id;
}


inline size_t Trinx::GetCounterCertificateSize() const
{
    return HMac::SIZE;
}


inline ctrno_t Trinx::GetNumberOfCounters() const
{
    return m_ncounters;
}


inline size_t Trinx::GetMacCertificateSize() const
{
    return HMac::SIZE;
}


inline void Trinx::Touch() const
{

}


#endif /* _COUNTER_TRINX_H_ */

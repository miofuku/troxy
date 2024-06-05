#ifndef _SGX_UNTRUSTED_SGXTRINX_H_
#define _SGX_UNTRUSTED_SGXTRINX_H_

#include <string>

#include "counter/trinx_types.h"
#include "counter/counter_t.h"
#include "common/sgx/EnclaveLauncher.h"
#include "common/sgx/sgx_exception.h"
#include "trinx_u.h"


class Trinx
{

    static std::string *m_enclavepath;

    tssid_t           m_id;
    sgx_enclave_id_t m_eid;

public:

    static void Init(const char *enclavepath);

    static void CleanUp();

    Trinx(tssid_t id, ctrno_t ncounters, const uint8_t *key, size_t keylen);
    ~Trinx();

    tssid_t GetID() const;
    size_t  GetNumberOfCounters() const;
    size_t  GetCounterCertificateSize() const;
    size_t  GetMacCertificateSize() const;

    void ExecuteCommand(void *cmd);

    void Touch() const;

};


inline tssid_t Trinx::GetID() const
{
    return m_id;
}


inline size_t Trinx::GetNumberOfCounters() const
{
    size_t retval;

    sgx_status_t sgxret = ecall_get_number_of_counters( m_eid, &retval, m_id );
    sgx_check_status( sgxret );

    return retval;
}


inline size_t Trinx::GetCounterCertificateSize() const
{
    size_t retval;

    sgx_status_t sgxret = ecall_get_counter_certificate_size( m_eid, &retval, m_id );
    sgx_check_status( sgxret );

    return retval;
}


inline size_t Trinx::GetMacCertificateSize() const
{
    size_t retval;

    sgx_status_t sgxret = ecall_get_mac_certificate_size( m_eid, &retval, m_id );
    sgx_check_status( sgxret );

    return retval;
}


inline void Trinx::ExecuteCommand(void *cmd)
{
    sgx_status_t sgxret = ecall_execute_command( m_eid, m_id, cmd );
    sgx_check_status( sgxret );
}


inline void Trinx::Touch() const
{
    sgx_status_t sgxret = ecall_touch( m_eid, m_id );
    sgx_check_status( sgxret );
}


#endif /* _SGX_UNTRUSTED_SGXTRINX_H_ */

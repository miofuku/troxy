#include <stdexcept>
#include "SgxTrinx.h"


#ifdef SGX_MULTI_COUNTER

    sgx_enclave_id_t m_multi_eid = -1;


    void Trinx::Init(const char *enclavepath)
    {
        if( m_multi_eid!=(sgx_enclave_id_t) -1 )
            throw std::logic_error( "TrInX has been already initialized." );

        m_multi_eid = EnclaveLauncher::StartEnclave( enclavepath );
    }


    void Trinx::CleanUp()
    {
        EnclaveLauncher::TerminateEnclave( m_multi_eid );
    }


    Trinx::Trinx(tssid_t id, ctrno_t ncounters, const uint8_t *key, size_t keylen)
    {
        m_eid = m_multi_eid;

        sgx_status_t sgxret = ecall_init_counter( m_eid, id, ncounters, key, keylen );
        sgx_check_status( sgxret );

        m_id = id;
    }


    Trinx::~Trinx()
    {
        ecall_remove_counter( m_eid, m_id );
    }

#else

    std::string *Trinx::m_enclavepath = NULL;


    void Trinx::Init(const char *enclavepath)
    {
        if( m_enclavepath )
            delete m_enclavepath;

        m_enclavepath = new std::string( enclavepath );
    }


    void Trinx::CleanUp()
    {
    }


    Trinx::Trinx(tssid_t id, ctrno_t ncounters, const uint8_t *key, size_t keylen)
    {
        m_eid = EnclaveLauncher::StartEnclave( m_enclavepath->c_str() );

        sgx_status_t sgxret = ecall_init_counter( m_eid, id, ncounters, key, keylen );
        sgx_check_status( sgxret );

        m_id = id;
    }


    Trinx::~Trinx()
    {
        EnclaveLauncher::TerminateEnclave( m_eid );
    }

#endif

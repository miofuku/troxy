#include "trinx_t.h"

#ifdef DUMMYCOUNTER
    #include "counter/DummyTrinx.h"
#else
    #include "counter/Trinx.h"
#endif

//#define SGX_MULTI_COUNTER

#ifndef SGX_MAX_COUNTERS
    #define SGX_MAX_TSS 128
#endif


#ifdef SGX_MULTI_COUNTER
static Trinx *m_tms[ SGX_MAX_TSS ] = { NULL };

Trinx *tm(tssid_t tssid)
{
    return m_tms[ tssid ];
}

void set_tm(tssid_t tssid, Trinx *tm)
{
    m_tms[ tssid ] = tm;
}
#else
static Trinx *m_tm = NULL;

Trinx *tm(tssid_t tssid)
{
    return m_tm;
}

void set_tm(tssid_t tssid, Trinx *tm)
{
    m_tm = tm;
}
#endif


void ecall_init_counter(tssid_t tssid, ctrno_t ncounters, const uint8_t* key, size_t keylen)
{
    #ifdef SGX_MULTI_COUNTER
    if( tssid>=SGX_MAX_TSS )
    {
        ocall_print_string( "TM ID out of range!!\n" );
        abort();
    }
    #endif

    set_tm( tssid, new Trinx( tssid, ncounters, key, keylen ) );
}


void ecall_remove_counter(tssid_t tssid)
{
    delete tm( tssid );
    set_tm( tssid, NULL );
}


size_t ecall_get_counter_certificate_size(tssid_t tssid)
{
    return tm( tssid )->GetCounterCertificateSize();
}


size_t ecall_get_mac_certificate_size(tssid_t tssid)
{
    return tm( tssid )->GetMacCertificateSize();
}


size_t ecall_get_number_of_counters(tssid_t tssid)
{
    return tm( tssid )->GetNumberOfCounters();
}


void ecall_execute_command(tssid_t tssid, void *cmd)
{
    tm( tssid )->ExecuteCommand( cmd );
}


void ecall_touch(tssid_t tssid)
{
    tm( tssid )->Touch();
}

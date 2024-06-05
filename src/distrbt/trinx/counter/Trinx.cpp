#include <cstring>
#include <string>
#include <stdexcept>
#include "Trinx.h"

#include "common/format.h"
#include "endian.h"

Trinx::Trinx(tssid_t id, ctrno_t ncounters, const uint8_t *key, size_t keylen) : m_id( id )
{
    m_ncounters = ncounters;
    m_counters  = ncounters==0 ? NULL : new counter_t[ ncounters ];

    m_hmac.InitKey( key, keylen );
}


Trinx::~Trinx()
{
    delete[] m_counters;
}


void Trinx::ExecuteCommand(void *msg)
{
    switch( *(cmdid_t *) msg )
    {
    case CREATE_TGROUP_CERTIFICATE_ID:
        CreateTrustedGroupCertificate( *(certification_header *) msg );
        break;
    case CREATE_TMAC_CERTIFICATE_ID:
        CreateTrustedMacCertificate( *(certification_header *) msg );
        break;
    case CREATE_INDEPENDENT_CERTIFICATE_ID:
        CreateIndependentCounterCertificate( *(certification_header *) msg );
        break;
    case CREATE_CONTINUING_CERTIFICATE_ID:
        CreateContinuingCounterCertificate( *(certification_header *) msg );
        break;
    case VERIFY_TGROUP_CERTIFICATE_ID:
    case VERIFY_TMAC_CERTIFICATE_ID:
    case VERIFY_INDEPENDENT_CERTIFICATE_ID:
    case VERIFY_CONTINUING_CERTIFICATE_ID:
        VerifyCertificate( *(certification_header *) msg );
        break;
    case BATCH_ID:
        ProcessBatch( (uint8_t *) msg );
        break;
    default:
        throw std::invalid_argument( "Unknown command type." );
    }
}


void Trinx::ProcessBatch(uint8_t *batch)
{
    uint8_t *cmd = batch + sizeof( cmdid_t );

    while( *((cmdid_t *) cmd)!=EMPTY_ID )
    {
        ExecuteCommand( cmd );
        cmd += sizeof(certification_header) + ((certification_header *) cmd)->bodysize;
    }
}


void Trinx::VerifyCertificate(certification_header &cmd)
{
    StartMac( cmd );

    m_hmac.Update( cmd.bodydata.ptr, cmd.bodysize-1 );

    uint8_t calcproof[ HMac::SIZE ];
    m_hmac.Finalize( calcproof );

    int res = std::memcmp( cmd.certdata.ptr, calcproof, HMac::SIZE );
    cmd.set_result( res==0 ? cmdres_t::CERTIFICATE_VALID : cmdres_t::CERTIFICATE_INVALID );
}


void Trinx::CreateTrustedGroupCertificate(certification_header &cmd)
{
    StartMac( cmd );
    CreateMac( cmd, 0 );
}


void Trinx::CreateTrustedMacCertificate(certification_header &cmd)
{
    StartMac( cmd );

    create_trusted_mac_body &body = *((create_trusted_mac_body *) cmd.bodydata.ptr);
    CheckTssID( body.tssid );

    CreateMac( cmd, sizeof( body ) );
}


void Trinx::CreateIndependentCounterCertificate(certification_header &cmd)
{
    StartMac( cmd );

    create_independent_counter_body &body = *((create_independent_counter_body *) cmd.bodydata.ptr);

    CheckTssID( body.tssid );
    UpdateIndependentCounter( body );

    CreateMac( cmd, sizeof( body ) );
}


void Trinx::CreateContinuingCounterCertificate(certification_header &cmd)
{
    StartMac( cmd );

    create_continuing_counter_body &body = *((create_continuing_counter_body *) cmd.bodydata.ptr);

    CheckTssID( body.tssid );
    UpdateContinuingCounter( body );

    CreateMac( cmd, sizeof( body ) );
}


void Trinx::StartMac(const certification_header &cmd)
{
    m_hmac.Update( cmd.msgdata.ptr, cmd.msgsize );
}


void Trinx::CreateMac(const certification_header &cmd, uint32_t bodysize)
{
    if( cmd.bodysize!=bodysize )
        throw std::invalid_argument( "Invalid certificate input." );

    m_hmac.Update( cmd.bodydata.ptr, cmd.bodysize );
    m_hmac.Finalize( cmd.certdata.ptr );
}


void Trinx::CheckTssID(tssid_t tssid) const
{
    if( tssid!=m_id )
        throw std::out_of_range( "Invalid TSS ID." );
}


void Trinx::UpdateIndependentCounter(create_independent_counter_body &body)
{
    CheckCounter( body.ctrno );

    if( body.ctrval<=m_counters[ body.ctrno ] )
        throw std::invalid_argument( "Invalid counter value." );

    m_counters[ body.ctrno ] = body.ctrval;
}


void Trinx::UpdateContinuingCounter(create_continuing_counter_body &body)
{
    CheckCounter( body.ctrno );

    if( body.ctrval<m_counters[ body.ctrno ] )
        throw std::invalid_argument( "Invalid counter value." );

    body.prev = m_counters[ body.ctrno ];
    m_counters[ body.ctrno ] = body.ctrval;
}


void Trinx::CheckCounter(ctrno_t ctrno) const
{
    if( ctrno>=m_ncounters )
        throw std::out_of_range( "Invalid counter number." );
}


void Trinx::CheckCounters(ctrno_t nctrs, const ctrno_t *ctrnos) const
{
    if( nctrs==0 )
        throw std::invalid_argument( "At least one counter must be given." );

    size_t last = ctrnos[ 0 ];
    CheckCounter( last );

    for( size_t i=1; i<nctrs; i++ )
    {
        size_t cur = ctrnos[ i ];

        CheckCounter( cur );

        if( cur<=last )
            throw std::invalid_argument( "Counter must be given in increasing order." );

        last = cur;
    }
}

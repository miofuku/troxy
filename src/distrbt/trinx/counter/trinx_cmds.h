#ifndef _COUNTER_TRINX_CMDS_H_
#define _COUNTER_TRINX_CMDS_H_

#include <stdexcept>
#include <cstring>

#include "trinx_types.h"
#include "counter_t.h"


enum cmdid_t : uint8_t
{
    EMPTY_ID                            = 0,
    CREATE_TGROUP_CERTIFICATE_ID        = 1,
    VERIFY_TGROUP_CERTIFICATE_ID        = 2,
    CREATE_TMAC_CERTIFICATE_ID          = 3,
    VERIFY_TMAC_CERTIFICATE_ID          = 4,
    CREATE_INDEPENDENT_CERTIFICATE_ID   = 5,
    VERIFY_INDEPENDENT_CERTIFICATE_ID   = 6,
    CREATE_CONTINUING_CERTIFICATE_ID    = 7,
    VERIFY_CONTINUING_CERTIFICATE_ID    = 8,
    BATCH_ID                            = 100
};


namespace trinx
{

inline bool is_verification(cmdid_t cmdid)
{
    return ( cmdid&0x01 )==0;
}

}


enum cmdres_t : uint8_t
{
    NONE                = 0,
    CERTIFICATE_INVALID = 1,
    CERTIFICATE_VALID   = 2
};


// It's not that easy to determine the size of a native pointer in Java. Thus, a fixed size is used for pointers.
union __attribute__ ((packed)) data_t
{
    uint64_t    pointer_value;
    uint8_t *   ptr;
};


struct __attribute__ ((packed)) certification_header
{
    cmdid_t          cmdid;
    uint32_t         msgsize;
    uint32_t         msgoff;
    uint32_t         certoff;
    uint32_t         bodysize;
    uint32_t         bodyoff;
    data_t           msgdata;
    data_t           certdata;
    data_t           bodydata;

    void init_adjacent_body()
    {
        bodydata.ptr = ((uint8_t *) this) + sizeof( certification_header );
        bodyoff = 0;
    }

    bool is_verification()
    {
        return trinx::is_verification( cmdid );
    }

    cmdres_t get_result()
    {
        return (cmdres_t) bodydata.ptr[ bodysize-1 ];
    }

    void set_result(cmdres_t res)
    {
        bodydata.ptr[ bodysize-1 ] = res;
    }

    bool is_certificate_valid()
    {
        return get_result()==cmdres_t::CERTIFICATE_VALID;
    }
};


struct __attribute__ ((packed)) verify_trusted_group_body
{
    cmdres_t        res;
};


struct __attribute__ ((packed)) create_trusted_mac_body
{
    tssid_t         tssid;
};


struct __attribute__ ((packed)) verify_trusted_mac_body
{
    tssid_t         tssid;
    cmdres_t        res;
};


struct __attribute__ ((packed)) create_independent_counter_body
{
    tssid_t         tssid;
    ctrno_t         ctrno;
    counter_t       ctrval;
};


struct __attribute__ ((packed)) verify_independent_counter_body
{
    tssid_t         tssid;
    ctrno_t         ctrno;
    counter_t       ctrval;
    cmdres_t        res;
};


struct __attribute__ ((packed)) create_continuing_counter_body
{
    tssid_t         tssid;
    ctrno_t         ctrno;
    counter_t       ctrval;
    counter_t       prev;
};


struct __attribute__ ((packed)) verify_continuing_counter_body
{
    tssid_t         tssid;
    ctrno_t         ctrno;
    counter_t       ctrval;
    counter_t       prev;
    cmdres_t        res;
};


namespace trinx
{

inline int body_size(cmdid_t cmdid)
{
    switch( cmdid )
    {
    case CREATE_TGROUP_CERTIFICATE_ID:
        return 0;
    case VERIFY_TGROUP_CERTIFICATE_ID:
        return sizeof( verify_trusted_group_body );
    case CREATE_TMAC_CERTIFICATE_ID:
        return sizeof( create_trusted_mac_body );
    case VERIFY_TMAC_CERTIFICATE_ID:
        return sizeof( verify_trusted_mac_body );
    case CREATE_INDEPENDENT_CERTIFICATE_ID:
        return sizeof( create_independent_counter_body );
    case VERIFY_INDEPENDENT_CERTIFICATE_ID:
        return sizeof( verify_independent_counter_body );
    case CREATE_CONTINUING_CERTIFICATE_ID:
        return sizeof( create_continuing_counter_body );
    case VERIFY_CONTINUING_CERTIFICATE_ID:
        return sizeof( verify_continuing_counter_body );
    default:
        // Not supported by SGX library?
        //throw std::invalid_argument( "Unknown command type." );
        throw std::exception();
    }
}

}


template<class B> struct __attribute__ ((packed)) certification_command
{
    certification_header header;
    B                    body;

    certification_command()
    {
        header.init_adjacent_body();
    }

    certification_command &init()
    {
        header.bodydata.ptr = (uint8_t *) &body;
        return *this;
    }

    bool is_verification()
    {
        return header.is_verification();
    }

    cmdres_t get_result()
    {
        return header.get_result();
    }

    bool is_certificate_valid()
    {
        return header.is_certificate_valid();
    }

    certification_command &type(cmdid_t cmdid)
    {
        header.cmdid    = cmdid;
        header.bodysize = trinx::body_size( cmdid );
        return *this;
    }

    certification_command &createTrustedGroup()
    {
        return type( CREATE_TGROUP_CERTIFICATE_ID );
    }

    certification_command &verifyTrustedGroup()
    {
        return type( VERIFY_TGROUP_CERTIFICATE_ID );
    }

    certification_command &createTrustedMac()
    {
        return type( CREATE_TMAC_CERTIFICATE_ID );
    }

    certification_command &verifyTrustedMac()
    {
        return type( VERIFY_TMAC_CERTIFICATE_ID );
    }

    certification_command &createIndependent()
    {
        return type( CREATE_INDEPENDENT_CERTIFICATE_ID );
    }

    certification_command &verifyIndependent()
    {
        return type( VERIFY_INDEPENDENT_CERTIFICATE_ID );
    }

    certification_command &createContinuing()
    {
        return type( CREATE_CONTINUING_CERTIFICATE_ID );
    }

    certification_command &verifyContinuing()
    {
        return type( VERIFY_CONTINUING_CERTIFICATE_ID );
    }

    certification_command &tss(tssid_t tssid)
    {
        body.tssid = tssid;
        return *this;
    }

    certification_command &counter(ctrno_t ctrno)
    {
        body.ctrno = ctrno;
        return *this;
    }

    certification_command &counter(tssid_t tssid, ctrno_t ctrno)
    {
        return tss( tssid ).counter( ctrno );
    }

    certification_command &values(counter_t ctrval, counter_t prev)
    {
        value( ctrval );
        body.prev = prev;
        return *this;
    }

    certification_command &value(uint64_t high, uint64_t low)
    {
        return value( counter_t( high, low ) );
    }

    certification_command &value(counter_t ctrval)
    {
        body.ctrval = ctrval;
        return *this;
    }

    certification_command &value(uint64_t lowval)
    {
        body.ctrval.low = lowval;
        return *this;
    }

    certification_command &result(cmdres_t result)
    {
        header.set_result( result );
        return *this;
    }

    certification_command &message(uint8_t *msg, size_t msgsize, uint8_t *cert)
    {
        header.msgdata.ptr  = msg;
        header.msgsize      = msgsize;
        header.certdata.ptr = cert;
        return *this;
    }
};


#endif /* _COUNTER_TRINX_CMDS_H_ */

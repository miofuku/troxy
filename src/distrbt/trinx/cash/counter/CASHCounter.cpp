#include <cstdio>
#include <cstring>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdexcept>
#include <limits>
#include "CASHCounter.h"
#include "counter/endian.h"


static const char DEVICE[] = "/dev/counter";


static void check_io_status(size_t nbytes, size_t expected)
{
    if( nbytes!=expected )
        throw std::runtime_error( "IO error." );
}


Trinx::Trinx(tssid_t id, ctrno_t ncounters, const uint8_t *key, size_t keylen) : m_id( id )
{
    if( ncounters!=1 )
        throw std::logic_error( "Unsupported operation" );

    // The kernel driver of CASH expects buffers of exactly the required size.
    // It's better to handle this low-level. Therefore no C++ streams here.
    m_cash = std::fopen( DEVICE, "r+" );

    if( m_cash==NULL )
        throw std::runtime_error( "Cannot open CASH device." );

    setvbuf( m_cash, NULL, _IONBF, 0 );
}


Trinx::~Trinx()
{
    std::fclose( m_cash );
}


void Trinx::CreateIndependentCertificate(const uint8_t *msg, size_t msglen,
                                         ctrno_t ctrno, counter_t ctrval,
                                         uint8_t *certbuf)
{
    if( ctrno!=0 )
        throw std::logic_error( "Unsupported operation" );
    if( ctrval.high!=0 )
        throw std::logic_error( "Unsupported operation" );
    if( ctrval.low>std::numeric_limits<int32_t>::max() )
        throw std::logic_error( "Unsupported operation" );

    char iobuf[ 40 ];

    std::memset( iobuf, 0, 4 );
    std::memcpy( iobuf+4, msg, 32 );

    size_t ret = std::fwrite( iobuf, 1, 36, m_cash );
    check_io_status( ret, 36 );

    ret = std::fread( iobuf, 1, 40, m_cash );
    check_io_status( ret, 40 );

    std::memcpy( certbuf, iobuf, COUNTER_CERTIFICATE_SIZE );
}


bool Trinx::VerifyIndependentCertificate(const uint8_t *msg, size_t msglen, const uint8_t *certbuf,
                                         tssid_t tmid, ctrno_t ctrno, counter_t ctrval)
{
    if( ctrno!=0 )
        throw std::logic_error( "Unsupported operation" );
    if( ctrval.high!=0 )
        throw std::logic_error( "Unsupported operation" );
    if( ctrval.low>std::numeric_limits<int32_t>::max() )
        throw std::logic_error( "Unsupported operation" );

    char iobuf[ 80 ];
    int  pos = 0;

    put_bigendian( (uint8_t *) iobuf, (uint32_t) 1 );
    pos += 4;
    std::memcpy( iobuf+pos, msg, msglen );
    pos += msglen;
    std::memcpy( iobuf+pos, certbuf, COUNTER_CERTIFICATE_SIZE );
    pos += COUNTER_CERTIFICATE_SIZE;
    std::memset( iobuf+pos, 0, 4 );
    pos += 4;
    put_bigendian( (uint8_t *) iobuf+pos, (uint32_t) tmid );

    size_t ret = std::fwrite( iobuf, 1, 80, m_cash );
    check_io_status( ret, 80 );

    ret = std::fread( iobuf, 1, 4, m_cash );

    check_io_status( ret, 4 );

    return iobuf[ 3 ]==1;
}

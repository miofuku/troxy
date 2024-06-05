// See http://grepcode.com/file/repository.grepcode.com/java/root/jdk/openjdk/6-b14/com/sun/crypto/provider/HmacCore.java?av=h#HmacCore.HmacSHA256

#include <assert.h>
#include <stdint.h>

#include "HMac.h"

static const size_t BLOCK_SIZE = MessageDigest::SIZE*2;


HMac::HMac()
{
    m_keyipad = new uint8_t[ BLOCK_SIZE ];
    m_keyopad = new uint8_t[ BLOCK_SIZE ];
}


HMac::~HMac()
{
    delete[] m_keyopad;
    delete[] m_keyipad;
}


void HMac::InitKey(const uint8_t *key, size_t keylen)
{
    uint8_t buf[ SIZE ];

    if( keylen>BLOCK_SIZE )
    {
        m_digest.Clear();
        m_digest.Create( key, keylen, buf );
        key    = buf;
        keylen = SIZE;
    }

    for( size_t i=0; i<BLOCK_SIZE; i++ )
    {
        uint8_t d = i<keylen? key[ i ] : 0;
        m_keyipad[i] = ( d ^ 0x36 );
        m_keyopad[i] = ( d ^ 0x5c );
    }
}


void HMac::Create(const uint8_t *data, size_t datalen, uint8_t *mac)
{
    Update( data, datalen );
    Finalize( mac );
}


void HMac::Update(const uint8_t *data, size_t datalen)
{
    CheckFirst();

    m_digest.Update( data, datalen );
}


void HMac::Finalize(uint8_t *mac)
{
    CheckFirst();

    m_digest.Finalize( mac );

    m_digest.Update( m_keyopad, BLOCK_SIZE );
    m_digest.Update( mac, SIZE );
    m_digest.Finalize( mac );
}


void HMac::CheckFirst()
{
    if( !m_digest.HasData() )
        m_digest.Update( m_keyipad, BLOCK_SIZE );
}

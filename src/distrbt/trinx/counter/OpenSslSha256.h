#ifndef _COUNTER_OPENSSLSHA256_H_
#define _COUNTER_OPENSSLSHA256_H_

#include <openssl/sha.h>


inline MessageDigest::MessageDigest()
{
    m_cntxt = new SHA256_CTX();
    m_first = true;
}


inline MessageDigest::~MessageDigest()
{
    delete (SHA256_CTX *) m_cntxt;
}


inline void MessageDigest::Clear()
{
    SHA256_Init( (SHA256_CTX *) m_cntxt );
}


inline void MessageDigest::Create(const uint8_t *data, size_t datalen, uint8_t *digest)
{
    assert( m_first );
    SHA256( data, datalen, digest );
}


inline void MessageDigest::Update(const uint8_t *data, size_t datalen)
{
    CheckFirst();

    SHA256_Update( (SHA256_CTX *) m_cntxt, data, datalen );
}


inline void MessageDigest::Finalize(uint8_t *digest)
{
    CheckFirst();

    SHA256_Final( digest, (SHA256_CTX *) m_cntxt );
    m_first = true;
}


#endif /* _COUNTER_OPENSSLSHA256_H_ */

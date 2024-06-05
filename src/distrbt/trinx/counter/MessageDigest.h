#ifndef _COUNTER_MESSAGEDIGEST_H_
#define _COUNTER_MESSAGEDIGEST_H_

#include <assert.h>
#include <stddef.h>
#include <stdint.h>


class MessageDigest
{

    void * m_cntxt;
    bool   m_first;

public:

    static const size_t SIZE = 32;

    MessageDigest();
    ~MessageDigest();

    size_t size() const;

    bool HasData() const;
    void Clear();

    void Create(const uint8_t *data, size_t datalen, uint8_t *digest);

    void Update(const uint8_t *data, size_t datalen);
    void Finalize(uint8_t *digest);

private:

    void CheckFirst();

};


inline size_t MessageDigest::size() const
{
    return SIZE;
}


inline bool MessageDigest::HasData() const
{
    return !m_first;
}


inline void MessageDigest::CheckFirst()
{
    if( m_first )
    {
        Clear();
        m_first = false;
    }
}


#if SGXCRYPTO
    #include "SgxSha256.h"
#else
    #include "OpenSslSha256.h"
#endif


#endif /* _COUNTER_MESSAGEDIGEST_H_ */

#ifndef _COUNTER_HMAC_H_
#define _COUNTER_HMAC_H_

#include <stddef.h>
#include <stdint.h>

#include "MessageDigest.h"


class HMac
{

    MessageDigest m_digest;

    uint8_t * m_keyipad;
    uint8_t * m_keyopad;

public:

    static const size_t SIZE = MessageDigest::SIZE;

    HMac();
    ~HMac();

    size_t size() const;

    void InitKey(const uint8_t *key, size_t keylen);

    void Create(const uint8_t *data, size_t datalen, uint8_t *mac);

    void Update(const uint8_t *data, size_t datalen);
    void Finalize(uint8_t *mac);

private:

    void CheckFirst();

};


inline size_t HMac::size() const
{
    return SIZE;
}


#endif /* _COUNTER_HMAC_H_ */

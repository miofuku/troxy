#ifndef _COUNTER_SGXSHA256_H_
#define _COUNTER_SGXSHA256_H_

#include <sgx_tcrypto.h>


inline MessageDigest::MessageDigest()
{
    sgx_sha256_init( (sgx_sha_state_handle_t *) &m_cntxt );
    m_first = true;
}


inline MessageDigest::~MessageDigest()
{
    sgx_sha256_close( (sgx_sha_state_handle_t *) m_cntxt );
}


inline void MessageDigest::Clear()
{
    sgx_sha256_close( (sgx_sha_state_handle_t *) m_cntxt );
    sgx_sha256_init( (sgx_sha_state_handle_t *) &m_cntxt );
}


inline void MessageDigest::Create(const uint8_t *data, size_t datalen, uint8_t *digest)
{
    sgx_sha256_msg( data, datalen, (sgx_sha256_hash_t *) digest );
}


inline void MessageDigest::Update(const uint8_t *data, size_t datalen)
{
    CheckFirst();

    sgx_sha256_update( data, datalen, (sgx_sha_state_handle_t) m_cntxt );
}


inline void MessageDigest::Finalize(uint8_t *digest)
{
    CheckFirst();

    sgx_sha256_get_hash( (sgx_sha_state_handle_t) m_cntxt, (sgx_sha256_hash_t *) digest );
    m_first = true;
}


#endif /* _COUNTER_SGXSHA256_H_ */

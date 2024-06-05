#ifndef _SGX_SGX_EXCEPTION_H_
#define _SGX_SGX_EXCEPTION_H_

#include <exception>
#include <sgx_urts.h>


class sgx_error : public std::exception
{
    sgx_status_t m_code;

public:

    sgx_error(sgx_status_t code);

    sgx_status_t code() const;
    const char*  what() const throw ();
};


inline sgx_error::sgx_error(sgx_status_t code)
{
    m_code = code;
}


inline sgx_status_t sgx_error::code() const
{
    return m_code;
}


inline void sgx_check_status(sgx_status_t status)
{
    if( status!=SGX_SUCCESS )
        throw sgx_error( status );
}


#endif /* _SGX_SGX_EXCEPTION_H_ */

#ifndef _SGX_ENCLAVELAUNCHER_H_
#define _SGX_ENCLAVELAUNCHER_H_

#include <sgx_eid.h>


class EnclaveLauncher
{

public:

    static sgx_enclave_id_t StartEnclave(const char* enclavepath);

    static void             TerminateEnclave(sgx_enclave_id_t eid);

};


#endif /* _SGX_ENCLAVELAUNCHER_H_ */

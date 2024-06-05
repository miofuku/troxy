#include "EnclaveLauncher.h"
#include "sgx_exception.h"


sgx_enclave_id_t EnclaveLauncher::StartEnclave(const char* enclavepath)
{
    sgx_enclave_id_t   eid;
    sgx_launch_token_t token = {0};
    int                updated;

    sgx_status_t ret = sgx_create_enclave( enclavepath, SGX_DEBUG_FLAG, &token, &updated, &eid, 0 );
    sgx_check_status( ret );

    return eid;
}


void EnclaveLauncher::TerminateEnclave(sgx_enclave_id_t eid)
{
    sgx_status_t ret = sgx_destroy_enclave( eid );
    sgx_check_status( ret );
}

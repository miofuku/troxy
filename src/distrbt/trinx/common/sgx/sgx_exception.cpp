#include "sgx_exception.h"


const char* sgx_error::what() const throw ()
{
    switch( m_code )
    {
    case SGX_ERROR_UNEXPECTED:
        return "Unexpected error occurred.";
    case SGX_ERROR_INVALID_PARAMETER:
        return "Invalid parameter.";
    case SGX_ERROR_OUT_OF_MEMORY:
        return "Out of memory.";
    case SGX_ERROR_ENCLAVE_LOST:
        return "Power transition occurred.";
    case SGX_ERROR_INVALID_ENCLAVE:
        return "Invalid enclave image.";
    case SGX_ERROR_INVALID_ENCLAVE_ID:
        return "Invalid enclave identification.";
    case SGX_ERROR_INVALID_SIGNATURE:
        return "Invalid enclave signature.";
    case SGX_ERROR_OUT_OF_EPC:
        return "Out of EPC memory.";
    case SGX_ERROR_NO_DEVICE:
        return "Invalid SGX device.";
    case SGX_ERROR_MEMORY_MAP_CONFLICT:
        return "Memory map conflicted.";
    case SGX_ERROR_INVALID_METADATA:
        return "Invalid enclave metadata.";
    case SGX_ERROR_DEVICE_BUSY:
        return "SGX device was busy.";
    case SGX_ERROR_INVALID_VERSION:
        return "Enclave version was invalid.";
    case SGX_ERROR_INVALID_ATTRIBUTE:
        return "Enclave was not authorized.";
    case SGX_ERROR_ENCLAVE_FILE_ACCESS:
        return "Can't open enclave file.";
    default:
        return "Unknown status code.";
    }
}

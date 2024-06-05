ifndef _SGX_CONFIG_MK_
_SGX_CONFIG_MK_ := 1

SGX_SDKDIR  ?= /opt/intel/sgxsdk
SGX_INCDIR  := $(SGX_SDKDIR)/include
SGX_LIBDIR  := $(SGX_SDKDIR)/lib64
SGX_SIGNER  := $(SGX_SDKDIR)/bin/x64/sgx_sign
SGX_EDGER8R := $(SGX_SDKDIR)/bin/x64/sgx_edger8r

endif

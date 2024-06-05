include c/common/consts.mk

# The following places might need to be edited:
# - c/common/consts.mk to change the values of:
#   - OpenSSL include (OSSL_INCL)
#   - OpenSSL library name (OSSL_LIB)
#   - Java JNI include (JNI_INCL)
#   - SGX Mode (SGX_MODE)
#   - SGX SDK Path (SGX_SDK)

all: common native jni
jni: native sgx
	@make -C c/jni

native: common
	@make -C c/native

common:
	@make -C c/common

sgx: native
	@make -C c/sgx

clean:
	@make -C c/native clean
	@make -C c/jni clean
	@make -C c/common clean
	@make -C c/sgx clean

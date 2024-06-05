/**
 * @author weichbr
 */

#include "dummies.h"
#include <exception>
#include <stdlib.h>
#include <debug.h>
#include "openssl/ssl.h"
#include "enclave_t.h"

//int printf(const char *f, ...)
//{
//	char buf[250] = { '\0' };
//	va_list ap;
//	vsnprintf(buf, 1000, f, ap);
//	va_end(ap);
//	ocall_print_string(buf);
//	return 0;
//}

//int fflush(FILE *f)
//{
//	return 0; // TODO use ocall
//}

// _ZSt17__throw_bad_allocv

// _ZN11stlpmtx_std17__throw_bad_allocEv

extern "C" void _ZSt17__throw_bad_allocv()
{
	throw std::exception();
}


extern "C" sgx_status_t ocall_println_string(const char* str)
{
	ocall_print_string(str);
	ocall_print_string("\n");
	return SGX_SUCCESS;
}

extern "C" sgx_status_t ocall_malloc(void **r, size_t size)
{
	debugE("(%lu)", size);
	*r = calloc(1, size);
	debugL("ptr = %p", r);
	return SGX_SUCCESS;
}

extern "C" sgx_status_t ocall_free(void* ptr)
{
	debug("<=> ocall_free");
	free(ptr);
	return SGX_SUCCESS;
}

extern "C" sgx_status_t ocall__getpagesize(int *r)
{
	debugE("NYI");
	return SGX_SUCCESS;
}

extern "C" sgx_status_t ocall_exit(int s)
{
	debugE("NYI");
	return SGX_SUCCESS;
}

extern "C" sgx_status_t ocall_realloc(void **r, void* ptr, size_t size)
{
	debug("<=> ocall_realloc");
	*r = realloc(ptr, size);
	return SGX_SUCCESS;
}

extern "C" int ocall_fsync(int fd)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_ftruncate(int fd, long int length)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_unlink(const char* str)
{
	debugE("NYI");
	return 0;
}

extern "C" long int ocall_lseek(int fd, long int offset, int whence)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_lstat(const char *pathname, struct stat *buf, size_t size)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_fstat(int fd, struct stat *buf, size_t size)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_stat(const char *path, struct stat *buf, size_t size)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_fcntl(int fd, int cmd, void* arg, size_t size)
{
	debugE("NYI");
	return 0;
}

extern "C" char* ocall_getcwd(char *buf, size_t size)
{
	debugE("NYI");
	return NULL;
}

extern "C" void* ocall_mmap(void *addr, size_t length, int prot, int flags, int fd, long int offset)
{
	debugE("NYI");
	return NULL;
}

extern "C" void* ocall_fopen(const char *path, const char *mode)
{
	debugE("NYI");
	return NULL;
}

extern "C" size_t ocall_fwrite_copy(const void *ptr, size_t size, size_t nmemb, void *stream)
{
	debugE("NYI");
	return 0;
}

extern "C" size_t ocall_fwrite(const void *ptr, size_t size, size_t nmemb, void *stream)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_fflush(void* stream)
{
	//debugE("NYI");
	return 0;
}

extern "C" int ocall_fclose(void* fp)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_close(int fd)
{
	debugE("NYI");
	return 0;
}

extern "C" char *ocall_fgets(char *s, int size, void *stream)
{
	debugE("NYI");
	return NULL;
}

extern "C" unsigned long long ocall_get_cpuid_for_openssl(void)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_open(const char *filename, int flags, unsigned int mode)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_open64(const char *filename, int flags, unsigned int mode)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_read(int fd, void *buf, size_t count)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_write(int fd, const void *buf, size_t count)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_getpid(void)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_getuid(void)
{
	debugE("NYI");
	return 0;
}

extern "C" long int ocall_time(long int *t)
{
	debugE("NYI");
	return 0;
}

extern "C" void* ocall_calloc(size_t nmemb, size_t size)
{
	debugE("NYI");
	return NULL;
}


extern "C" void ocall_crypto_set_locking_cb(void* cb, int mode, int type, const char* file, int line)
{
	debugE("NYI");
	return;
}

extern "C" unsigned long ocall_crypto_set_id_cb(void* cb)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_bio_create(BIO* b,void*cb)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_bio_destroy(BIO* b, void*cb)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_bio_read(BIO *b, char *buf, int len, void* cb)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_bio_write(BIO *b, char *buf, int len,  void* cb)
{
	debugE("NYI");
	return 0;
}

extern "C" long ocall_bio_ctrl(BIO *b, int cmd, long argl, void *arg, void* cb)
{
	debugE("NYI");
	return 0;
}

extern "C" DH* ocall_SSL_CTX_set_tmp_dh_cb(SSL *ssl, int is_export, int keylength, void* cb)
{
	debugE("NYI");
	return NULL;
}

extern "C" void ocall_execute_ssl_ctx_info_callback(const SSL *ssl, int type, int val, void *cb)
{
	debugE("NYI");
	return;
}

extern "C" int ocall_alpn_select_cb(SSL *s, unsigned char **out, unsigned char *outlen, const unsigned char *in, unsigned int inlen, void *arg, void *cb)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_next_protos_advertised_cb(SSL *s, unsigned char **buf, unsigned int *len, void *arg, void* cb)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_pem_password_cb(char *buf, int size, int rwflag, void *userdata, void* cb)
{
	debugE("NYI");
	return 0;
}

extern "C" int ocall_new_session_callback(struct ssl_st *ssl, void *sess, void* cb)
{
	debugE("NYI");
	return 0;
}

extern "C" void ocall_remove_session_cb(SSL_CTX *ctx, void* sess, void* cb)
{
	debugE("NYI");
	return;
}

extern "C" void* ocall_get_session_cb(struct ssl_st *ssl, unsigned char *data, int len, int *copy, void* cb)
{
	debugE("NYI");
	return NULL;
}

extern "C" int ocall_ssl_ctx_callback_ctrl(SSL* ssl, int* ad, void* arg, void* cb)
{
	debugE("NYI");
	return 0;
}

extern "C" void ocall_crypto_ex_free_cb(void *parent, void *ptr, CRYPTO_EX_DATA *ad, int idx, long argl, void *argp, void* cb)
{
	debugE("NYI");
	return;
}

extern "C" void ocall_sk_pop_free_cb(void* data, void* cb)
{
	debugE("NYI");
	return;
}

extern "C" int ocall_ssl_ctx_set_next_proto_select_cb(SSL *s, unsigned char **out, unsigned char *outlen, const unsigned char *in, unsigned int inlen, void *arg, void* cb)
{
	debugE("NYI");
	return 0;
}

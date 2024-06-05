include Makefile.sgx

talos.a: enclaveshim_ocalls.o ecall_queue.o mpmc_queue.o lthread.o lthread_sched.o mempool.o sfiles libcrypto_sfiles libcrypto_cfiles libssl_cfiles compat
	ar cr talos.a enclaveshim_ocalls.o ecall_queue.o mpmc_queue.o lthread.o lthread_sched.o mempool.o $(SFILES) $(LIBCRYPTO_SFILES) $(LIBCRYPTO_CFILES) $(LIBSSL_CFILES) $(COMPAT_FILES)

libenclave_u.a: hashmap-nosgx.o ecall_queue-nosgx.o enclaveshim_ecalls.o ocalls.o cpuid-elf-x86_64-ocall.o
	ar cr libenclave_u.a hashmap-nosgx.o ecall_queue-nosgx.o enclaveshim_ecalls.o ocalls.o cpuid-elf-x86_64-ocall.o

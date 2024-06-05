include mk/_sgx_build.mk

SGX_KEY     ?= $(basename $(SGX_ENC))_private.pem
SGX_CONFIG  ?= $(basename $(SGX_ENC)).config.xml
SGX_LDS     ?= $(basename $(SGX_ENC)).lds

SGX_GEN_ENC_OBJ := $(OBJDIR)/$(TARGET)/$(SGX_GEN_ENC:%.c=%.o)

INCS    += $(SGX_INCDIR)/tlibc $(SGX_INCDIR)/stlport
SRCS    += $(SGX_ENC)
OBJS    += $(SGX_GEN_ENC_OBJ)

SGX_CRYPTOLIB ?= sgx_tcrypto

SGX_CFLAGS := -nostdinc -fvisibility=hidden -fpie -fstack-protector
CFLAGS     += $(SGX_CFLAGS)
CXXFLAGS   += $(SGX_CFLAGS) -nostdinc++
LDFLAGS    := $(LDFLAGS) \
	-Wl,--no-undefined -nostdlib -nodefaultlibs -nostartfiles -L$(SGX_LIBDIR) \
	-Wl,--whole-archive -lsgx_trts \
	-Wl,--no-whole-archive -Wl,--start-group -lsgx_tstdc -lsgx_tstdcxx -l$(SGX_CRYPTOLIB) -Wl,--end-group \
	-Wl,-Bstatic -Wl,-Bsymbolic -Wl,--no-undefined \
	-Wl,-pie,-eenclave_entry -Wl,--export-dynamic \
	-Wl,--defsym,__ImageBase=0 \
	-Wl,--version-script=$(SGX_LDS)

SGX_GEN_ENC_FLAGS := $(CFLAGS) $(addprefix -I,$(INCS)) $(addprefix -D,$(DEFS))


$(SGX_GEN_ENC_OBJ): $(SGX_GEN_ENC) | $(PREREQS)
	@mkdir -p $(dir $@)
	@$(CC) $(SGX_GEN_ENC_FLAGS) -c $< -o $@


$(SIGNED): $(RESULT)
	@echo "CREATE $@"
	@$(SGX_SIGNER) sign -key $(SGX_KEY) -enclave $(RESULT) -out $@ -config $(SGX_CONFIG)

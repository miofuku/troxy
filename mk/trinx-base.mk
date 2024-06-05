include mk/_config.mk

ifneq ($(CRYPTO), sgxlibdummy)
    SRCS += $(SRCDIR)/distrbt/trinx/counter/HMac.cpp $(SRCDIR)/distrbt/trinx/counter/Trinx.cpp
    # Does not work with SGX:
    #$(SRCDIR)/distrbt/trinx/common/format.cpp
endif

INCS += $(SRCDIR)/distrbt/trinx

ifeq (,$(filter $(CRYPTO), sgx sgxlibstd sgxlibopt sgxlibdummy))
    LIBS += crypto
else
    include mk/_sgx_config.mk
    
    INCS    += $(SGX_INCDIR)
    DEFS    += SGXCRYPTO
    LIBDIRS += $(SGX_LIBDIR)

    ifneq ($(CRYPTO), sgxlibdummy)
        ifeq ($(CRYPTO), sgxlibstd)
#            SGX_CRYPTOLIB ?= sgx_tcrypto
        else
            SGX_CRYPTOLIB ?= sgx_tcrypto
        endif
    endif

    LIBS += $(SGX_CRYPTOLIB)
endif


ifndef TARGET
    TARGET := counter
    
    ifneq ($(CRYPTO), sgx)
        TARGET := $(TARGET)-ossl
    else
        TARGET := $(TARGET)-sgx
    endif
    

    STATLIB := $(LIBDIR)/lib$(TARGET).a
    
    
    .PHONY: all build
    
    
    all: build
    
    
    build: $(STATLIB)
    
    
    include mk/_build.mk
endif

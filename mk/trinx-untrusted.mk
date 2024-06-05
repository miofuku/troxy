include mk/_config.mk
include mk/_sgx_config.mk


SGX_EDL := $(SRCDIR)/distrbt/trinx/sgx/trinx.edl
SRCS    += $(addprefix $(SRCDIR)/distrbt/trinx/, common/sgx/sgx_exception.cpp common/sgx/EnclaveLauncher.cpp sgx/untrusted/trinx_untrusted.cpp sgx/untrusted/SgxTrinx.cpp)
INCS    += $(SRCDIR)/distrbt/trinx
LIBS    += sgx_urts


ifndef TARGET
    TARGET  := trinx-untrusted

    STATLIB := $(LIBDIR)/lib$(TARGET).a
    
    
    .PHONY: all build
    
    
    all: build
    
    
    build: $(STATLIB)
    
    include mk/_sgx_build_unt.mk
    include mk/_build.mk
endif

include mk/_config.mk
include mk/_sgx_config.mk

TARGET  := trinx

SGX_EDL := $(SRCDIR)/distrbt/trinx/sgx/trinx.edl
SGX_ENC := $(SRCDIR)/distrbt/trinx/sgx/enclave/trinx_enclave.cpp
INCS    += $(SRCDIR)/distrbt/trinx

CRYPTO ?= sgxlibopt-b

ifneq ($(CRYPTO), $(CRYPTO:%-u=%))
    CONFIG_NAME := u
    CRYPTO      := $(CRYPTO:%-u=%)
    TARGET_ADD  := u
else ifneq ($(CRYPTO), $(CRYPTO:%-b=%))
    CONFIG_NAME := b
    CRYPTO      := $(CRYPTO:%-b=%)
    TARGET_ADD  := b
else ifneq ($(CRYPTO), $(CRYPTO:%-mu=%))
    CONFIG_NAME := u
    CRYPTO      := $(CRYPTO:%-mu=%)
    TARGET_ADD  := mu
    DEFS += SGX_MULTI_COUNTER
else
    CONFIG_NAME := b
    CRYPTO      := $(CRYPTO:%-mb=%)
    TARGET_ADD  := mb
    DEFS += SGX_MULTI_COUNTER
endif

ifeq (, $(filter $(CRYPTO), sgxlibstd sgxlibdummy))
    TARGET := $(TARGET)-opt
else ifeq ($(CRYPTO), sgxlibdummy)
    TARGET := $(TARGET)-dummy
    DEFS   += DUMMYCOUNTER
else
    TARGET := $(TARGET)-std
endif

TARGET     := $(TARGET)-$(TARGET_ADD)
SGX_CONFIG := $(basename $(SGX_ENC))_$(CONFIG_NAME).config.xml

RESULT := $(LIBDIR)/lib$(TARGET).$(DSO)
SIGNED := $(LIBDIR)/lib$(TARGET).signed.$(DSO)


.PHONY: all build


all: build


build: $(SIGNED)


include mk/trinx-base.mk
include mk/_sgx_build_enc.mk
include mk/_build.mk

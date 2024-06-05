CRYPTO ?= ossl

ifneq (, $(filter $(CRYPTO), ossl sgxlibstd sgxlibopt dummy cash))
    TARGET := $(TARGET)-$(CRYPTO)

    ifeq (, $(filter $(CRYPTO), dummy cash))
        CNTMK := mk/trinx-base.mk
    else ifeq ($(CRYPTO), cash)
        CNTMK := mk/cash-counter.mk
        DEFS  += CASH
    else
        DEFS += DUMMYCOUNTER
    endif
else
    ifneq ($(CRYPTO), $(CRYPTO:%-m=%))
        CONFIG_NAME=m
        CRYPTO_BASE=$(CRYPTO:%-m=%)
        DEFS += SGX_MULTI_COUNTER
    else
        CONFIG_NAME=s
        CRYPTO_BASE=$(CRYPTO:%-s=%)
    endif
    TARGET := $(TARGET)-sgx-$(CONFIG_NAME)
    DEFS   += SGX
    CNTMK  := mk/trinx-untrusted.mk mk/_sgx_build_unt.mk

    ifeq ($(CRYPTO_BASE), sgxstd)
        DEPS += $(LIBDIR)/libtrinx-std-b.signed.so
    else ifeq ($(CRYPTO_BASE), sgxdummy)
        DEPS += $(LIBDIR)/libtrinx-dummy-b.signed.so
    else
        DEPS += $(LIBDIR)/libtrinx-opt-b.signed.so
    endif
endif


$(LIBDIR)/libtrinx-%.signed.so:
	@CRYPTO=sgxlib$* $(MAKE) -f mk/trinx-sgx.mk


include $(CNTMK)

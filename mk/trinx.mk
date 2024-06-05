include mk/_config.mk


.PHONY: all build

ARTS += $(BINDIR)/trinx-test-ossl
ARTS += $(BINDIR)/trinx-test-dummy
ARTS += $(BINDIR)/trinx-test-cash

ARTS += $(LIBDIR)/libtrinx-jni-ossl.$(DSO)
ARTS += $(LIBDIR)/libtrinx-jni-dummy.$(DSO)
ARTS += $(LIBDIR)/libtrinx-jni-cash.$(DSO)

ifneq ($(SGX), no)
#    ARTS += $(LIBDIR)/trinx-std-u.signed.$(DSO)
#    ARTS += $(LIBDIR)/trinx-std-b.signed.$(DSO)
    ARTS += $(LIBDIR)/trinx-opt-u.signed.$(DSO)
    ARTS += $(LIBDIR)/trinx-opt-b.signed.$(DSO)
    ARTS += $(LIBDIR)/trinx-opt-mu.signed.$(DSO)
    ARTS += $(LIBDIR)/trinx-opt-mb.signed.$(DSO)
    ARTS += $(LIBDIR)/trinx-dummy-u.signed.$(DSO)
    ARTS += $(LIBDIR)/trinx-dummy-b.signed.$(DSO)
    ARTS += $(LIBDIR)/trinx-dummy-mu.signed.$(DSO)
    ARTS += $(LIBDIR)/trinx-dummy-mb.signed.$(DSO)

#    ARTS += $(BINDIR)/trinx-test-sgxlibstd
    ARTS += $(BINDIR)/trinx-test-sgxlibopt
    ARTS += $(BINDIR)/trinx-test-sgx-s
    ARTS += $(BINDIR)/trinx-test-sgx-m

#    ARTS += $(LIBDIR)/libtrinx-jni-sgxlibstd.$(DSO)
    ARTS += $(LIBDIR)/libtrinx-jni-sgxlibopt.$(DSO)
    ARTS += $(LIBDIR)/libtrinx-jni-sgx-s.$(DSO)
    ARTS += $(LIBDIR)/libtrinx-jni-sgx-m.$(DSO)
endif


all: build


build: $(ARTS)


$(BINDIR)/trinx-test-%:
	@CRYPTO=$* $(MAKE) -f mk/trinx-test.mk


$(LIBDIR)/libtrinx-jni-%.$(DSO):
	@CRYPTO=$* $(MAKE) -f mk/trinx-jni.mk


$(LIBDIR)/trinx-%.signed.$(DSO):
	@CRYPTO=sgxlib$* $(MAKE) -f mk/trinx-sgx.mk


include mk/_build.mk

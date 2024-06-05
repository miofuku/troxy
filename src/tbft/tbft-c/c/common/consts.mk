C := gcc
CXX := g++

DIST := $(shell if [ -f "/etc/debian_version" ]; then echo "Debian"; else echo "Arch"; fi)

ifeq ($(DIST),Debian)
OSSL_LIB := ssl
OSSL_INCL := /usr/include/openssl
JNI_INCL := /usr/lib/jvm/java-8-openjdk-amd64/include
else
OSSL_LIB := :libssl-compat.so.1.0.0
OSSL_INCL := /usr/include/openssl-1.0
JNI_INCL := /usr/lib/jvm/java-8-openjdk/include
endif

COMMONFLAGS := -fPIC -O2 -march=native -Wall -Wextra -Wno-unused-parameter -Wno-unused-function -DNDEBUG

CFLAGS := -std=gnu11 $(COMMONFLAGS)
CXXFLAGS := -std=gnu++11 $(COMMONFLAGS)

SGX_MODE ?= SIM
SGX_SDK ?= /opt/intel/sgxsdk

ifneq ($(SGX_MODE), HW)
SGXURTSLIB := sgx_urts_sim
else
SGXURTSLIB := sgx_urts
endif

LINKFLAGS :=
JNINATIVELINKFLAGS := $(LINKFLAGS) -l$(OSSL_LIB)

COMMON_INCL := c/common

LD_PROJ_INCL := -I$(COMMON_INCL) -I../../$(COMMON_INCL)
LD_COMMON_INCL := $(LD_PROJ_INCL)
LD_NATIVE_INCL := -I$(OSSL_INCL) $(LD_PROJ_INCL)
LD_JNI_INCL := $(LD_PROJ_INCL) -I$(OSSL_INCL) -I$(JNI_INCL) -I$(JNI_INCL)/linux

LD_ENCL_INCL := $(LD_PROJ_INCL) -I../../c/native -I../../c/sgx/talos/src/libressl-2.4.1/include -Ienclave

BUILD_PATH := ../../../../../build/

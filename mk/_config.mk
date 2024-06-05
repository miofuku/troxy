ifndef _CONFIG_MK_
_CONFIG_MK_ := 1

BLDDIR := build/trinx
GENDIR := $(BLDDIR)/gen
OBJDIR := $(BLDDIR)/obj
BINDIR := $(BLDDIR)/bin
LIBDIR := $(BLDDIR)/lib
SRCDIR := src

TARGET  :=

SRCS    :=
INCS    :=
DEPS    :=
LIBS    :=
LIBDIRS := $(LIBDIR)
DEFS    :=
PREREQS :=

CFLAGS   += -Wall -Wextra -g -Wno-unused-parameter -O3
CXXFLAGS += $(CFLAGS) -std=c++11

UNAME_S := $(shell uname -s)
ifeq ($(UNAME_S), Darwin)
    DSO := dylib
else
    DSO := so
endif

endif

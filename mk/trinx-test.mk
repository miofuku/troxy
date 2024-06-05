include mk/_config.mk

TARGET := trinx-test

SRCS := $(shell find $(SRCDIR)/reptor/test/cpp/trinx/ -name '*.cpp') $(SRCDIR)/distrbt/trinx/common/format.cpp
INCS := $(SRCDIR)/distrbt/trinx
LIBS := pthread


.PHONY: all build


all: build


include mk/_trinx-app.mk
RESULT := $(BINDIR)/$(TARGET)


build: $(RESULT)


include mk/_build.mk

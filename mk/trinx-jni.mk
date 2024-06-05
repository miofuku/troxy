include mk/_config.mk

TARGET := trinx-jni

JNICLS  := reptor.distrbt.certify.trusted.JniTrinxImplementation
JNIHEAD := $(GENDIR)/$(SRCDIR)/distrbt/trinx/jni/$(subst .,_,$(JNICLS)).h
PREREQS += $(JNIHEAD)

JCLSDIR := build/classes
JSRCS   := $(shell find $(SRCDIR) -name "*.java")
JSRCPAT := $(SRCDIR)/distrbt/distrbt/java:$(SRCDIR)/reptor/test/java
JCLSS   := build/classes/distrbt/distrbt/reptor/distrbt/certify/trusted/JniTrinxImplementation.class#$(patsubst $(SRCDIR)/%.java,$(JCLSDIR)/%.class,$(JSRCS))
JCLSPAT := $(JCLSDIR)/reptor/test/java:$(JCLSDIR)/distrbt/distrbt:$(JCLSDIR)/base/jlib:$(JCLSDIR)/exprmt/measr:$(JCLSDIR)/replct/replct:$(JCLSDIR)/base/chronos

JDKDIR  ?= /usr/lib/jvm/java-8-openjdk-amd64

SRCS := $(shell find $(SRCDIR)/distrbt/trinx/jni -name "*.cpp") $(SRCDIR)/distrbt/trinx/common/format.cpp
INCS := $(SRCDIR)/distrbt/trinx $(dir $(JNIHEAD)) $(JDKDIR)/include $(JDKDIR)/include/linux $(JDKDIR)/include/darwin

CFLAGS        += -fPIC
CXXFLAGS      += -fPIC
LDFLAGS_EXTRA += -shared -fPIC


.PHONY: all build


all: build


include mk/_trinx-app.mk
RESULT := $(LIBDIR)/lib$(TARGET).$(DSO)


build: $(RESULT) $(JCLSS)


$(JCLSDIR)/%.class: $(SRCDIR)/%.java
	@mkdir -p $(dir $@)
	@javac -d $(JCLSDIR)/$(word 1, $(subst /, ,$*))/$(word 2, $(subst /, ,$*)) -cp $(JCLSPAT) -sourcepath $(JSRCPAT) $^


$(JNIHEAD): $(JCLSDIR)/distrbt/distrbt/$(subst .,/,$(JNICLS)).class
	@javah -jni -d $(dir $@) -cp $(JCLSPAT) $(JNICLS)


include mk/_build.mk

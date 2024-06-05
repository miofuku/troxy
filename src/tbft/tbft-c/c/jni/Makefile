include ../common/consts.mk

JNICLS := reptor.tbftc.ctroxy.CTroxy

JHEADERS := $(addsuffix .h,$(subst .,_,$(JNICLS)))
HEADERS := $(JHEADERS)
HEADERS += ../common/HandshakeResult.h ../common/ClientResult.h ../common/debug.h

JNINOBJS := ctroxy_jni_native.o
JNIEOBJS := ctroxy_jni_enclave.o
JNINLIB := libtbftc-native.so
JNIELIB := libtbftc-enclave.so

NATIVELIB := ../native/*.a
SGXLIB    := ../sgx/*.a
COMMONLIB := ../common/*.a

CLSPRX := $(BUILD_PATH)classes/
CLSBUILD := $(CLSPRX)tbft/tbft-c/
CLASSES := $(addprefix $(CLSBUILD),$(addsuffix .class,$(subst .,/,$(JNICLS))))
CLSPATH := $(CLSPRX)tbft/tbft-c:$(CLSPRX)replct/replct:$(CLSPRX)distrbt/distrbt:$(CLSPRX)tbft/tbft:$(CLSPRX)base/chronos

all: $(JNINLIB) $(JNIELIB)

$(JHEADERS): $(CLASSES)
	@javah -jni -cp $(CLSPATH) $(JNICLS)
	@echo "JAVH =>  $@"

%.o: %.cpp $(HEADERS)
	@$(CXX) $(CXXFLAGS) $(LD_JNI_INCL) -c $< -o $@
	@echo "CXX  <=  $<"

ctroxy_jni_native.o: ctroxy_jni.cpp $(HEADERS)
	@$(CXX) $(CXXFLAGS) $(LD_JNI_INCL) -c $< -o $@
	@echo "CXX  <=  $<"

ctroxy_jni_enclave.o: ctroxy_jni.cpp $(HEADERS)
	@$(CXX) $(CXXFLAGS) $(LD_JNI_INCL) -c $< -o $@ -DSGX
	@echo "CXX  <=  $<"

$(JNINLIB): $(JNINOBJS) $(NATIVELIB) $(COMMONLIB)
	@$(CXX) $(CXXFLAGS) -shared $(JNINOBJS) $(NATIVELIB) $(COMMONLIB) -o $@ $(JNINATIVELINKFLAGS)
	@echo "LINK =>  $@"

$(JNIELIB): $(JNIEOBJS) $(SGXLIB) $(COMMONLIB)
	@$(CXX) $(CXXFLAGS) -shared $(JNIEOBJS) $(SGXLIB) $(COMMONLIB) -o $@ $(JNINATIVELINKFLAGS) -l$(SGXURTSLIB)
	@echo "LINK =>  $@"

clean:
	@rm -f $(JHEADERS) $(JNINOBJS) $(JNIEOBJS) $(JNINLIB) $(JNIELIB)
	@echo "RM   =>  $(JHEADERS) $(JNINOBJS) $(JNIEOBJS) $(JNINLIB) $(JNIELIB)"

include ../common/consts.mk

NATIVE_OBJS := native_impl.o cache.o rwlock.o
NATIVE_LIB := native_impl.a

HEADERS := ../common/BIO.h ../common/Buffer.h ../common/debug.h protocolparser.h defaultprotocolparser.h httpprotocolparser.h cache.h

.PHONY: all
all: $(NATIVE_LIB)

%.o: %.cpp $(HEADERS)
	@$(CXX) $(CXXFLAGS) $(LD_NATIVE_INCL) -c $< -o $@
	@echo "CXX  <=  $<"

$(NATIVE_LIB): $(NATIVE_OBJS)
	@ar cr $@ $(NATIVE_OBJS)
	@echo "AR   =>  $@"

clean:
	@rm -f $(NATIVE_OBJS) $(NATIVE_LIB)
	@echo "RM   => $(NATIVE_OBJS) $(NATIVE_LIB)"

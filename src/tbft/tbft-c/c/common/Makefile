include consts.mk

COMMON_OBJS := quorum.o
COMMON_LIB := common.a

HEADERS := Buffer.h slots.h votes.h debug.h

.PHONY: all
all: $(COMMON_LIB)

%.o: %.cpp $(HEADERS)
	@$(CXX) $(CXXFLAGS) $(LD_COMMON_INCL) -c $< -o $@
	@echo "CXX  <=  $<"

$(COMMON_LIB): $(COMMON_OBJS)
	@ar cr $@ $(COMMON_OBJS)
	@echo "AR   =>  $@"

clean:
	@rm -f $(COMMON_OBJS) $(COMMON_LIB)
	@echo "RM   => $(COMMON_OBJS) $(COMMON_LIB)"

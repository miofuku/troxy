OBJS += $(SRCS:%.cpp=$(OBJDIR)/$(TARGET)/%.o)

CXXFLAGS += $(addprefix -I,$(INCS)) $(addprefix -D,$(DEFS))
LDFLAGS  ?= $(addprefix -L,$(LIBDIRS)) $(addprefix -l,$(LIBS)) $(LDFLAGS_EXTRA)
 

.PHONY: clean


$(RESULT): $(OBJS) $(DEPS) | $(PREREQS)
	@mkdir -p $(dir $@)
	@echo "CREATE $@"
	@$(CXX) $^ -o $@ $(LDFLAGS)


$(STATLIB): $(OBJS) $(DEPS) | $(PREREQS)
	@mkdir -p $(dir $@)
	@echo "CREATE $@"
	@ar rcs $@ $^


$(OBJDIR)/$(TARGET)/%.o: %.cpp $(DEPS) | $(PREREQS)
	@mkdir -p $(dir $@)
	@$(CXX) $(CXXFLAGS) -c $< -o $@


print-%:
	@echo "$* = '$($*)'"


clean:
	rm -r $(BLDDIR)/*

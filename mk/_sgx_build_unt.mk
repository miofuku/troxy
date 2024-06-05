include mk/_sgx_build.mk

SGX_GEN_UNT_OBJ := $(OBJDIR)/$(TARGET)/$(SGX_GEN_UNT:%.c=%.o)

SRCS += $(SGX_UNT)
OBJS += $(SGX_GEN_UNT_OBJ)

SGX_GEN_UNT_FLAGS := $(CFLAGS) $(addprefix -I,$(INCS)) $(addprefix -D,$(DEFS))


$(SGX_GEN_UNT_OBJ): $(SGX_GEN_UNT) | $(PREREQS)
	@mkdir -p $(dir $@)
	@$(CC) $(SGX_GEN_UNT_FLAGS) -c $< -o $@

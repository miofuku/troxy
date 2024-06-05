SGX_GENDIR  ?= $(GENDIR)/$(dir $(SGX_EDL))

SGX_EDLNAM  := $(basename $(notdir $(SGX_EDL)))
SGX_GEN_ENC := $(SGX_GENDIR)$(SGX_EDLNAM)_t.c
SGX_GEN_UNT := $(SGX_GENDIR)$(SGX_EDLNAM)_u.c
SGX_GENS    := $(SGX_GEN_ENC) $(SGX_GEN_UNT) $(addprefix $(SGX_GENDIR)$(SGX_EDLNAM),_u.h _t.h)

INCS    += $(SGX_GENDIR) $(SGX_INCDIR)
PREREQS += $(SGX_GENS)


$(SGX_GENS): $(SGX_EDL)
	@mkdir -p $(SGX_GENDIR)
	@$(SGX_EDGER8R) $< --untrusted-dir $(SGX_GENDIR) --trusted-dir $(SGX_GENDIR)

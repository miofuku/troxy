SRC_DIR       := src
DEST_DIR      := build/classes
BUILD_LIB_DIR := build/libs
SMART_DIR     := libs/bftsmarts
DEPS_DIR      := build/deps
PIP_DEPS      := pyreqs.txt
DOCS_DIR      := docs

EXT_PROJS  := ${SMART_DIR}/.git

SOURCE_FILES := `find ${SRC_DIR} -name "*.java"`

SRCSETS      := base/jlib base/chronos exprmt/measr distrbt/distrbt replct/replct tbft/tbft tbft/tbft-c replct/bench reptor/start reptor/test #replct/smart
SRCSET_DESTS := $(addprefix $(DEST_DIR)/,$(SRCSETS))
#PROJ_DESTS   := $(SMART_DIR) $(SRCSET_DESTS)
PROJ_DESTS   := $(SRCSET_DESTS)

SPACE :=
SPACE +=

CLASSPATH := .:$(subst $(SPACE),:,$(SRCSET_DESTS)):$(DEPS_DIR)/*:$(SMART_DIR)/build/classes:$(SMART_DIR)/lib/*


.PHONY: all build gendocs init clean cleanall trinx trinx-clean $(PROJ_DESTS)


all: build


build: $(PROJ_DESTS)


$(SMART_DIR):
	make -C "$(SMART_DIR)"


$(SRCSET_DESTS):
	mkdir -p "$@"
	javac -d "$@" -cp "$(CLASSPATH)" `find "$(patsubst $(DEST_DIR)/%,$(SRC_DIR)/%,$@)" -name "*.java"`


init: ${EXT_PROJS} ${DEPS_DIR}
	pip3 install --target "${BUILD_LIB_DIR}" -r "${PIP_DEPS}"


${DEPS_DIR}:
	@echo "Store external libraries und ${DEPS_DIR}. This could be done via 'gradle initDeps'."


${EXT_PROJS}:
	git submodule init $(dir $@)
	git submodule update $(dir $@)
	make -C $(dir $@) init

trinx:
	make -f mk/trinx.mk

trinx-clean:
	make -f mk/trinx.mk clean

gendocs:
	sphinx-apidoc -f -o "${DOCS_DIR}/src" src/base/plib/python src/exprmt/exprmt/python src/reptor/start/python
	scripts/start.py make -C "${DOCS_DIR}" html


clean:
	rm -r ${DEST_DIR}


cleanall: clean trinx-clean
	make -C ${SMART_DIR} cleanall

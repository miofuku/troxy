SRC_DIR  := src
DEST_DIR := build/classes

SOURCE_FILES := `find ${SRC_DIR} -name "*.java"`


.PHONY: all init build clean cleanall


all: build


${DEST_DIR}:
	mkdir -p $@


init:


build: ${DEST_DIR}
	javac -d ${DEST_DIR} -cp .:lib/*:${DEST_DIR} ${SOURCE_FILES}


clean:
	rm -r ${DEST_DIR}


cleanall: clean

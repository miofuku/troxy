/**
 * @author weichbr
 */

#ifndef TBFT_C_DEBUG_H
#define TBFT_C_DEBUG_H

#include <stdint.h>
#include <stdio.h>

#define DEBUG 0

#if DEBUG
#define debug(msg, ...) \
do { \
	printf(msg, ##__VA_ARGS__); \
	fflush(stdout); \
} while(false);
#define debugE(msg, ...) \
do { \
	printf("==> ENTER %s " msg "\n", __FUNCTION__, ##__VA_ARGS__); \
	fflush(stdout); \
} while(false);
#define debugL(msg, ...) \
do { \
	printf("<== LEAVE %s " msg "\n", __FUNCTION__, ##__VA_ARGS__); \
	fflush(stdout); \
} while(false);


#define hexdump(data, length) \
do { \
	void *addr = (void *)(data); \
	size_t len = (size_t)(length); \
	if (!addr) \
	{ \
		break; \
	} \
	int i; \
	unsigned char buff[17]; \
	unsigned char *pc = (unsigned char*)addr; \
	if (len == 0) { \
		printf("  ZERO LENGTH\n"); \
		break; \
	} \
	for (i = 0; (size_t)i < len; i++) { \
		if ((i % 16) == 0) { \
			if (i != 0) \
				printf ("  %s\n", buff);\
			printf ("  %04x ", i); \
		}\
		printf (" %02x", pc[i]); \
		if ((pc[i] < 0x20) || (pc[i] > 0x7e)) \
			buff[i % 16] = '.'; \
		else\
			buff[i % 16] = pc[i];\
		buff[(i % 16) + 1] = '\0';\
	}\
	while ((i % 16) != 0) {\
		printf ("   ");\
		i++;\
	}\
	printf ("  %s\n", buff);\
} while(false);

#else
#define debug(msg, ...) \
do { \
} while(false);
#define debugE(msg, ...) \
do { \
} while(false);
#define debugL(msg, ...) \
do { \
} while(false);

#define hexdump(...) \
do { \
} while(false);

#endif

#endif //TBFT_C_DEBUG_H

/**
 * @author weichbr
 */

#ifndef TBFT_C_DUMMIES_H_H
#define TBFT_C_DUMMIES_H_H

#include <stdint.h>

struct FILE {};

#define stdout 0

extern "C" int printf(const char *f, ...);

extern "C" int fflush(FILE *f);


#endif //TBFT_C_DUMMIES_H_H

/**
 * @author weichbr
 */

#ifndef TBFT_C_TIME_H
#define TBFT_C_TIME_H

#ifndef __timeval_defined
#define __timeval_defined 1

#ifdef __cplusplus
extern "C" {
#endif

#define time_t long int
#define suseconds_t long int

struct timeval
{
	time_t tv_sec;
	suseconds_t tv_usec;
};

#ifdef __cplusplus
};
#endif

#endif // timeval

#ifdef __cplusplus
extern "C" {
#endif

struct timezone
{
	int tz_minuteswest;		/* Minutes west of GMT.  */
	int tz_dsttime;		/* Nonzero if DST is ever in effect.  */
};

#ifdef __cplusplus
};
#endif

#endif //TBFT_C_TIME_H

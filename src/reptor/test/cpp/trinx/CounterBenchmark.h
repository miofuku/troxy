#ifndef _TEST_TRINX_COUNTERBENCHMARK_H_
#define _TEST_TRINX_COUNTERBENCHMARK_H_

#include <algorithm>
#include <limits>
#include <chrono>
#include <vector>
#include <mutex>
#include <condition_variable>

#include "Trinx.h"


template <class C, class V> class summary
{
    C m_cnt;
    V m_sum;
    V m_min;
    V m_max;

public:

    summary()
    {
        m_cnt = 0;
        m_sum = 0;
        m_min = std::numeric_limits<V>::max();
        m_max = std::numeric_limits<V>::min();
    }

    summary<C, V>& operator+=(V value)
    {
        add( value );
        return *this;
    }

    summary<C, V>& operator+=(summary<C, V> sum)
    {
        add( sum );
        return *this;
    }

    void add(V value)
    {
        m_cnt++;
        m_sum += value;
        m_min  = std::min( m_min, value );
        m_max  = std::max( m_max, value );
    }

    void add(summary<C, V> sum)
    {
        add( sum.m_cnt, sum.m_sum, sum.m_min, sum.m_max );
    }

    void add(C cnt, V sum, V min, V max)
    {
        m_cnt += cnt;
        m_sum += sum;
        m_min  = std::min( m_min, min );
        m_max  = std::max( m_max, max );
    }

    C count() const
    {
        return m_cnt;
    }

    V sum() const
    {
        return m_sum;
    }

    V min() const
    {
        return m_min;
    }

    V max() const
    {
        return m_max;
    }

    V avg() const
    {
        return m_sum / m_cnt;
    }

    double avg_double() const
    {
        return m_sum / (double) m_cnt;
    }
};


class CounterBenchmark
{

public:

    typedef summary<uint32_t, std::chrono::high_resolution_clock::duration::rep> intsum;

private:

    size_t m_nints         = 10;
    size_t m_intlen        = 1000000;
    bool   m_threadinit    = true;
    bool   m_measuresingle = false;
    bool   m_printints     = false;
    bool   m_touchonly     = false;
    size_t m_msgsize       = 32;

    std::vector<Trinx *>             m_tms;
    std::vector<std::vector<intsum>> m_ints;
    std::vector<int>                 m_affs;

    int                     m_startcnt;
    std::mutex              m_startmtx;
    std::condition_variable m_startsig;

    void ClearCounters();

public:

    ~CounterBenchmark();


    void InitCounters(tssid_t ntms);


    tssid_t GetNumberOfCounters() const
    {
        return m_tms.size();
    }


    void InitIntervals(size_t nints);


    size_t GetNumberOfIntervals() const
    {
        return m_nints;
    }


    std::vector<std::vector<intsum>> &GetIntervals()
    {
        return m_ints;
    }


    void SetIntervalLength(size_t intlen)
    {
        m_intlen = intlen;
    }

    void SetMeasureSingleCalls(bool msc)
    {
        m_measuresingle = msc;
    }

    void SetPrintIntervals(bool pi)
    {
        m_printints = pi;
    }

    void SetTouchOnly(bool to)
    {
        m_touchonly = to;
    }

    void SetMessageSize(size_t msgsize)
    {
        m_msgsize = msgsize;
    }

    void SetCpuAffinity(tssid_t tmid, int cpuid)
    {
        m_affs[ tmid ] = cpuid;
    }

    void SetThreadLocalInit(bool ti)
    {
        m_threadinit = ti;

        InitCounters( m_tms.size() );
    }

    int GetCpuAffinity(tssid_t tmid)
    {
        return m_affs[ tmid ];
    }


    void Run();


    void RunForCounter(tssid_t tmid);

};


#endif /* _TEST_TRINX_COUNTERBENCHMARK_H_ */

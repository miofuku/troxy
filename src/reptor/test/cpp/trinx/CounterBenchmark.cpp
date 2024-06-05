#include <cstring>
#include <iostream>
#include <vector>
#include <thread>
#include <iomanip>

#ifdef __linux
    #include <sched.h>
#endif

#include "CounterBenchmark.h"
#include "counter/trinx_cmds.h"


CounterBenchmark::~CounterBenchmark()
{
    ClearCounters();
}


void CounterBenchmark::ClearCounters()
{
    for( Trinx *tm : m_tms )
        delete tm;

    m_tms.clear();
}


void CounterBenchmark::InitCounters(tssid_t ntms)
{
    ClearCounters();
    m_tms.resize( ntms );

    if( !m_threadinit )
        for( tssid_t i=0; i<ntms; i++ )
            m_tms[ i ] = new Trinx( i, 1, KEY, sizeof( KEY ) );

    m_affs.clear();
    m_affs.resize( ntms, -1 );

    InitIntervals( m_nints );
}


void CounterBenchmark::InitIntervals(size_t nints)
{
    m_ints.clear();
    m_ints.resize( m_tms.size() );

    for( std::vector<intsum> &tmints : m_ints )
        tmints.resize( nints );

    m_nints = nints;
}


void CounterBenchmark::Run()
{
    m_startcnt = m_tms.size();

    std::vector<std::thread> benchthreads( m_tms.size() );

    for( tssid_t i=0; i<m_tms.size(); i++ )
        benchthreads[ i ] = std::thread( &CounterBenchmark::RunForCounter, this, i );

    for( auto &t : benchthreads )
        t.join();
}


void CounterBenchmark::RunForCounter(tssid_t tmid)
{
    #ifdef __linux
        int cpuid = m_affs[ tmid ];

        if( cpuid>=0 )
        {
            cpu_set_t cpuset;
            CPU_ZERO( &cpuset );
            CPU_SET( cpuid, &cpuset );

            int sysret;
            if( (sysret = pthread_setaffinity_np( pthread_self(), sizeof( cpuset ), &cpuset )) )
                throw std::runtime_error( std::to_string( sysret ) );
        }
    #endif

    const double timefac = 1000000000.0;
    std::cout << std::fixed << std::setprecision( 0 );

    uint8_t msg[ m_msgsize ]; std::memset( msg, 0, m_msgsize );
    uint8_t certbuf[ COUNTER_CERTIFICATE_SIZE ] = { 0 };

    if( m_threadinit )
        m_tms[ tmid ] = new Trinx( tmid, 1, KEY, sizeof( KEY ) );

    Trinx &tm = *m_tms[ tmid ];

    std::unique_lock<std::mutex> lock( m_startmtx );
    if( --m_startcnt )
    {
        m_startsig.wait( lock, [&] { return m_startcnt==0; } );
        lock.unlock();
    }
    else
    {
        lock.unlock();
        m_startsig.notify_all();
    }

    uint64_t ctrval = 1;
    certification_command<create_independent_counter_body> tcmd;
    tcmd.createIndependent().counter( tmid, 0 ).value( 0, 0 );

    for( auto &cint : m_ints[ tmid ] )
    {
        if( m_measuresingle )
        {
            for( size_t c=0; c<m_intlen; c++ )
            {
                auto cs = std::chrono::high_resolution_clock::now();
                if( m_touchonly )
                    tm.Touch();
                else
                    tm.ExecuteCommand( &tcmd.value( ctrval++ ).message( msg, sizeof( msg ), certbuf ) );

                auto ce = std::chrono::high_resolution_clock::now();

                cint += ( ce-cs ).count();
            }

            if( m_printints )
            {
                double avg = cint.avg_double();

                std::cout << std::setw( 2 ) << tmid
                          << " (in ns) " << std::setw( 9 ) << avg
                          << " (" << std::setw( 9 ) << cint.min() << " - " << std::setw( 9 ) << cint.max() << ")"
                          << " -> ops/sec " << std::setw( 7 ) << ( timefac/avg )
                          << std::endl;
            }
        }
        else
        {
            auto is = std::chrono::high_resolution_clock::now();

            for( size_t i=0; i<m_intlen; i++ )
            {
                if( m_touchonly )
                    tm.Touch();
                else
                {
                    tm.ExecuteCommand( &tcmd.value( ctrval++ ).message( msg, sizeof( msg ), certbuf ) );
                }

            }

            auto ie = std::chrono::high_resolution_clock::now();

            cint.add( m_intlen, ( ie-is ).count(), 0, 0 );

            if( m_printints )
            {
                double avg = cint.avg_double();

                std::cout << std::setw( 2 ) << tmid
                          << " (in ns) " << std::setw( 9 ) << avg
                          << " -> ops/sec " << std::setw( 7 ) << ( timefac/avg ) << std::endl;
            }
        }
    }
}

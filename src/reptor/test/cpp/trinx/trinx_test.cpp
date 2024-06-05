#include <iostream>
#include <string>
#include <sstream>
#include <fstream>
#include <vector>
#include <ratio>
#include <iomanip>
#include <unistd.h>

#include "Trinx.h"
#include "CounterBenchmark.h"


typedef std::ratio<3, 10> WARMUP_RATIO;
typedef std::ratio<3, 10> COOLDOWN_RATIO;

enum CpuAffinityPattern
{
    None,
    Progressive,
    Interlaced2,
    InterlacedC,
    ProgressiveN2,
    InterlacedCN2
};


extern void check_counter_implementation();


void write_summary(std::ostream &out, const std::string &aff, int tmid, int intno,
                   const CounterBenchmark::intsum &sum, double opsfac)
{
    out << aff << ";";

    if( tmid>=0 )
        out << tmid;
    out << ";";

    if( intno>=0 )
        out << intno;
    out << ";";

    double avg = sum.avg_double();

    out << sum.count() << ";" << sum.sum() << ";" << sum.min() << ";" << sum.max() << ";"
        << avg << ";" << ( opsfac/avg ) << std::endl;
}


void write_file_and_cout(std::ofstream &fout, const std::string &aff, int tmid, int intno,
                         const CounterBenchmark::intsum &sum, double opsfac)
{
    if( fout.is_open() )
        write_summary( fout, aff, tmid, intno, sum, opsfac );

    write_summary( std::cout, aff, tmid, intno, sum, opsfac );
}


void write_results(CounterBenchmark &bench, const char* outfile)
{
    std::ofstream fout;

    std::cout << std::fixed << std::setprecision( 0 );

    if( outfile )
    {
        fout.exceptions( std::ofstream::failbit | std::ofstream::badbit );
        fout.open( outfile );
        fout << std::fixed << std::setprecision( 0 );
        fout << "aff;thread;intno;cnt;sum;min;max;avg;ops_per_sec" << std::endl;
    }

    CounterBenchmark::intsum total;
    std::vector<CounterBenchmark::intsum> tmtotals( bench.GetNumberOfCounters() );

    size_t warmup   = bench.GetNumberOfIntervals() * WARMUP_RATIO::num / WARMUP_RATIO::den;
    size_t cooldown = bench.GetNumberOfIntervals() -
                      bench.GetNumberOfIntervals() * COOLDOWN_RATIO::num / COOLDOWN_RATIO::den;

    const double timefac = 1000000000.0;

    tssid_t tmid = 0;
    for( auto &ints : bench.GetIntervals() )
    {
        std::string aff = bench.GetCpuAffinity( tmid )>=0 ?
                std::to_string( bench.GetCpuAffinity( tmid ) ).c_str() : "";

        CounterBenchmark::intsum &tmtotal = tmtotals[ tmid ];

        size_t intno = 0;
        for( auto &tmint : ints )
        {
            write_file_and_cout( fout, aff, tmid, intno, tmint, timefac );

            if( intno>=warmup && intno<cooldown )
                tmtotal += tmint;
            intno++;
        }

        total += tmtotal;
        tmid++;
    }

    std::ostringstream afflist;
    tmid = 0;
    for( auto &tmtotal : tmtotals )
    {
        if( tmid )
            afflist << " ";

        std::string aff;

        if( bench.GetCpuAffinity( tmid )>=0 )
        {
            afflist << bench.GetCpuAffinity( tmid );
            aff = std::to_string( bench.GetCpuAffinity( tmid ) );
        }
        else
        {
            afflist << "-";
            aff = "";
        }

        write_file_and_cout( fout, aff, tmid, -1, tmtotal, timefac );
        tmid++;
    }

    write_file_and_cout( fout, afflist.str(), -1, -1, total, timefac*bench.GetNumberOfCounters() );

    fout.close();
}


int main(int argc, char *argv[])
{
    int curargidx = 1;

    #ifdef SGX
        if( curargidx>=argc )
        {
            std::cerr << "Enclave path expected!" << std::endl;
            exit( 1 );
        }

        Trinx::Init( argv[ curargidx++ ] );
    #endif


    char *             outfile = NULL;
    bool               check   = true;
    CpuAffinityPattern affpat  = None;
    int                affoff  = 4;
    int                ntms    = 1;

    CounterBenchmark bench;

    while( curargidx<argc )
    {
        char *curarg = argv[ curargidx++ ];

        if( std::string( "-s" )==curarg )
            bench.SetMeasureSingleCalls( true );
        else if( std::string( "-p" )==curarg )
            bench.SetPrintIntervals( true );
        else if( std::string( "-t" )==curarg )
            bench.SetTouchOnly( true );
        else if( std::string( "-n" )==curarg )
            check = false;
        else if( std::string( "-L" )==curarg )
            bench.SetThreadLocalInit( false );
        else if( curargidx==argc )
            throw std::runtime_error( "Value expected!" );
        else if( std::string( "--certmode" )==curarg )
            curargidx++;
        else if( std::string( "--nthreads" )==curarg )
            ntms = std::stoi( argv[ curargidx++ ] );
        else if( std::string( "--msgsize" )==curarg )
            bench.SetMessageSize( std::stoi( argv[ curargidx++ ] ) );
        else if( std::string( "--nints" )==curarg )
            bench.InitIntervals( std::stoi( argv[ curargidx++ ] ) );
        else if( std::string( "--intlen" )==curarg )
            bench.SetIntervalLength( std::stoi( argv[ curargidx++ ] ) );
        else if( std::string( "--affpat" )==curarg )
            affpat = (CpuAffinityPattern) std::stoi( argv[ curargidx++ ] );
        else if( std::string( "--affoff" )==curarg )
            affoff = std::stoi( argv[ curargidx++ ] );
        else if( std::string( "--outfile" )==curarg )
            outfile = argv[ curargidx++ ];
        else
            throw std::runtime_error( "Unexpected argument! " );
    }

    if( check )
    {
        std::cout << "Check implementation:" << std::endl;

        check_counter_implementation();

        std::cout << std::endl;
    }

    // The CASH driver only supports one open connection at a time.
    // Therefore, we cannot open the device file for the benchmarks
    // before we check the implementation, which requires the device as well.
    bench.InitCounters( ntms );

    if( affpat!=None )
    {
        int ncpus  = sysconf( _SC_NPROCESSORS_ONLN );
        int ncores = ncpus/2;

        for( tssid_t tmid=0; tmid<bench.GetNumberOfCounters(); tmid++ )
        {
            int cpuid;
            int x = tmid+affoff;

            if( affpat==Progressive )
                cpuid = x % ncpus; // ( tmid % ncpus )*1 + ( tmid/ncpus ) % 1
            else if( affpat==Interlaced2 )
                cpuid = ( x % ncores )*2 + ( x/ncores ) % 2;
            else if( affpat==InterlacedC )
                cpuid = ( x % 2 )*ncores + ( x/2 ) % ncores;
            else if( affpat==ProgressiveN2 )
                cpuid = ( x % (ncores/2) )*2 + ( x/(ncores/2) ) % 2 + ( ( x/ncores ) % 2 )*ncores;
            else
                cpuid = ( x % 2 )*ncores + ( ( x/2 )*2 ) % ncores + ( x/ncores ) % 2;

            bench.SetCpuAffinity( tmid, cpuid );
        }
    }

    std::cout << "Benchmark:" << std::endl;

    bench.Run();

    std::cout << std::endl;
    std::cout << "Results:" << std::endl;

    write_results( bench, outfile );

    #ifdef SGX
        Trinx::CleanUp();
    #endif
}

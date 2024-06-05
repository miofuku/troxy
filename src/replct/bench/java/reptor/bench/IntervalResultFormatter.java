package reptor.bench;

import org.apache.commons.math3.stat.descriptive.StatisticalSummary;
import org.apache.commons.math3.stat.descriptive.StatisticalSummaryValues;

import reptor.jlib.strings.StringBuilderExtender;
import reptor.measr.meter.IntervalHistorySummary;
import reptor.measr.meter.IntervalResult;
import reptor.measr.sink.LongStatisticalSummary;
import reptor.measr.sink.LongStatsSink;

public abstract class IntervalResultFormatter<S>
{
    public static void printInterval(IntervalResult<?> meter, int intno, long dur, LongStatsSink intsink)
    {
        StringBuilderExtender sb = new StringBuilderExtender();
        sb.format( "%4d ", intno );
        appendIntervalSummary( sb, intsink, dur );
        System.out.println( sb.toString() );
    }


    public static IntervalResultFormatter<LongStatsSink> createLongStatsFormatter()
    {
        return new IntervalResultFormatter<LongStatsSink>()
        {
            @Override
            protected StringBuilderExtender
                    appendIntervalSummary(StringBuilderExtender sb, IntervalResult<LongStatsSink> sum)
            {
                return IntervalResultFormatter.appendSimpleIntervalSummary( sb, sum );
            }
        };
    }


    public static <T extends StatisticalSummary> IntervalResultFormatter<T> createSumStatsFormatter()
    {
        return new IntervalResultFormatter<T>()
        {
            @Override
            protected StringBuilderExtender
                    appendIntervalSummary(StringBuilderExtender sb, IntervalResult<T> sum)
            {
                return IntervalResultFormatter.appendSumStatsIntervalSummary( sb, sum );
            }
        };
    }


    public static IntervalResultFormatter<StatisticalSummaryValues> createSumStats2Formatter()
    {
        return new IntervalResultFormatter<StatisticalSummaryValues>()
        {
            @Override
            protected StringBuilderExtender
                    appendIntervalSummary(StringBuilderExtender sb, IntervalResult<StatisticalSummaryValues> sum)
            {
                return IntervalResultFormatter.appendSumStatsIntervalSummary( sb, sum );
            }
        };
    }


    public void printResults(IntervalHistorySummary<S, S> result)
    {
        StringBuilderExtender sb = new StringBuilderExtender();
        sb.appendln( "Results:" );

        for( int i = 0; i < result.getNumberOfAvailInts(); i++ )
        {
            sb.format( "%4d ", i );
            appendIntervalSummary( sb, result.getInterval( i ) );
        }

        IntervalResult<S> sum = result.getSummary();
        if( sum != null )
        {
            sb.format( "   = " );
            appendIntervalSummary( sb, sum );
        }

        System.out.print( sb.toString() );
    }


    protected abstract StringBuilderExtender
            appendIntervalSummary(StringBuilderExtender sb, IntervalResult<S> sum);


    protected static StringBuilderExtender
            appendSimpleIntervalSummary(StringBuilderExtender sb, IntervalResult<LongStatsSink> sum)
    {
        return appendIntervalSummary( sb, sum.getValueSummary(), sum.getElapsedTime() )
                .appendln( " dur " + sum.getElapsedTime() );
    }


    protected static StringBuilderExtender
            appendSumStatsIntervalSummary(StringBuilderExtender sb, IntervalResult<? extends StatisticalSummary> sum)
    {
        StatisticalSummary valsum = sum.getValueSummary();

        return sb.formatln( "%6d rps (avg %8.2f min %6d max %6d stddev %8.2f) dur %d",
                valsum.getN() * 1_000_000_000L / sum.getElapsedTime(),
                valsum.getMean(), (long) valsum.getMin(), (long) valsum.getMax(),
                valsum.getStandardDeviation(), sum.getElapsedTime() );
    }


    protected static StringBuilderExtender
            appendIntervalSummary(StringBuilderExtender sb, LongStatisticalSummary sum, long dur)
    {
        return sb.format( "%6d rps (avg %6d min %6d max %6d)",
                sum.getCount() * 1_000_000_000L / dur, sum.getMean(), sum.getMin(), sum.getMax() );
    }
}

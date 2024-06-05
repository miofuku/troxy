package reptor.measr.meter;

import java.io.PrintStream;

import reptor.jlib.strings.StringBuilderExtender;
import reptor.measr.sink.LongStatsSink;


public class LongStatsIntervalLogger extends LongIntervalMeter<LongStatsSink>
{
    private final String      m_format;
    private final PrintStream m_out;


    public LongStatsIntervalLogger(String format)
    {
        this( format, null );
    }


    public LongStatsIntervalLogger()
    {
        this( null, null );
    }


    public LongStatsIntervalLogger(String format, PrintStream out)
    {
        super( new LongStatsSink() );

        m_format = format == null ? "%1$5d cnt %3$6d sum %4$6d avg %5$6d min %6$6d max %7$6d dur %2$12d" : format;
        m_out = out == null ? System.out : out;
    }


    @Override
    protected void onIntervalEnded(int intno, long dur, LongStatsSink sink)
    {
        StringBuilderExtender sb = new StringBuilderExtender();
        sb.format( m_format, intno, dur, sink.getCount(), sink.getSum(), sink.getMean(), sink.getMin(), sink.getMax() );
        m_out.println( sb.toString() );
    }
}

package reptor.bench.measure;

import java.io.PrintWriter;
import java.util.Objects;

import org.apache.commons.math3.stat.descriptive.StatisticalSummary;

import reptor.measr.meter.IntervalResult;
import reptor.measr.sink.LongStatsSink;


public class CSVResultsWriter
{
    private final PrintWriter m_writer;

    private boolean           isnewline = true;


    public CSVResultsWriter(PrintWriter writer)
    {
        m_writer = Objects.requireNonNull( writer, "writer" );
    }


    public void newField()
    {
        if( !isnewline )
            m_writer.write( ";" );
        else
            isnewline = false;
    }


    public CSVResultsWriter newLine()
    {
        m_writer.println();
        isnewline = true;

        return this;
    }


    public CSVResultsWriter writeField(String value)
    {
        newField();
        m_writer.write( value );

        return this;
    }


    public CSVResultsWriter writeLongStatsIntervalHeader(String prefix)
    {
        for( String f : new String[] { "evt_per_sec", "time", "cnt", "sum", "min", "max", "mean" } )
            writeField( prefix + f );

        return this;
    }


    public CSVResultsWriter writeSumStatsIntervalHeader(String prefix)
    {
        writeLongStatsIntervalHeader( prefix );
        writeField( prefix + "approx_stddev" );

        return this;
    }


    public CSVResultsWriter writeLongStatsIntervalSummary(IntervalResult<LongStatsSink> sum)
    {
        newField();

        LongStatsSink valsum = sum.getValueSummary();
        m_writer.format( "%d;%d;%d;%d;%d;%d;%d",
                sum.getElapsedTime() != 0 ? valsum.getCount() * 1_000_000_000L / sum.getElapsedTime() : 0,
                sum.getElapsedTime(),
                valsum.getCount(),
                valsum.getSum(),
                valsum.getMin(),
                valsum.getMax(),
                valsum.getMean()
                );

        return this;
    }


    public CSVResultsWriter writeSumStatsIntervalSummary(IntervalResult<? extends StatisticalSummary> sum)
    {
        newField();

        StatisticalSummary valsum = sum.getValueSummary();
        m_writer.format( "%d;%d;%d;%d;%d;%d;%d;%.2f",
                sum.getElapsedTime() != 0 ? valsum.getN() * 1_000_000_000L / sum.getElapsedTime() : 0,
                sum.getElapsedTime(),
                valsum.getN(),
                Math.round( valsum.getSum() ),
                Math.round( valsum.getMin() ),
                Math.round( valsum.getMax() ),
                Math.round( valsum.getMean() ),
                valsum.getStandardDeviation()
                );

        return this;
    }

}

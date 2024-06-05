package reptor.bench.measure;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;

import org.apache.commons.math3.stat.descriptive.StatisticalSummary;

import reptor.measr.meter.IntervalHistorySummary;
import reptor.measr.meter.IntervalResult;
import reptor.measr.sink.LongStatsSink;


public abstract class CSVIntervalResultWriter<S>
{
    public static CSVIntervalResultWriter<LongStatsSink> createLongStatsWriter(PrintWriter writer)
    {
        return new CSVIntervalResultWriter<LongStatsSink>( writer )
        {
            @Override
            protected void saveIntervalHeader() throws IOException
            {
                m_writer.writeLongStatsIntervalHeader( "" );
            }


            @Override
            protected void saveIntervalSummary(IntervalResult<LongStatsSink> sum) throws IOException
            {
                m_writer.writeLongStatsIntervalSummary( sum );
            }
        };
    }


    public static <T extends StatisticalSummary> CSVIntervalResultWriter<T> createSumStatsWriter(PrintWriter writer)
    {
        return new CSVIntervalResultWriter<T>( writer )
        {
            @Override
            protected void saveIntervalHeader() throws IOException
            {
                m_writer.writeSumStatsIntervalHeader( "" );
            }


            @Override
            protected void saveIntervalSummary(IntervalResult<T> sum) throws IOException
            {
                m_writer.writeSumStatsIntervalSummary( sum );
            }
        };
    }


    protected final CSVResultsWriter m_writer;


    public CSVIntervalResultWriter(PrintWriter writer)
    {
        m_writer = new CSVResultsWriter( Objects.requireNonNull( writer, "writer" ) );
    }


    public void writeHeader() throws IOException
    {
        m_writer.writeField( "intno" );
        saveIntervalHeader();
        m_writer.newLine();
    }


    public void write(IntervalHistorySummary<S, S> result) throws IOException
    {
        IntervalResult<S> sum = result.getSummary();
        if( sum != null )
        {
            m_writer.newField();
            saveIntervalSummary( sum );
            m_writer.newLine();
        }

        for( int i = 0; i < result.getNumberOfAvailInts(); i++ )
        {
            m_writer.writeField( Integer.toString( i ) );
            saveIntervalSummary( result.getInterval( i ) );
            m_writer.newLine();
        }
    }


    protected abstract void saveIntervalHeader() throws IOException;


    protected abstract void saveIntervalSummary(IntervalResult<S> sum) throws IOException;
}

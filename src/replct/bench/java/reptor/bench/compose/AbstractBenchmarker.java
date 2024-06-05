package reptor.bench.compose;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.StatisticalSummaryValues;

import reptor.bench.IntervalResultFormatter;
import reptor.bench.MultiDomainMeasurement;
import reptor.bench.measure.CSVIntervalResultWriter;
import reptor.bench.measure.CSVResultsWriter;
import reptor.measr.IntervalObserver;
import reptor.measr.compose.AsyncMeasurementTask;
import reptor.measr.meter.IntervalHistorySummary;


public abstract class AbstractBenchmarker
{

    protected final List<Short>                   m_clients;
    // TODO: Request duration meters could be placed behind the domain meters.
    protected final MultiDomainMeasurement        m_meas;
    protected final long                          m_intdur;
    protected final int                           m_intcnt;

    protected IntervalObserver                    m_intobs;
    protected IntervalHistorySummary<StatisticalSummaryValues, StatisticalSummaryValues> m_result;


    public AbstractBenchmarker(List<Short> clients, int nthreads, long intdur, int intcnt, int delints, int recints)
    {
        m_clients = clients;
        m_intcnt   = intcnt;
        m_intdur   = intdur;
        m_meas     = new MultiDomainMeasurement( nthreads, delints, recints );
    }


    public void activate()
    {
        // Clients must have been created before meas.createIntervalObserver()
        m_intobs = m_meas.createIntervalObserver();
    }


    public abstract void awaitClientConnections() throws InterruptedException;


    public void startBenchmark()
    {
        // TODO: Trigger only by domains
        AsyncMeasurementTask task = new AsyncMeasurementTask( m_intdur, m_intcnt, m_intobs );

        task.run();

        m_result = m_meas.aggregatedResult();

        if( !task.isCancelled() )
        {
            IntervalResultFormatter<StatisticalSummaryValues> formatter = IntervalResultFormatter.createSumStatsFormatter();
            formatter.printResults( m_result );
        }
    }


    public void saveClientRequestDurations(Path respath) throws IOException
    {
        try( BufferedWriter bufwriter = Files.newBufferedWriter( respath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING );
                PrintWriter writer = new PrintWriter( bufwriter ) )
        {
            CSVResultsWriter csvwriter = new CSVResultsWriter( writer );

            csvwriter.writeField( "client_id" );
            csvwriter.writeSumStatsIntervalHeader( "" );
            csvwriter.newLine();

            for( short clino : m_clients )
            {
                csvwriter.writeField( Short.toString( clino ) );
                csvwriter.writeSumStatsIntervalSummary( m_meas.getObjectResult( clino ) );
                csvwriter.newLine();
            }
        }
    }


    public void saveTotalResults(Path respath) throws IOException
    {
        try( BufferedWriter bufwriter = Files.newBufferedWriter( respath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING );
                PrintWriter writer = new PrintWriter( bufwriter ) )
        {
            CSVIntervalResultWriter<StatisticalSummaryValues> csvwriter = CSVIntervalResultWriter.createSumStatsWriter( writer );

            csvwriter.writeHeader();
            csvwriter.write( m_result );
        }
    }

}

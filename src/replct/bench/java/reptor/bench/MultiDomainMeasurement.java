package reptor.bench;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

import org.apache.commons.math3.stat.descriptive.AggregateSummaryStatistics;
import org.apache.commons.math3.stat.descriptive.StatisticalSummary;
import org.apache.commons.math3.stat.descriptive.StatisticalSummaryValues;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import reptor.measr.IntervalObserver;
import reptor.measr.IntervalObserverList;
import reptor.measr.LongConsumerList;
import reptor.measr.LongIntervalValueSink;
import reptor.measr.compose.QuantityMeasurement;
import reptor.measr.filter.CountingIntervalFilter;
import reptor.measr.generate.LongIntervalValueSynchronizer;
import reptor.measr.meter.IntervalHistory;
import reptor.measr.meter.IntervalHistorySummary;
import reptor.measr.meter.IntervalResult;
import reptor.measr.meter.LongConcurrentFilteringMeter;
import reptor.measr.meter.LongIntervalMeter;
import reptor.measr.meter.LongIntervalMeter.Listener;
import reptor.measr.sink.LongStatsSink;
import reptor.measr.sink.SummaryStatsSink;


public class MultiDomainMeasurement
{

    private final int m_ndomains;
    private final int m_delints;
    private final int m_recints;

    private final List<IntervalHistorySummary<SummaryStatsSink, SummaryStatsSink>> m_domresults;
    private final List<LongIntervalValueSink>                                      m_domsinks;
    private final Map<Integer, LongConcurrentFilteringMeter<SummaryStatsSink>>     m_objsinks;

    private final LongStatsSink m_intsum  = new LongStatsSink();
    private int                 m_intno   = 0;
    private int                 m_nmeasrs = 0;


    public MultiDomainMeasurement(int ndomains, int delints, int recints)
    {
        m_ndomains   = ndomains;
        m_delints    = delints;
        m_recints    = recints;
        m_domresults = new ArrayList<>( ndomains );
        m_domsinks   = new ArrayList<>( ndomains );

        for( int i=0; i<ndomains; i++ )
        {
            LongIntervalMeter<LongStatsSink> intobs =
                    new LongIntervalMeter<>( new LongStatsSink(), (Listener<? super LongStatsSink>) this::intervalFinished );
            QuantityMeasurement<SummaryStatsSink, SummaryStatsSink> quant =
                    new QuantityMeasurement<>( delints, recints, intobs, SummaryStatsSink::new, SummaryStatsSink::new );

            m_domresults.add( quant );
            m_domsinks.add( new LongIntervalValueSynchronizer( quant ) );
        }

        m_objsinks = new HashMap<>();
    }


    public LongConsumer createMeasuringObject(int objid, int domno)
    {
        LongConcurrentFilteringMeter<SummaryStatsSink> objsnk =
                new LongConcurrentFilteringMeter<>( new SummaryStatsSink() );

        m_objsinks.put( objid, objsnk );

        return LongConsumerList.createIfNecessary( m_domsinks.get( domno ), objsnk );
    }


    public IntervalObserver createIntervalObserver()
    {
        // Clients measure all of their processed requests over the complete period of measurement.
        // -> They get only the start and end of the recording interval.
        // TODO: Move behind domain
        IntervalObserver objsobs = new IntervalObserverList( m_objsinks.values() );
        IntervalObserver objsfil = new CountingIntervalFilter( m_delints, m_recints, null, objsobs );

        // Each domain collects the values per interval of all contained clients the the complete benchmark run.
        // -> The domain meters need to be provided with all (unfiltered) intervals.
        IntervalObserver domintobs = new IntervalObserverList( m_domsinks );
        return IntervalObserverList.createIfNecessary( objsfil, domintobs );
    }


    private void intervalFinished(IntervalResult<?> meter, int intno, long dur, LongStatsSink intsink)
    {
        synchronized( m_intsum )
        {
            assert intno==m_intno;

            m_intsum.add( intsink );

            if( m_nmeasrs<m_ndomains-1 )
                m_nmeasrs++;
            else
            {
                m_intno++;
                m_nmeasrs = 0;

                IntervalResultFormatter.printInterval( meter, intno, dur, m_intsum );

                m_intsum.reset();
            }
        }
    }


    public IntervalResult<? extends StatisticalSummary> getObjectResult(int objid)
    {
        return m_objsinks.get( objid );
    }


    public IntervalHistorySummary<StatisticalSummaryValues, StatisticalSummaryValues> aggregatedResult()
    {
        int nints = -1;
        List<IntervalResult<SummaryStatsSink>> intreslist = new ArrayList<>( m_domresults.size() );

        for( IntervalHistorySummary<SummaryStatsSink, SummaryStatsSink> hissum : m_domresults )
        {
            if( nints==-1 )
                nints = hissum.getNumberOfAvailInts();
            else if( hissum.getNumberOfAvailInts()!=nints )
                throw new IllegalArgumentException();

            intreslist.add( hissum.getSummary() );
        }

        IntervalResult<StatisticalSummaryValues> aggsum = aggregateIntervalResults( intreslist );

        List<IntervalResult<StatisticalSummaryValues>> agghislist = new ArrayList<>( nints );

        for( int i=0; i<nints; i++ )
        {
            intreslist.clear();

            for( IntervalHistorySummary<SummaryStatsSink, SummaryStatsSink> hissum : m_domresults )
                intreslist.add( hissum.getInterval( i ) );

            agghislist.add( aggregateIntervalResults( intreslist ) );
        }


        IntervalHistory<IntervalResult<StatisticalSummaryValues>> agghis = new IntervalHistory<IntervalResult<StatisticalSummaryValues>>()
        {
            @Override
            public int getNumberOfAvailInts()
            {
                return agghislist.size();
            }

            @Override
            public IntervalResult<StatisticalSummaryValues> getInterval(int index)
            {
                return agghislist.get( index );
            }
        };


        return IntervalHistorySummary.create( aggsum, agghis );
    }


    private IntervalResult<StatisticalSummaryValues>
            aggregateIntervalResults(Collection<IntervalResult<SummaryStatsSink>> intreslist)
    {
        if( intreslist==null || intreslist.size()==0 )
            return null;

        List<SummaryStatistics> sums = new ArrayList<>( intreslist.size() );
        long totdur = 0;

        for( IntervalResult<? extends SummaryStatistics> intres : intreslist )
        {
            sums.add( intres.getValueSummary() );
            totdur += intres.getElapsedTime();
        }

        return IntervalResult.create( totdur/intreslist.size(), AggregateSummaryStatistics.aggregate( sums ) );
    }

}
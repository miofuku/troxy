package reptor.measr.sink;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import reptor.measr.DoubleValueSink;
import reptor.measr.LongValueSink;


public class SummaryStatsSink extends SummaryStatistics implements DoubleValueSink, LongValueSink
{
    private static final long serialVersionUID = 2902235438463218695L;


    public SummaryStatsSink()
    {
    }


    public SummaryStatsSink(SummaryStatistics org)
    {
        super( org );
    }


    @Override
    public void accept(double value)
    {
        addValue( value );
    }


    @Override
    public void accept(long value)
    {
        addValue( value );
    }


    @Override
    public void reset()
    {
        clear();
    }
}

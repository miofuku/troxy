package reptor.measr.sink;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import reptor.measr.DoubleValueSink;
import reptor.measr.LongValueSink;


public class DescriptiveStatsSink extends DescriptiveStatistics implements DoubleValueSink, LongValueSink
{
    private static final long serialVersionUID = -114184299425522354L;


    public DescriptiveStatsSink()
    {
    }


    public DescriptiveStatsSink(int window)
    {
        super( window );
    }


    public DescriptiveStatsSink(double[] initial)
    {
        super( initial );
    }


    public DescriptiveStatsSink(DescriptiveStatistics org)
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

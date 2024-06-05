package reptor.test.chronos.message;

import reptor.chronos.Immutable;


@Immutable
public class IPongConfiguration
{

    protected int   m_nsenders = 10;
    protected int   m_wndsize  = 100;
    protected int   m_minbatch = m_wndsize;
    protected int   m_maxbatch = m_wndsize;
    protected int   m_duration = 30000;
    protected int   m_printint = 1000;

    public int getNumberOfSenders()     { return m_nsenders; }
    public int getMessageWindowSize()   { return m_wndsize; }
    public int getMinimumBatchSize()    { return m_minbatch; }
    public int getMaximumBatchSize()    { return m_maxbatch; }
    public int getDuration()            { return m_duration; }
    public int getPrintInterval()       { return m_printint; }

}
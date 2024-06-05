package reptor.test.chronos.message;


public class IPongConfigurator extends IPongConfiguration
{
    public void setNumberOfSenders(int nsenders)    { m_nsenders = nsenders; }
    public void setMessageWindowSize(int wndsize)   { m_wndsize = wndsize; }
    public void setMinimumBatchSize(int maxbatch)   { m_minbatch = maxbatch; }
    public void setMaximumBatchSize(int minbatch)   { m_maxbatch = minbatch; }
    public void setDuration(int duration)           { m_duration = duration; }
    public void setPrintInterval(int printint)      { m_printint = printint; }
}
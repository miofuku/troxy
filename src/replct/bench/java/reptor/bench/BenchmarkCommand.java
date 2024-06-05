package reptor.bench;

import reptor.jlib.NotImplementedException;
import reptor.replct.service.ServiceCommand;


public abstract class BenchmarkCommand implements ServiceCommand
{

    private long m_starttime = -1;
    private long m_endtime   = -1;

    public void invocationStarted(long starttime)
    {
        m_starttime = starttime;
    }


    public void invocationFinished(long endtime)
    {
        m_endtime = endtime;
    }


    public long getDuration()
    {
        return m_endtime-m_starttime;
    }


    public abstract boolean hasResult();


    public void reset()
    {
        m_starttime = m_endtime = -1;
    }


    @Override
    public void bindToMaster(Object master)
    {
        throw new NotImplementedException();
    }


    @Override
    public void unbindFromMaster(Object master)
    {
        throw new NotImplementedException();

    }

}

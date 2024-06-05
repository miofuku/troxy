package reptor.distrbt.com.connect;

import java.io.IOException;
import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.chronos.ChronosTask;
import reptor.chronos.com.CommunicationSink;
import reptor.chronos.com.CommunicationSource;
import reptor.chronos.com.SynchronousLinkElement;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.orphics.AbstractMaster;
import reptor.distrbt.com.NetworkConnection;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.DataChannelContext;
import reptor.distrbt.io.DataChannelTask;


public abstract class AbstractNetworkConnection<I extends CommunicationSource, O extends CommunicationSink>
        extends AbstractMaster<SelectorDomainContext>
        implements NetworkConnection<I, O>, DataChannelContext<SelectorDomainContext>
{

    protected SchedulerContext<? extends SelectorDomainContext> m_master;


    public AbstractNetworkConnection()
    {
    }


    protected void bindToMaster(SchedulerContext<? extends SelectorDomainContext> master)
    {
        Preconditions.checkState( m_master==null );

        m_master = master;
    }


    protected void unbindFromMaster(SchedulerContext<? extends SelectorDomainContext> master)
    {
        Preconditions.checkArgument( Objects.requireNonNull( master )==m_master );

        m_master = null;
    }


    @Override
    protected SchedulerContext<? extends SelectorDomainContext> master()
    {
        return m_master;
    }


    @Override
    public void taskReady(ChronosTask task)
    {
        notifyReady();
    }


    @Override
    public void dataReady(SynchronousLinkElement elem)
    {
        notifyReady();
    }


    protected boolean executeChannels(DataChannelTask first, DataChannelTask second) throws IOException
    {
        if( first.isReady() )
            first.execute();

        if( second.isReady() )
            second.execute();

        return !first.isReady() && !second.isReady();
    }

}

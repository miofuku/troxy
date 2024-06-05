package reptor.distrbt.io.stage;

import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.chronos.com.CommunicationSink;
import reptor.chronos.com.CommunicationSource;
import reptor.chronos.com.CommunicationStage;
import reptor.chronos.context.ChronosDomainContext;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.DataChannelContext;
import reptor.distrbt.io.GenericDataLinkElement;


public abstract class AbstractDataStage<I extends CommunicationSink, O extends CommunicationSource>
        implements CommunicationStage<I, O>
{

    // TODO: Both masters should belong to the same channel.
    private DataChannelContext<? extends SelectorDomainContext> m_sinkmaster;
    private DataChannelContext<? extends SelectorDomainContext> m_sourcemaster;

    private String          m_channelname;


    protected abstract ChronosDomainContext domainContext();


    protected String channelName()
    {
        return m_channelname;
    }


    public void bindSinkToMaster(DataChannelContext<? extends SelectorDomainContext> sinkmaster)
    {
        Preconditions.checkState( m_sinkmaster==null );
        Preconditions.checkArgument( sinkmaster.getDomainContext().getDomainAddress()==domainContext().getDomainAddress()  );
        Preconditions.checkState( !isActivated() );

        m_sinkmaster  = sinkmaster;
        m_channelname = sinkmaster.getChannelName();
    }


    public void unbindSinkFromMaster(DataChannelContext<? extends SelectorDomainContext> sinkmaster)
    {
        Preconditions.checkArgument( Objects.requireNonNull( sinkmaster )==m_sinkmaster );
        Preconditions.checkState( !isActivated() );

        m_sinkmaster = null;
    }


    protected DataChannelContext<? extends SelectorDomainContext> sinkMaster()
    {
        return m_sinkmaster;
    }


    public void bindSourceToMaster(DataChannelContext<? extends SelectorDomainContext> sourcemaster)
    {
        Preconditions.checkState( m_sourcemaster==null );
        Preconditions.checkArgument( sourcemaster.getDomainContext().getDomainAddress()==domainContext().getDomainAddress()  );

        assert !isActivated();

        m_sourcemaster = sourcemaster;
    }


    public void unbindSourceFromMaster(DataChannelContext<? extends SelectorDomainContext> sinkmaster)
    {
        Preconditions.checkArgument( Objects.requireNonNull( sinkmaster )==m_sourcemaster );
        Preconditions.checkState( !isActivated() );

        m_sourcemaster = null;
    }


    protected DataChannelContext<? extends SelectorDomainContext> sourceMaster()
    {
        return m_sourcemaster;
    }


    protected abstract class AbstractSink implements GenericDataLinkElement
    {
        @Override
        public void bindToMaster(DataChannelContext<? extends SelectorDomainContext> master)
        {
            AbstractDataStage.this.bindSinkToMaster( master );
        }

        @Override
        public void unbindFromMaster(DataChannelContext<? extends SelectorDomainContext> master)
        {
            AbstractDataStage.this.unbindSinkFromMaster( master );
        }
    }


    protected abstract class AbstractSource implements GenericDataLinkElement
    {
        @Override
        public void bindToMaster(DataChannelContext<? extends SelectorDomainContext> master)
        {
            AbstractDataStage.this.bindSourceToMaster( master );
        }

        @Override
        public void unbindFromMaster(DataChannelContext<? extends SelectorDomainContext> master)
        {
            AbstractDataStage.this.unbindSourceFromMaster( master );
        }
    }

}

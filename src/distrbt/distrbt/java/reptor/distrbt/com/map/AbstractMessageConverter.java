package reptor.distrbt.com.map;

import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.chronos.Notifying;
import reptor.chronos.com.CommunicationSink;
import reptor.chronos.com.CommunicationSource;
import reptor.chronos.com.CommunicationStage;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.DataChannelContext;
import reptor.distrbt.io.GenericDataLinkElement;


public abstract class AbstractMessageConverter<I extends CommunicationSink, O extends CommunicationSource>
        implements CommunicationStage<I, O>, GenericDataLinkElement
{

    protected DataChannelContext<? extends SelectorDomainContext>  m_master;

    protected final MessageMapper   m_mapper;
    protected boolean               m_isactivated = false;


    // Although it's only used for logging, the channel name is cached since getting it can be a slow operation
    // due to cascading invocations.
    private String m_channelname;


    public AbstractMessageConverter(MessageMapper mapper)
    {
        m_mapper = Objects.requireNonNull( mapper );
    }


    @Override
    public void bindToMaster(DataChannelContext<? extends SelectorDomainContext> master)
    {
        Preconditions.checkState( m_master==null );
        Preconditions.checkState( !m_isactivated );

        m_master = master;
        m_channelname = master.getChannelName();
    }


    @Override
    public void unbindFromMaster(DataChannelContext<? extends SelectorDomainContext> master)
    {
        Preconditions.checkArgument( Objects.requireNonNull( master )==m_master );
        Preconditions.checkState( !m_isactivated );

        m_master = null;
    }


    protected String channelName()
    {
        return m_channelname;
    }


    @Override
    @Notifying
    public void activate()
    {
        if( m_isactivated )
            return;

        Preconditions.checkState( m_master!=null );

        m_isactivated = true;
    }


    @Override
    public void deactivate()
    {
        if( !m_isactivated )
            return;

        Preconditions.checkState( m_master!=null );

        m_isactivated = false;
    }


    @Override
    public boolean isActivated()
    {
        return m_isactivated;
    }

}

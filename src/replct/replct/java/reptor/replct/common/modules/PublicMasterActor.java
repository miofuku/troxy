package reptor.replct.common.modules;

import reptor.chronos.ChronosAddress;
import reptor.chronos.com.DomainEndpoint;
import reptor.chronos.com.MessageQueue;
import reptor.chronos.com.Portal;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.orphics.AbstractMaster;
import reptor.chronos.orphics.Actor;
import reptor.chronos.orphics.MessageQueueHandler;
import reptor.chronos.portals.MultiQueuePortal;
import reptor.chronos.portals.QueuePortal;
import reptor.distrbt.com.Message;
import reptor.distrbt.domains.SelectorDomainContext;


public abstract class PublicMasterActor extends AbstractMaster<SelectorDomainContext>
                                        implements Actor, PushMessageSink<Message>,
                                                   MessageQueueHandler<MessageQueue<Message>>,
                                                   DomainEndpoint<PushMessageSink<Message>>
{

    private final SchedulerContext<? extends SelectorDomainContext> m_cntxt;

    private final Portal<Message> m_msgsink;


    public PublicMasterActor(SchedulerContext<? extends SelectorDomainContext> cntxt, ChronosAddress[] remdoms)
    {
        m_cntxt   = cntxt;
        m_msgsink = remdoms!=null && remdoms.length>1 ? new MultiQueuePortal<>( this, remdoms ) : new QueuePortal<Message>( this );
    }


    @Override
    protected SchedulerContext<? extends SelectorDomainContext> master()
    {
        return m_cntxt;
    }


    protected ChronosAddress domainAddress()
    {
        return m_cntxt.getDomainContext().getDomainAddress();
    }


    @Override
    public PushMessageSink<Message> createChannel(ChronosAddress origin)
    {
        return m_msgsink.createChannel( origin );
    }


    @Override
    public void enqueueMessage(Message msg)
    {
        m_msgsink.enqueueMessage( msg );
    }


    @Override
    public void messagesReady(MessageQueue<Message> queue)
    {
        notifyReady();
    }


    @Override
    public boolean execute()
    {
        do
        {
            m_msgsink.retrieveMessages();

            Message msg;

            while( ( msg = m_msgsink.poll() )!=null )
                processMessage( msg );

            executeSubjects();
        }
        while( m_msgsink.isReady() );

        clearReady();

        return true;
    }


    protected abstract void processMessage(Message msg);


    protected abstract void executeSubjects();

}

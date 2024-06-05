package reptor.test.distrbt.com.connect;

import java.util.ArrayDeque;
import java.util.Queue;

import reptor.chronos.Asynchronous;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.ChronosSystemContext;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.orphics.AbstractTask;
import reptor.chronos.orphics.Actor;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.domains.SelectorDomainContext;


public abstract class SingleConnectionPeer extends AbstractTask
                                           implements Actor, PushMessageSink<Message>
{

    public interface Context extends SchedulerContext<SelectorDomainContext>
    {
        MessageMapper                           getMessageMapper();
        PushMessageSink<? super NetworkMessage> getPeerChannel();
        ChronosSystemContext                    getSystemContext();
    }


    private final Context           m_cntxt;

    private final Queue<Message>    m_inqueue = new ArrayDeque<>();


    public SingleConnectionPeer(Context cntxt)
    {
        m_cntxt = cntxt;
    }


    @Override
    protected SchedulerContext<SelectorDomainContext> master()
    {
        return m_cntxt;
    }


    protected MessageMapper mapper()
    {
        return m_cntxt.getMessageMapper();
    }


    protected PushMessageSink<? super NetworkMessage> peerChannel()
    {
        return m_cntxt.getPeerChannel();
    }


    protected Queue<Message> inQueue()
    {
        return m_inqueue;
    }


    @Asynchronous
    public void connectionEstablished()
    {
        notifyReady();
    }


    @Override
    public void enqueueMessage(Message msg)
    {
        m_inqueue.add( msg );

        notifyReady();
    }


    protected void shutdownSystem()
    {
        m_cntxt.getSystemContext().shutdownDomains();
    }

}
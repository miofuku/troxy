package reptor.bench.measure;

import java.util.function.LongConsumer;

import reptor.measr.meter.Stopwatch;
import reptor.replct.invoke.InvocationExtensions.FinishedReplyObserver;
import reptor.replct.invoke.InvocationExtensions.ReceivedRequestObserver;
import reptor.replct.invoke.InvocationExtensions.ReplyContextFactory;
import reptor.replct.invoke.InvocationExtensions.RequestContextFactory;
import reptor.replct.invoke.InvocationExtensions.RequestProcessingExtension;
import reptor.replct.invoke.InvocationMessages.Reply;
import reptor.replct.invoke.InvocationMessages.Request;


public class RequestProcessingMeter extends TasksMeter implements RequestProcessingExtension
{
    private final RequestContextFactory m_reqcntxtfactory = new RequestContextFactory()
                                                          {
                                                              @Override
                                                              public Object createRequestContext(int clientid)
                                                              {
                                                                  Stopwatch stopwatch = new Stopwatch();
                                                                  stopwatch.start();

                                                                  return stopwatch;
                                                              }
                                                          };

    private final ReplyContextFactory   m_repcntxtfactory = new ReplyContextFactory()
                                                          {
                                                              @Override
                                                              public Object createReplyContext(Request request)
                                                              {
                                                                  return request.getExtensionContext();
                                                              }
                                                          };


    private class FinReplyObserver implements FinishedReplyObserver
    {
        private final LongConsumer m_consumer;


        public FinReplyObserver(LongConsumer consumer)
        {
            m_consumer = consumer;
        }


        @Override
        public void replyFinished(Reply reply)
        {
            Object extcntxt = reply.getExtensionContext();

            if( extcntxt != null )
                m_consumer.accept( ((Stopwatch) extcntxt).stopAndReset() );
        }
    };


    private final FinReplyObserver[] m_repobs;


    public RequestProcessingMeter(int nstages, long durwarm, long durrun, long durcool, boolean withhis)
    {
        super( nstages, 1, durwarm, durrun, durcool, withhis );

        if( !isActive() )
            m_repobs = null;
        else
        {
            m_repobs = new FinReplyObserver[nstages];
            for( int i = 0; i < m_repobs.length; i++ )
                m_repobs[i] = new FinReplyObserver( getTaskConsumer( i, 0 ) );
        }
    }


    @Override
    public RequestContextFactory getRequestContextFactory()
    {
        return m_reqcntxtfactory;
    }


    @Override
    public ReplyContextFactory getReplyContextFactory()
    {
        return m_repcntxtfactory;
    }


    @Override
    public ReceivedRequestObserver getReceivedRequestObserver(int clientid)
    {
        return ReceivedRequestObserver.EMPTY;
    }


    @Override
    public FinishedReplyObserver getFinishedReplyObserver(int stageid)
    {
        return m_repobs != null ? m_repobs[stageid] : FinishedReplyObserver.EMPTY;
    }
}

package reptor.replct.invoke;

import reptor.replct.invoke.InvocationMessages.Reply;
import reptor.replct.invoke.InvocationMessages.Request;

public class InvocationExtensions
{
    @FunctionalInterface
    public interface RequestContextFactory
    {
        Object createRequestContext(int clientid);


        static final RequestContextFactory EMPTY = new RequestContextFactory()
                                                 {
                                                     @Override
                                                     public Object createRequestContext(int clientid)
                                                     {
                                                         return null;
                                                     }
                                                 };
    }

    @FunctionalInterface
    public interface ReplyContextFactory
    {
        Object createReplyContext(Request request);


        static final ReplyContextFactory EMPTY = new ReplyContextFactory()
                                               {
                                                   @Override
                                                   public Object createReplyContext(Request request)
                                                   {
                                                       return null;
                                                   }
                                               };
    }

    @FunctionalInterface
    public interface ReceivedRequestObserver
    {
        void requestReceived(int clientid, Request request);


        static final ReceivedRequestObserver EMPTY = new ReceivedRequestObserver()
                                                   {
                                                       @Override
                                                       public void requestReceived(int clientid, Request request)
                                                       {
                                                       }
                                                   };
    }

    @FunctionalInterface
    public interface FinishedReplyObserver
    {
        void replyFinished(Reply reply);


        static final FinishedReplyObserver EMPTY = new FinishedReplyObserver()
                                                 {
                                                     @Override
                                                     public void replyFinished(Reply reply)
                                                     {
                                                     }
                                                 };
    }

    public interface RequestProcessingExtension
    {
        RequestContextFactory getRequestContextFactory();


        ReplyContextFactory getReplyContextFactory();


        ReceivedRequestObserver getReceivedRequestObserver(int clientid);


        FinishedReplyObserver getFinishedReplyObserver(int stateid);
    }


    private final RequestProcessingExtension m_reqext;


    public InvocationExtensions(RequestProcessingExtension reqext)
    {
        m_reqext = reqext;
    }


    public RequestContextFactory getRequestContextFactory()
    {
        return m_reqext != null ? m_reqext.getRequestContextFactory() : RequestContextFactory.EMPTY;
    }


    public ReplyContextFactory getReplyContextFactory()
    {
        return m_reqext != null ? m_reqext.getReplyContextFactory() : ReplyContextFactory.EMPTY;
    }


    public ReceivedRequestObserver getReceivedRequestObserver(int clientid)
    {
        return m_reqext != null ? m_reqext.getReceivedRequestObserver( clientid ) : ReceivedRequestObserver.EMPTY;
    }


    public FinishedReplyObserver getFinishedReplyObserver(int stageid)
    {
        return m_reqext != null ? m_reqext.getFinishedReplyObserver( stageid ) : FinishedReplyObserver.EMPTY;
    }
}

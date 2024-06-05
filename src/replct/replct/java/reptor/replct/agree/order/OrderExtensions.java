package reptor.replct.agree.order;

import reptor.distrbt.com.Message;


public class OrderExtensions
{
    // -- Extension point type
    public interface OrderInstanceObserver
    {
        void messageFetched(Message msg);


        void proposalFetched(Message proposal);


        void instanceInitialized(long instid, int viewid);


        void instanceCompleted(Message result);


        static final OrderInstanceObserver EMPTY = new OrderInstanceObserver()
                                                 {
                                                     @Override
                                                     public void messageFetched(Message msg)
                                                     {
                                                     }


                                                     @Override
                                                     public void proposalFetched(Message proposal)
                                                     {
                                                     }


                                                     @Override
                                                     public void instanceInitialized(long instid, int viewid)
                                                     {
                                                     }


                                                     @Override
                                                     public void instanceCompleted(Message result)
                                                     {
                                                     }
                                                 };
    }

    // -- Extension
    @FunctionalInterface
    public interface ProtocolInstanceExtension
    {
        OrderInstanceObserver getProtocolInstanceObserver(int orderid, int slotid);
    }


    private final ProtocolInstanceExtension m_protinstext;


    public OrderExtensions(ProtocolInstanceExtension protinstext)
    {
        m_protinstext = protinstext;
    }


    public OrderInstanceObserver getProtocolInstanceObserver(int orderid, int slotid)
    {
        return m_protinstext != null ? m_protinstext.getProtocolInstanceObserver( orderid, slotid )
                : OrderInstanceObserver.EMPTY;
    }
}

package refit.pbfto.order;

import reptor.chronos.ChronosTask;
import reptor.chronos.Orphic;
import reptor.distrbt.com.NetworkMessage;
import reptor.replct.agree.common.order.OrderMessages;
import reptor.replct.agree.common.order.OrderNetworkMessage;
import reptor.replct.agree.common.order.OrderExtensions.OrderInstanceObserver;
import reptor.replct.agree.common.view.View;


public abstract class OrderInstance implements ChronosTask, Orphic
{

    protected final OrderInstanceContext  cntxt;

    protected final OrderInstanceObserver protinstobserver;

    protected long                           instanceID;
    protected View                           view;


    public OrderInstance(OrderInstanceContext cntxt)
    {
        this.cntxt = cntxt;
        this.messageStore = cntxt.getMessageStore();
        this.instanceID = -1L;
        this.view = null;
        this.protinstobserver = cntxt.getOrderInstanceObserver();
    }


    // ##############
    // # LIFE CYCLE #
    // ##############

    public boolean complete;


    public void init(View view, long instanceID)
    {
        this.instanceID = instanceID;
        this.view = view;
        this.complete = false;

        protinstobserver.instanceInitialized( instanceID, view.getNumber() );
    }

    @Override
    public boolean isReady()
    {
        return messageStore.size()>0;
    }

    protected void abortInstance()
    {
        complete = true;
        messageStore.clear();
    }


    @Override
    public abstract boolean execute();


    protected void complete(short primaryID, OrderMessages.CommandContainer result)
    {
        protinstobserver.instanceCompleted( result );

        // Mark instance complete
        complete = true;

        // Nothing more to do if the instance did not produce a result
        if( result == null )
        {
            cntxt.instanceComplete( null );
            return;
        }

//        // Deliver stable result
//        Operation operation = new Operation( ExecutionMessages.REQUEST_OPERATION_ID, primaryID, instanceID, result );
//
//        // Complete instance
//        cntxt.instanceComplete( new CommandOrdered( inst.getProposer(), inst.getOrderNumber(), inst.getResult() ) );
    }


    // ####################
    // # MESSAGE HANDLING #
    // ####################

    private final OrderInstanceMessageStore<OrderNetworkMessage> messageStore;


    protected NetworkMessage fetchMessage(int typeid)
    {
        return fetchMessage( typeid, -1 );
    }


    protected NetworkMessage fetchMessage(int typeid, int viewID)
    {
        OrderNetworkMessage msg = messageStore.remove( typeid );

        assert msg==null || msg.getViewNumber()==viewID;

        protinstobserver.messageFetched( msg );

        return msg;
    }


    public long getInstanceID()
    {
        return instanceID;
    }


    // #####################
    // # PROPOSAL HANDLING #
    // #####################

    public abstract boolean isProposer();


    protected OrderMessages.CommandContainer fetchProposal()
    {
        OrderMessages.CommandContainer proposal = cntxt.fetchProposal();

        protinstobserver.proposalFetched( proposal );

        return proposal;
    }

}

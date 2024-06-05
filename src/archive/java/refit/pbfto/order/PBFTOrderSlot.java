package refit.pbfto.order;

import distrbt.com.transmit.MessageTransmitter;
import refit.modules.worker.WorkerMessages.TransmitToReplicas;
import reptor.chronos.DomainEndpoint;
import reptor.chronos.PushMessageSink;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.NetworkMessage;
import reptor.replct.agree.common.AgreementPeers;
import reptor.replct.agree.common.checkpoint.CheckpointMessages.Snapshot;
import reptor.replct.agree.common.order.OrderExtensions;
import reptor.replct.agree.common.order.OrderMessages;
import reptor.replct.agree.common.order.OrderNetworkMessage;
import reptor.replct.agree.common.order.OrderExtensions.OrderInstanceObserver;
import reptor.replct.agree.common.view.View;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

// TODO: Do we really need this slot class anymore? Or are instances and processor contexts sufficient?
public class PBFTOrderSlot implements OrderInstanceContext, Comparable<PBFTOrderSlot>
{

    protected final PBFTOrderShardProcessor proc;
    protected final PBFTOrderShard          ordershard;
    protected final int                 no;
    protected final int[]               wrkids;

    private OrderInstance               currentInstance;
    private long                        currentInstanceID;
    private int                         salt;

    private PushMessageSink<Message> orderlearner;
    private AgreementPeers           config;

    private final OrderInstanceObserver protinstobserver;


    public PBFTOrderSlot(PBFTOrderShardProcessor proc, PBFTOrderShard ordershard, int no, OrderExtensions extmanager)
    {
        // Initialize data structures
        this.proc = proc;
        this.ordershard = ordershard;
        this.no = no;
        this.messageStore = null;//new OrderInstanceMessageStore<OrderNetworkMessage>( ordershard.getMessageFactory() );
        this.currentInstanceID = -1L;
//        this.wrkids = Config.ORDERSTAGE.getWorkerIDs( ordershard.getShardNumber() );
//
//        this.protinstobserver = extmanager.getProtocolInstanceObserver( ordershard.getShardNumber(), no );
//
//        salt = hashCode();
        throw new  NotImplementedException();
    }


    @Override
    public void broadcastToReplicas(NetworkMessage msg)
    {
        int salt = generateMessageSalt();
        DomainEndpoint<PushMessageSink<Message>> worker = getWorker( salt );

        if( worker!=null )
            worker.createChannel( proc.getDomainContext().getDomainAddress() ).enqueueMessage( new TransmitToReplicas( msg, ordershard.getReplicaTransmission() ) );
        else
        {
            getCertifyingReplicaTransmitter().broadcastMessage( msg );
        }
    }


    @Override
    public DomainEndpoint<PushMessageSink<Message>> getWorker(int salt)
    {
//        return wrkids != null ? config.getWorkers().get( wrkids[Math.abs( salt % wrkids.length )] ) : null;
        throw new UnsupportedOperationException();
    }


    @Override
    public void instanceReady()
    {
        proc.slotReady( this );
    }


    @Override
    public String toString()
    {
        return String.format( "ORDSL%02d%04d[%d]", ordershard.getShardNumber(), no, currentInstanceID );
    }


    @Override
    public int compareTo(PBFTOrderSlot slot)
    {
        return (int) (currentInstanceID - slot.currentInstanceID);
    }


    public OrderInstance getCurrentInstance()
    {
        return currentInstance;
    }


    public boolean isProposer()
    {
        return currentInstance.isProposer();
    }


//    public void advanceView(View view)
//    {
//        currentInstance.advanceView( view );
//    }
//

    // ######################
    // # LIFE-CYCLE METHODS #
    // ######################

    public void initPeers(AgreementPeers config, PushMessageSink<Message> learner)
    {
        this.config  = config;
        orderlearner = learner;
    }

    public boolean init(View view, long instanceID)
    {
//        if( currentInstance != null && !currentInstance.complete
//                && !Config.CHECKPOINT_MODE.includes( CheckpointMode.APPLY ) )
//            throw new IllegalStateException( "Instance " + currentInstanceID
//                    + " has not been completed although applying checkpoints is not enabled!" );
//
//        currentInstanceID = instanceID;
//
//        this.currentInstance = ordershard.createOrderInstance( this, view.getReplicaNumber() );
//
//        // Initialize protocol instance
//        currentInstance.init( view, instanceID );
//
//        return messageStore.init( instanceID );
        throw new  NotImplementedException();
    }


    public void execute()
    {
        if( currentInstance.complete )
            return;
        currentInstance.execute();
    }


//    public void abort()
//    {
//        currentInstance = currentInstance.abort();
//        successorInstance = null;
//    }


    @Override
    public void instanceComplete(Snapshot result)
    {
        if( result != null )
            orderlearner.enqueueMessage( result );
    }


    // ####################
    // # MESSAGE HANDLING #
    // ####################

    protected final OrderInstanceMessageStore<OrderNetworkMessage> messageStore;


    public boolean insertMessage(OrderNetworkMessage message)
    {
        // Ignore messages for old instances, old views, and completed instances
        if( message.getOrderNumber() < currentInstanceID )
            return false;
        if( message.getOrderNumber() == currentInstanceID && currentInstance.complete )
            return false;

        return messageStore.add( message.getOrderNumber(), message );
    }


    @Override
    public int generateMessageSalt()
    {
        return salt++;
    }


    @Override
    public final OrderInstanceMessageStore<OrderNetworkMessage> getMessageStore()
    {
        return messageStore;
    }


    @Override
    public final OrderInstanceObserver getOrderInstanceObserver()
    {
        return protinstobserver;
    }


    @Override
    public OrderMessages.CommandContainer fetchProposal()
    {
        return proc.fetchProposal( currentInstanceID );
    }


    @Override
    public MessageTransmitter getCertifyingReplicaTransmitter()
    {
        return ordershard.getCertifyingReplicaTransmitter();
    }

}

package refit.hybstero.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import distrbt.com.transmit.MessageTransmitter;
import refit.hybstero.order.HybsterOrderMessages.HybsterCommit;
import refit.hybstero.order.HybsterOrderMessages.HybsterPrepare;
import reptor.chronos.Actor;
import reptor.chronos.Orphic;
import reptor.chronos.PushMessageSink;
import reptor.distrbt.certify.VerifierGroup;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.ImmutableData;
import reptor.jlib.collect.Slots;
import reptor.replct.MessageHandler;
import reptor.replct.agree.common.order.OrderMessages;
import reptor.replct.agree.common.order.OrderNetworkMessage;
import reptor.replct.agree.common.order.OrderExtensions.OrderInstanceObserver;
import reptor.replct.agree.common.view.View;
import reptor.replct.common.quorums.Votes;
import reptor.replct.invoke.InvocationMessages;


public class HybsterOrderInstance implements MessageHandler<OrderNetworkMessage>,
                                             Actor, PushMessageSink<OrderNetworkMessage>
{

    //-------------------------------------//
    //               Types                 //
    //-------------------------------------//

    public interface Context extends Orphic
    {
        MessageMapper        getMessageMapper();

        MessageTransmitter   getReplicaTCTransmitter();
        // TODO: Verification of requests should be carried out by client shards.
        VerifierGroup   getClientVerifiers();

        HybsterOrderVariant  getOrderVariant();
        int                  getOrderCommitThreshold();
        boolean              useSkipUnnecessaryOrderMessages();
    }


    private enum State
    {
        INITIALIZED,
        PREPARED,    // Received a proposal and sent own message (PREPARE or COMMIT) if necessary.
        COMMITTED;
    }


    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private static final Logger s_logger = LoggerFactory.getLogger( HybsterOrderInstance.class );

    private final Context                       m_cntxt;
    private final HybsterOrderVariant           m_variant;
    private final MessageMapper                 m_mapper;
    private final VerifierGroup            m_cliverifs;
    private final MessageTransmitter            m_reptrans;
    private final OrderInstanceObserver         m_observer;

    private Slots<OrderNetworkMessage>  m_inqueue  = null;
    // TODO: Probably, this part will become asynchronous, for instance to verify requests at the client shards.
    // TODO: If we used pre-allocated message objects, we would also have a store indexed by the message types.
    private Slots<OrderNetworkMessage>  m_msgstore = null;

    private State   m_state      = null;
    private int     m_viewno     = -1;
    private long    m_orderno    = -1;
    private byte    m_repno      = -1;
    private byte    m_proposer   = -1;
    private boolean m_isproposer = false;

    private OrderMessages.CommandContainer m_command  = null;  // The full (unhashed) command.
    private Votes<Data> m_votes;


    public HybsterOrderInstance(Context cntxt, OrderInstanceObserver observer)
    {
        m_cntxt     = cntxt;
        // TODO: If everything is final, would it also be possible to use everything directly?
        //       (Or using accessors like variant(), mapper() etc.)
        m_variant   = cntxt.getOrderVariant();
        m_mapper    = cntxt.getMessageMapper();
        m_cliverifs = cntxt.getClientVerifiers();
        m_reptrans  = cntxt.getReplicaTCTransmitter();
        m_observer  = observer;
    }


    private int commitThreshold()
    {
        return m_cntxt.getOrderCommitThreshold();
    }

    private boolean skipUnessaryMessages()
    {
        return m_cntxt.useSkipUnnecessaryOrderMessages();
    }


    //-------------------------------------//
    //           External State            //
    //-------------------------------------//

    @Override
    public String toString()
    {
        return "HYBSTER[" + m_orderno + "-" + m_viewno + "]";
    }


    public int getViewNumber()
    {
        return m_viewno;
    }

    public long getOrderNumber()
    {
        return m_orderno;
    }

    public byte getProposer()
    {
        return m_proposer;
    }

    public boolean isProposer()
    {
        return m_isproposer && m_command==null;
    }

    public State getState()
    {
        return m_state;
    }

    public boolean isPrepared()
    {
        return m_state==State.PREPARED;
    }

    public boolean isCommitted()
    {
        return m_state==State.COMMITTED;
    }

    public OrderMessages.CommandContainer getResult()
    {
        assert isCommitted();

        return m_command;
    }


    //-------------------------------------//
    //       Configuration Interface       //
    //-------------------------------------//

    public void init(View view, long orderno)
    {
        if( m_repno!=-1 )
        {
            m_inqueue.clear();
            m_msgstore.clear();
            m_votes.clear();
        }
        else
        {
            m_repno    = view.getReplicaGroup().getReplicaNumber();
            m_inqueue  = new Slots<>( view.getNumberOfReplicas() );
            m_msgstore = new Slots<>( view.getNumberOfReplicas() );
            m_votes    = new Votes<>( view.getNumberOfTolerableFaults()+1 );
        }

        m_viewno  = view.getNumber();
        m_orderno = orderno;

//        m_proposer   = view.getProposer( orderno );
        m_isproposer = m_proposer==m_repno;

        m_command = null;

        advanceState( State.INITIALIZED );

        m_observer.instanceInitialized( orderno, m_viewno );
    }


    //-------------------------------------//
    //            Actor Interface          //
    //-------------------------------------//

    @Override
    public void enqueueMessage(OrderNetworkMessage msg)
    {
        // TODO: If a slot is already occupied, what happened?
        //       It could be the same message, we don't have a problem then.
        //       In Hybster, the message certificates are already verified at this point and for each replica
        //       in each view and instance, it is pre-determined what it has to send. So maybe we can blacklist someone.
        if( m_inqueue.containsKey( msg.getSender() ) )
            throw new UnsupportedOperationException();

        m_inqueue.put( msg.getSender(), msg );
    }


    @Override
    public boolean isReady()
    {
        return !m_inqueue.isEmpty();
    }


    @Override
    public boolean execute()
    {
        if( m_inqueue.size()>1 )
        {
            OrderNetworkMessage msg;

            for( short i=0; i<m_inqueue.capacity(); i++ )
            {
                if( i!=m_proposer && ( msg=m_inqueue.removeKey( i ) )!=null )
                {
                    handleMessage( msg );

                    if( m_votes.getLeadingCount()>=commitThreshold()-1 )
                        break;
                }
            }

            if( ( msg=m_inqueue.removeKey( m_proposer ) )!=null )
                handleMessage( msg );
        }

        if( !m_inqueue.isEmpty() )
        {
            m_inqueue.forEach( this::handleMessage );
            m_inqueue.clear();
        }

        return true;
    }


    //-------------------------------------//
    //          Handler Interface          //
    //-------------------------------------//

    @Override
    public boolean handleMessage(OrderNetworkMessage msg)
    {
        assert msg.isCertificateValid()==Boolean.TRUE;

        m_observer.messageFetched( msg );

        switch( msg.getTypeID() )
        {
        case OrderMessages.COMMAND_BATCH_ID:
        case InvocationMessages.REQUEST_ID:
            return handleCommand( (OrderMessages.CommandContainer) msg );
        case HybsterOrderMessages.HYBSTER_PREPARE_ID:
            return handlePrepare( (HybsterPrepare) msg );
        case HybsterOrderMessages.HYBSTER_COMMIT_ID:
            return handleCommit( (HybsterCommit) msg );
        default:
            throw new IllegalArgumentException( msg.toString() );
        }
    }


    public boolean handleCommand(OrderMessages.CommandContainer command)
    {
        assert m_command==null;

        // TODO: If we order over hashes, maybe we can get a complete request at this point.
        if( !m_isproposer )
            throw new UnsupportedOperationException( command.toString() );

        m_command = command;

        HybsterPrepare prepare = prepareFor( command );
        prepare.setValid();
        // This serialises and certifies the message.
        m_reptrans.broadcastMessage( prepare );

        // We must add the message to the (quorum) certificate not before sending it because this requires
        // the calculated message certificate.
        addToStore( prepare, voteFor( prepare ) );

        advanceState( State.PREPARED );

        return true;
    }


    public boolean handlePrepare(HybsterPrepare prepare)
    {
        // If we had time, we could nevertheless verify this PREPARE to detect errors.
        if( isCommitted() )
            return false;

        if( prepare.getSender()!=m_proposer )
            throw new UnsupportedOperationException();

        if( isAlreadyKnown( prepare ) )
            return false;

        // TODO: Verifying inner messages is generic.
        if( prepare.areInnerMessagesValid()==Boolean.FALSE )
            throw new UnsupportedOperationException();
        else if( prepare.areInnerMessagesValid()==null )
        {
            // TODO: PREPAREs with hashes. (If we didn't have a command, we won't be able to verify the
            //       PREPARE at this point.)
            // An invalid command in a prepare with valid certificate? -> blacklist.
            prepare.getCommand().verifyCommands( m_mapper, m_cliverifs );
            prepare.setInnerMessagesValid( true );
        }

        m_command = prepare.getCommand();

        ImmutableData proposal = voteFor( prepare );
        addToStore( prepare, proposal );

        boolean iscommitted = false;

        // We need the full command and we need to know that it was proposed by the leader
        // then we are allowed to send a commit.
        if( !skipUnessaryMessages() || !( iscommitted=checkCommitted() ) )
        {
            // TODO: If we want to support hashed PREPAREs, we would need to check if we already have
            //       the full command.
            HybsterCommit commit = commitFor( prepare, m_command, proposal );
            commit.setValid();

            m_reptrans.broadcastMessage( commit );

            addToStore( commit, voteFor( commit ) );

            advanceState( State.PREPARED );
        }

        if( iscommitted || checkCommitted() )
            instanceCompleted();

        return true;
    }


    public boolean handleCommit(HybsterCommit commit)
    {
        if( isCommitted() )
            return false;

        if( commit.getSender()==m_proposer )
            throw new UnsupportedOperationException();

        if( isAlreadyKnown( commit ) )
            return false;

        // TODO: Verify inner messages when full commands are ordered.
        commit.setInnerMessagesValid( true );

        addToStore( commit, voteFor( commit ) );

        if( !checkCommitted() )
            return false;
        else
        {
            instanceCompleted();
            return true;
        }
    }


    //-------------------------------------//
    //        Additional Internals         //
    //-------------------------------------//

    private boolean isAlreadyKnown(OrderNetworkMessage msg)
    {
        OrderNetworkMessage curmsg = m_msgstore.get( msg.getSender() );

        if( curmsg==null )
            return false;
        else if( curmsg.equals( msg ) )
            return true;
        else
            // TODO: Did someone send two conflicting messages? -> blacklist.
            throw new UnsupportedOperationException();
    }


    private void addToStore(OrderNetworkMessage msg, Data vote)
    {
        m_msgstore.put( msg.getSender(), msg );
        addToVotes( vote );
    }


    private HybsterPrepare prepareFor(OrderMessages.CommandContainer command)
    {
        return new HybsterPrepare( m_repno, m_orderno, m_viewno, command );
    }


    private HybsterCommit commitFor(HybsterPrepare prepare, OrderMessages.CommandContainer command, ImmutableData proposal)
    {
        if( m_variant==HybsterOrderVariant.AgreeOnFullCommand )
            return new HybsterCommit( m_repno, m_orderno, m_viewno, command );
        else
            return new HybsterCommit( m_repno, m_orderno, m_viewno, proposal );
    }


    private ImmutableData voteFor(HybsterPrepare prepare)
    {
        if( m_variant.useCertificateVotes() )
            return prepare.getCertificateData().immutable();
        else
            // In most configurations, the command was already digested for the verification.
            return m_mapper.digestMessage( prepare.getCommand() ).getMessageDigest();
    }


    private ImmutableData voteFor(HybsterCommit commit)
    {
        return m_variant==HybsterOrderVariant.AgreeOnFullCommand ?
                    commit.getCommand().getMessageDigest() : commit.getData();
    }


    private void addToVotes(Data vote)
    {
        m_votes.addVote( vote );

        assert m_votes.isUnanimous();
    }


    private boolean checkCommitted()
    {
        if( m_votes.getLeadingCount()<commitThreshold() )
            return false;

        // As usual, we need two things: The complete command and quorum that supports it.
        // If prepare certificates are ordered, we need the PREPARE to learn about the command.
        if( m_variant!=HybsterOrderVariant.AgreeOnFullCommand )
        {
            // In the current implementation, if the command is set, the PREPARE is set as well.
            if( m_command==null )
                return false;
        }
        else if( m_command==null )
        {
            // If we agree on complete commands, a quorum of COMMITs suffices.
            // TODO: Look up findFirst
            HybsterCommit commit = null;

            for( short i=0; i<m_msgstore.capacity(); i++ )
                if( i!=m_proposer )
                    commit = (HybsterCommit) m_msgstore.get( i );

            m_command = commit.getCommand();

            s_logger.debug( "{} learned {} from commits", this, m_command );
        }

        // TODO: It should not be possible, using Hybster and the current implementation, that the vote
        //       differs from the proposal. However, that still needs to be confirmed.

        return true;
    }


    private void instanceCompleted()
    {
        advanceState( State.COMMITTED );

        m_observer.instanceCompleted( m_command );
    }


    private void advanceState(State state)
    {
        m_state = state;

        s_logger.debug( "{} {} ({}, prop {}) at {}", this, state, m_command, m_proposer, m_cntxt );
    }

}

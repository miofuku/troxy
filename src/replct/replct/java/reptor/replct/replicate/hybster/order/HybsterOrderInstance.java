package reptor.replct.replicate.hybster.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.chronos.Orphic;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.orphics.Actor;
import reptor.distrbt.certify.trusted.CounterCertifier;
import reptor.distrbt.certify.trusted.TrustedCounterGroupCertifier;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.MessageVerifier;
import reptor.distrbt.common.data.Data;
import reptor.jlib.collect.Slots;
import reptor.replct.MessageHandler;
import reptor.replct.agree.View;
import reptor.replct.agree.order.OrderExtensions.OrderInstanceObserver;
import reptor.replct.agree.order.OrderMessages.Command;
import reptor.replct.agree.order.OrderMessages.CommandContainer;
import reptor.replct.agree.order.OrderNetworkMessage;
import reptor.replct.common.quorums.Votes;
import reptor.replct.replicate.hybster.order.HybsterOrderMessages.HybsterCommit;
import reptor.replct.replicate.hybster.order.HybsterOrderMessages.HybsterPrepare;


public class HybsterOrderInstance implements MessageHandler<OrderNetworkMessage>,
                                              Actor, PushMessageSink<OrderNetworkMessage>
{

    //-------------------------------------//
    //               Types                 //
    //-------------------------------------//

    public interface Context extends Orphic
    {
        MessageMapper                                   getMessageMapper();
        PushMessageSink<? super OrderNetworkMessage>    getReplicaChannel();
        TrustedCounterGroupCertifier                    getOrderCertifier(byte proposer);
        MessageVerifier<? super Command>                getProposalVerifier();
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
    private final HybsterOrdering               m_config;
    private final HybsterOrderVariant           m_variant;
    private final int                           m_commitquorum;
    private final boolean                       m_skip;
    private final MessageMapper                 m_mapper;
    private final OrderInstanceObserver         m_observer;

    private Slots<OrderNetworkMessage>  m_inqueue  = null;
    // TODO: Probably, this part will become asynchronous, for instance to verify requests at the client shards.
    // TODO: If we used pre-allocated message objects, we would also have a store indexed by the message types.
    private Slots<OrderNetworkMessage>  m_msgstore = null;

    private State   m_state      = null;
    private long    m_localno    = -1;
    private long    m_orderno    = -1;
    private int     m_viewno     = -1;
    private byte    m_repno      = -1;
    private byte    m_proposer   = -1;
    private boolean m_isproposer = false;

    private CommandContainer m_command  = null;  // The full (unhashed) command.
    private Votes<Data>      m_votes;


    public HybsterOrderInstance(Context cntxt, HybsterOrdering config, OrderInstanceObserver observer)
    {
        m_cntxt        = cntxt;
        m_config       = config;
        m_observer     = observer;

        m_variant      = config.getVariant();
        m_commitquorum = config.getCommitQuorumSize();
        m_skip         = config.getUsePassiveProgress();
        m_mapper       = cntxt.getMessageMapper();
    }


    private MessageMapper mapper()
    {
        return m_mapper;
    }

    private PushMessageSink<? super OrderNetworkMessage> replicaChannel()
    {
        return m_cntxt.getReplicaChannel();
    }

    private TrustedCounterGroupCertifier orderCertifier(byte proposer)
    {
        return m_cntxt.getOrderCertifier( proposer );
    }

    private MessageVerifier<? super Command> proposalVerifier()
    {
        return m_cntxt.getProposalVerifier();
    }


    //-------------------------------------//
    //           External State            //
    //-------------------------------------//

    @Override
    public String toString()
    {
        return "HYBSTER[" + m_localno + "/" + m_orderno + "-" + m_viewno + "]";
    }

    public long getLocalNumber()
    {
        return m_localno;
    }

    public long getOrderNumber()
    {
        return m_orderno;
    }

    public int getViewNumber()
    {
        return m_viewno;
    }

    public byte getProposer()
    {
        return m_proposer;
    }

    public boolean isProposer()
    {
        return m_isproposer;
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

    public CommandContainer getResult()
    {
        assert isCommitted();

        return m_command;
    }

    public CommandContainer getCommand()
    {
        return m_command;
    }

    public OrderNetworkMessage preparedCertificate()
    {
        // TODO: When we learn a commit passively, we maybe only have a command and COMMITs. -> OrderCertificate
        return isPrepared() || isCommitted() ? (HybsterPrepare) m_msgstore.get( m_proposer ) : null;
    }

    public OrderNetworkMessage getOwnMessage()
    {
        return m_msgstore.get( m_repno );
    }


    //-------------------------------------//
    //       Configuration Interface       //
    //-------------------------------------//

    public void init(long localno, long orderno, View view)
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

        m_orderno = orderno;
        m_localno = localno;
        m_viewno  = view.getNumber();

        m_proposer   = m_config.getProposer( m_viewno, orderno );
        m_isproposer = m_proposer==m_repno;

        m_command = null;

        advanceState( State.INITIALIZED );

        m_observer.instanceInitialized( orderno, m_viewno );
    }


    // TODO: Create new prepares for new views here.
    public void setPrepare(HybsterPrepare prepare)
    {
        assert prepare.getSender()==m_repno;

        m_command = prepare.getCommand();
        addToStore( prepare, voteFor( prepare ) );

        advanceState( State.PREPARED );
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

                    if( m_votes.getLeadingCount()>=m_commitquorum-1 )
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
        m_observer.messageFetched( msg );

        switch( msg.getTypeID() )
        {
        case HybsterOrderMessages.HYBSTER_PREPARE_ID:
            return handlePrepare( (HybsterPrepare) msg );
        case HybsterOrderMessages.HYBSTER_COMMIT_ID:
            return handleCommit( (HybsterCommit) msg );
        default:
            return handleCommand( (CommandContainer) msg );
        }
    }


    public boolean handleCommand(CommandContainer command)
    {
        assert m_command==null;

        // TODO: If we order over hashes, maybe we can get a complete request at this point.
        if( !m_isproposer )
            throw new UnsupportedOperationException( command.toString() );

        HybsterPrepare prepare = prepareFor( command );
        prepare.setValid();

        certifyOrderMessage( prepare );
        replicaChannel().enqueueMessage( prepare );

        // We must add the message to the (quorum) certificate not before sending it because this requires
        // the calculated message certificate.
        m_command = command;
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

        verifyOrderMessage( prepare );

        // TODO: Verifying inner messages is generic.
        if( prepare.areInnerMessagesValid()==Boolean.FALSE )
            throw new UnsupportedOperationException();
        else if( prepare.areInnerMessagesValid()==null )
        {
            // TODO: PREPAREs with hashes. (If we didn't have a command, we won't be able to verify the
            //       PREPARE at this point.)
            // An invalid command in a prepare with valid certificate? -> blacklist.
            prepare.getCommand().verifyCommands( mapper(), proposalVerifier() );
            prepare.setInnerMessagesValid( true );
        }

        m_command = prepare.getCommand();

        Data proposal = voteFor( prepare );
        addToStore( prepare, proposal );

        boolean iscommitted = false;

        // We need the full command and we need to know that it was proposed by the leader
        // then we are allowed to send a commit.
        if( !m_skip || !( iscommitted=checkCommitted() ) )
        {
            // TODO: If we want to support hashed PREPAREs, we would need to check if we already have
            //       the full command.
            HybsterCommit commit = commitFor( prepare, m_command, proposal );
            commit.setValid();

            certifyOrderMessage( commit );
            replicaChannel().enqueueMessage( commit );

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

        verifyOrderMessage( commit );

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

    private void certifyOrderMessage(OrderNetworkMessage msg)
    {
        CounterCertifier ordercertif = orderCertifier( m_proposer ).getCertifier();

        ordercertif.createIndependent().value( msg.getViewNumber(), msg.getOrderNumber() );
        mapper().certifyAndSerializeMessage( msg, ordercertif );
    }


    public void verifyOrderMessage(OrderNetworkMessage msg)
    {
        CounterCertifier ordercertif = orderCertifier( m_proposer ).getVerifier( msg.getSender() );

        ordercertif.verifyIndependent().value( msg.getViewNumber(), msg.getOrderNumber() );
        mapper().verifyMessage( msg, ordercertif );
    }


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


    private HybsterPrepare prepareFor(CommandContainer command)
    {
        return new HybsterPrepare( m_repno, m_orderno, m_viewno, command );
    }


    private HybsterCommit commitFor(HybsterPrepare prepare, CommandContainer command, Data proposal)
    {
        if( m_variant==HybsterOrderVariant.AgreeOnFullCommand )
            return new HybsterCommit( m_repno, m_orderno, m_viewno, command );
        else
            return new HybsterCommit( m_repno, m_orderno, m_viewno, proposal );
    }


    private Data voteFor(HybsterPrepare prepare)
    {
        if( m_variant.useCertificateVotes() )
            return prepare.getCertificateData();
        else
            // In most configurations, the command was already digested for the verification.
            return mapper().digestMessage( prepare.getCommand() ).getMessageDigest();
    }


    private Data voteFor(HybsterCommit commit)
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
        if( m_votes.getLeadingCount()<m_commitquorum )
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

//        s_logger.debug( "{} {} ({}, prop {}) at {}", this, state, m_command, m_proposer, m_cntxt );
    }

}

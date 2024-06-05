package reptor.replct.replicate.pbft.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.chronos.Orphic;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.orphics.Actor;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.MessageVerifier;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.ImmutableData;
import reptor.jlib.collect.Slots;
import reptor.replct.MessageHandler;
import reptor.replct.agree.View;
import reptor.replct.agree.order.AbstractOrderHolderMessage;
import reptor.replct.agree.order.OrderExtensions.OrderInstanceObserver;
import reptor.replct.agree.order.OrderMessages.Command;
import reptor.replct.agree.order.OrderMessages.CommandContainer;
import reptor.replct.agree.order.OrderNetworkMessage;
import reptor.replct.common.quorums.Votes;
import reptor.replct.replicate.pbft.order.PbftOrderMessages.PbftCommit;
import reptor.replct.replicate.pbft.order.PbftOrderMessages.PbftPrePrepare;
import reptor.replct.replicate.pbft.order.PbftOrderMessages.PbftPrepare;


public class PbftOrderInstance implements MessageHandler<OrderNetworkMessage>,
                                           Actor, PushMessageSink<OrderNetworkMessage>
{

    //-------------------------------------//
    //               Types                 //
    //-------------------------------------//

    public interface Context extends Orphic
    {
        MessageMapper                                   getMessageMapper();
        PushMessageSink<? super OrderNetworkMessage>    getReplicaChannel();
        GroupConnectionCertifier                        getStandardCertifier();
        MessageVerifier<? super Command>                getProposalVerifier();
    }


    private enum State
    {
        INITIALIZED,
        PREPREPARED,    // Received a proposal and sent own message (PREPARE or COMMIT) if necessary.
        PREPARED,
        COMMITTED;
    }


    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private static final Logger s_logger = LoggerFactory.getLogger( PbftOrderInstance.class );

    private final Context                       m_cntxt;
    private final PbftOrdering                  m_config;
    private final PbftOrderVariant              m_variant;
    private final int                           m_commitquorum;
    private final boolean                       m_skip;
    private final MessageMapper                 m_mapper;
    private final OrderInstanceObserver         m_observer;

    // TODO: Phases have an inqueue, a store, and votes
    private Slots<OrderNetworkMessage>  m_pre_inqueue  = null;
    // TODO: Probably, this part will become asynchronous, for instance to verify requests at the client shards.
    // TODO: If we used pre-allocated message objects, we would also have a store indexed by the message types.
    private Slots<OrderNetworkMessage>  m_pre_msgstore = null;

    private Slots<OrderNetworkMessage>  m_com_inqueue  = null;
    private Slots<OrderNetworkMessage>  m_com_msgstore = null;

    private CommandContainer            m_command  = null;  // The full (unhashed) command.
    private ImmutableData               m_proposal;
    private Votes<Data>                 m_pre_votes;
    private Votes<Data>                 m_com_votes;

    private State   m_state      = null;
    private int     m_viewno     = -1;
    private long    m_orderno    = -1;
    private byte    m_repno      = -1;
    private byte    m_proposer   = -1;
    private boolean m_isproposer = false;

    public PbftOrderInstance(Context cntxt, PbftOrdering config, OrderInstanceObserver observer)
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

    private GroupConnectionCertifier standardCertifier()
    {
        return m_cntxt.getStandardCertifier();
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
        return "PBFT[" + m_orderno + "-" + m_viewno + "]";
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

    public boolean isPrePrepared()
    {
        return m_state==State.PREPREPARED;
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


    //-------------------------------------//
    //       Configuration Interface       //
    //-------------------------------------//

    public void init(View view, long orderno)
    {
        if( m_repno!=-1 )
        {
            m_pre_inqueue.clear();
            m_pre_msgstore.clear();
            m_pre_votes.clear();
            m_com_inqueue.clear();
            m_com_msgstore.clear();
            m_com_votes.clear();
        }
        else
        {
            m_repno    = view.getReplicaGroup().getReplicaNumber();
            m_pre_inqueue  = new Slots<>( view.getNumberOfReplicas() );
            m_pre_msgstore = new Slots<>( view.getNumberOfReplicas() );
            m_pre_votes    = new Votes<>( view.getNumberOfTolerableFaults()+1 );
            m_com_inqueue  = new Slots<>( view.getNumberOfReplicas() );
            m_com_msgstore = new Slots<>( view.getNumberOfReplicas() );
            m_com_votes    = new Votes<>( view.getNumberOfTolerableFaults()+1 );
        }

        m_viewno  = view.getNumber();
        m_orderno = orderno;

        // TODO: Cache coordinator.
        m_proposer   = m_config.getProposer( m_viewno, orderno );
        m_isproposer = m_proposer==m_repno;

        m_command  = null;
        m_proposal = null;

        advanceState( State.INITIALIZED, null );

        m_observer.instanceInitialized( orderno, m_viewno );
    }


    //-------------------------------------//
    //            Actor Interface          //
    //-------------------------------------//

    @Override
    public void enqueueMessage(OrderNetworkMessage msg)
    {
        Slots<OrderNetworkMessage> inqueue = msg.getTypeID()==PbftOrderMessages.PBFT_COMMIT_ID ? m_com_inqueue : m_pre_inqueue;

        // TODO: If a slot is already occupied, what happened?
        //       It could be the same message, we don't have a problem then.
        if( inqueue.containsKey( msg.getSender() ) )
            throw new UnsupportedOperationException( msg.toString() );

        inqueue.put( msg.getSender(), msg );
    }


    @Override
    public boolean isReady()
    {
        return !m_pre_inqueue.isEmpty() || !m_com_inqueue.isEmpty();
    }


    @Override
    public boolean execute()
    {
        if( !m_com_inqueue.isEmpty() )
        {
            m_com_inqueue.forEach( this::handleMessage );
            m_com_inqueue.clear();
        }

        if( m_pre_inqueue.size()>1 )
        {
            OrderNetworkMessage msg;

            for( short i=0; i<m_pre_inqueue.capacity(); i++ )
            {
                if( i!=m_proposer && ( msg=m_pre_inqueue.removeKey( i ) )!=null )
                {
                    handleMessage( msg );

                    if( m_pre_votes.getLeadingCount()>=m_commitquorum-1 )
                        break;
                }
            }

            if( ( msg=m_pre_inqueue.removeKey( m_proposer ) )!=null )
                handleMessage( msg );
        }

        if( !m_pre_inqueue.isEmpty() )
        {
            m_pre_inqueue.forEach( this::handleMessage );
            m_pre_inqueue.clear();
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
        case PbftOrderMessages.PBFT_PREPREPARE_ID:
            return handlePrePrepare( (PbftPrePrepare) msg );
        case PbftOrderMessages.PBFT_PREPARE_ID:
            return handlePrepare( (PbftPrepare) msg );
        case PbftOrderMessages.PBFT_COMMIT_ID:
            return handleCommit( (PbftCommit) msg );
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

        PbftPrePrepare preprepare = proposalFor( command );
        preprepare.setValid();
        // This serialises and certifies the message.
        mapper().certifyAndSerializeMessage( preprepare, standardCertifier().getCertifier() );
        replicaChannel().enqueueMessage( preprepare );

        m_command  = command;
        m_proposal = voteForProposal( command );

        // We must add the message to the (quorum) certificate not before sending it because this requires
        // the calculated message certificate.
        addToStore( m_pre_msgstore, preprepare, m_pre_votes, m_proposal );

        advanceState( State.PREPREPARED, State.INITIALIZED );

        return true;
    }


    public boolean handlePrePrepare(PbftPrePrepare preprepare)
    {
        // If we had time, we could nevertheless verify this PREPARE to detect errors.
        if( m_state!=State.INITIALIZED )
            return false;

        if( preprepare.getSender()!=m_proposer )
            throw new UnsupportedOperationException();

        if( isAlreadyKnown( m_pre_msgstore, preprepare ) )
            return false;

        mapper().verifyMessage( preprepare, standardCertifier() );

        // TODO: Verifying inner messages is generic.
        if( preprepare.areInnerMessagesValid()==Boolean.FALSE )
            throw new UnsupportedOperationException();
        else if( preprepare.areInnerMessagesValid()==null )
        {
            // TODO: PREPAREs with hashes. (If we didn't have a command, we won't be able to verify the
            //       PREPARE at this point.)
            // An invalid command in a prepare with valid certificate? -> blacklist.
            preprepare.getCommand().verifyCommands( mapper(), proposalVerifier() );
            preprepare.setInnerMessagesValid( true );
        }

        m_command  = preprepare.getCommand();
        m_proposal = voteForProposal( preprepare.getCommand() );

        addToStore( m_pre_msgstore, preprepare, m_pre_votes, m_proposal );

        boolean isprepared  = false;
        boolean iscommitted = false;

        // We need the full command and we need to know that it was proposed by the leader
        // then we are allowed to send a commit.
        // TODO: If we want to support hashed PREPAREs, we would need to check if we already have
        //       the full command.
        if( !m_skip || !( iscommitted=checkCommitted() ) && !( isprepared=checkPrepared() ) )
            instancePrePrepared();
        else if( iscommitted )
            instanceCompleted();
        else if( isprepared )
            instancePrepared();

        return true;
    }


    private void instancePrePrepared()
    {
        advanceState( State.PREPREPARED, State.INITIALIZED );

        PbftPrepare prepare = prepareFor( m_command, m_proposal );
        prepare.setValid();

        mapper().certifyAndSerializeMessage( prepare, standardCertifier().getCertifier() );
        replicaChannel().enqueueMessage( prepare );

        addToStore( m_pre_msgstore, prepare, m_pre_votes, voteForAck( prepare ) );

        if( checkPrepared() )
            instancePrepared();
    }


    public boolean handlePrepare(PbftPrepare prepare)
    {
        if( isCommitted() || isPrepared() )
            return false;

        if( prepare.getSender()==m_proposer )
            throw new UnsupportedOperationException();

        if( isAlreadyKnown( m_pre_msgstore, prepare ) )
            return false;

        mapper().verifyMessage( prepare, standardCertifier() );

        // TODO: Verify inner messages when full commands are ordered.
        prepare.setInnerMessagesValid( true );

        addToStore( m_pre_msgstore, prepare, m_pre_votes, voteForAck( prepare ) );

        if( !checkPrepared() )
            return false;
        else
        {
            instancePrepared();
            return true;
        }
    }


    private void instancePrepared()
    {
        advanceState( State.PREPARED, State.PREPREPARED );

        PbftCommit commit = commitFor( m_command, m_proposal );
        commit.setValid();

        mapper().certifyAndSerializeMessage( commit, standardCertifier().getCertifier() );
        replicaChannel().enqueueMessage( commit );

        addToStore( m_com_msgstore, commit, m_com_votes, voteForAck( commit ) );

        if( checkCommitted() )
            instanceCompleted();
    }


    public boolean handleCommit(PbftCommit commit)
    {
        if( isCommitted() )
            return false;

        if( isAlreadyKnown( m_com_msgstore, commit ) )
            return false;

        mapper().verifyMessage( commit, standardCertifier() );

        // TODO: Verify inner messages when full commands are ordered.
        commit.setInnerMessagesValid( true );

        addToStore( m_com_msgstore, commit, m_com_votes, voteForAck( commit ) );

        if( !checkCommitted() || !m_skip && !isPrepared() )
            return false;
        else
        {
            instanceCompleted();
            return true;
        }
    }


    private void instanceCompleted()
    {
        advanceState( State.COMMITTED, State.PREPARED );

        m_observer.instanceCompleted( m_command );
    }


    //-------------------------------------//
    //        Additional Internals         //
    //-------------------------------------//

    private boolean isAlreadyKnown(Slots<OrderNetworkMessage> store, OrderNetworkMessage msg)
    {
        OrderNetworkMessage curmsg = store.get( msg.getSender() );

        if( curmsg==null )
            return false;
        else if( curmsg.equals( msg ) )
            return true;
        else
            // TODO: Did someone send two conflicting messages? -> blacklist.
            throw new UnsupportedOperationException( msg.toString() );
    }


    private void addToStore(Slots<OrderNetworkMessage> store, OrderNetworkMessage msg, Votes<Data> votes, Data vote)
    {
        store.put( msg.getSender(), msg );
        addToVotes( votes, vote );
    }


    private PbftPrePrepare proposalFor(CommandContainer command)
    {
        return new PbftPrePrepare( m_repno, m_orderno, m_viewno, command );
    }


    // TODO: PRE-PREPARE/PREPARE = proposal; PREPARE/ACCEPT and COMMIT = acknowledgements
    private PbftPrepare prepareFor(CommandContainer command, ImmutableData proposal)
    {
        if( m_variant==PbftOrderVariant.AgreeOnFullCommand )
            return new PbftPrepare( m_repno, m_orderno, m_viewno, command );
        else
            return new PbftPrepare( m_repno, m_orderno, m_viewno, proposal );
    }


    private PbftCommit commitFor(CommandContainer command, ImmutableData proposal)
    {
        if( m_variant==PbftOrderVariant.AgreeOnFullCommand )
            return new PbftCommit( m_repno, m_orderno, m_viewno, command );
        else
            return new PbftCommit( m_repno, m_orderno, m_viewno, proposal );
    }


    private ImmutableData voteForProposal(CommandContainer command)
    {
        // In most configurations, the command was already digested for the verification.
        return m_mapper.digestMessage( command ).getMessageDigest();
    }


    private Data voteForAck(AbstractOrderHolderMessage ack)
    {
        return m_variant==PbftOrderVariant.AgreeOnFullCommand ?
                    ack.getCommand().getMessageDigest() : ack.getData();
    }


    private void addToVotes(Votes<Data> votes, Data vote)
    {
        votes.addVote( vote );

        assert votes.isUnanimous();
    }


    private boolean checkPrepared()
    {
        return checkState( m_pre_msgstore, m_pre_votes );
    }


    private boolean checkCommitted()
    {
        return checkState( m_com_msgstore, m_com_votes );
    }


    private boolean checkState(Slots<OrderNetworkMessage> store, Votes<Data> votes)
    {
        if( votes.getLeadingCount()<m_commitquorum )
            return false;

        // As usual, we need two things: The complete command and a quorum that supports it.
        if( m_variant!=PbftOrderVariant.AgreeOnFullCommand )
        {
            // In the current implementation, if the command is set, the PREPARE is set as well.
            if( m_command==null )
                return false;
        }
        else if( m_command==null )
        {
            // If we agree on complete commands, a quorum of COMMITs suffices.
            // TODO: Look up findFirst
            AbstractOrderHolderMessage ack = null;

            for( short i=0; i<store.capacity(); i++ )
                if( i!=m_proposer )
                    ack = (AbstractOrderHolderMessage) store.get( i );

            m_command  = ack.getCommand();
            m_proposal = voteForProposal( m_command );

            s_logger.debug( "{} learned {} from quorum", this, m_command );
        }

        // TODO: Confirm, that the vote does not differ from the proposal if such a case is possible.

        return true;
    }


    private void advanceState(State state, State expectedprev)
    {
        if( s_logger.isDebugEnabled() )
        {
            String skipped = m_state==expectedprev ? "" : ", jumped from " + m_state;

            s_logger.debug( "{} {} ({}, prop {}{}) at {}", this, state, m_command, m_proposer, skipped, m_cntxt );
        }

        m_state = state;
    }

}

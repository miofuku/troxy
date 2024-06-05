package reptor.replct.execute;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.chronos.ChronosAddress;
import reptor.chronos.ChronosTask;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.link.SelectorLink;
import reptor.distrbt.com.Message;
import reptor.distrbt.common.data.ImmutableData;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.MessageHandler;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.AgreementPeers;
import reptor.replct.agree.checkpoint.CheckpointMessage;
import reptor.replct.agree.checkpoint.CheckpointMessages;
import reptor.replct.agree.checkpoint.CheckpointMode;
import reptor.replct.agree.checkpoint.Checkpointing;
import reptor.replct.agree.checkpoint.CheckpointMessages.CheckpointCreated;
import reptor.replct.agree.checkpoint.CheckpointMessages.Snapshot;
import reptor.replct.agree.order.OrderMessages;
import reptor.replct.agree.order.OrderMessages.Command;
import reptor.replct.agree.order.OrderMessages.CommandContainer;
import reptor.replct.agree.order.OrderMessages.CommandOrdered;
import reptor.replct.common.WorkDistribution;
import reptor.replct.common.modules.PublicMasterActor;
import reptor.replct.execute.ExecutionExtensions.AppliedCheckpointObserver;
import reptor.replct.execute.ExecutionExtensions.ExecutedRequestObserver;
import reptor.replct.invoke.InvocationExtensions;
import reptor.replct.invoke.InvocationMessages;
import reptor.replct.invoke.ReplyMode;
import reptor.replct.invoke.ReplyModeStrategyInstance;
import reptor.replct.invoke.InvocationExtensions.ReplyContextFactory;
import reptor.replct.invoke.InvocationMessages.Request;
import reptor.replct.invoke.InvocationMessages.RequestExecuted;
import reptor.replct.invoke.bft.BFTInvocation;
import reptor.replct.service.ServiceInstance;


public class Executor extends PublicMasterActor implements MessageHandler<Message>
{

    private static final Logger s_logger = LoggerFactory.getLogger( Executor.class );

    private final short                         m_partno;
    private final ServiceInstance               m_application;
    private final Checkpointing         m_chkptprot;

    private final WorkDistribution          m_instdist;
    private final ReplyModeStrategyInstance     m_rmstrat;
    private final CheckpointMode                m_chkptmode;
    private final int                           m_chkptint;
    private final boolean                       m_combinedsnaps;

    private final ExecutedRequestObserver       m_exectreqobserver;
    private final ReplyContextFactory           m_repcntxtfactory;
    private final AppliedCheckpointObserver     m_appldchkptobserver;

    private final Map<Long, CommandOrdered>     m_ordered;
    private final Queue<Request>                m_unordered;
    private WorkDistribution.WorkIterator       m_institer;
    private long                                m_nextexecodr;
    private long                                m_nextexecloc;
    private long[]                              m_resmap;
    private boolean                             m_reqready;

    private long                                m_lastchkptodr;
    private Snapshot                            m_learnedsnapshot;

    private PushMessageSink<CheckpointMessage>  m_chkptlearner;
    private PushMessageSink<RequestExecuted>    m_execlearner;

    private byte                                repno;
    private boolean                             usecache;


    public Executor(SchedulerContext<? extends SelectorDomainContext> master, short partno, ChronosAddress[] remdoms,
                    ServiceInstance app,
                    BFTInvocation cliprot, Checkpointing chkptprot, WorkDistribution execinstdist,
                    ReplicaPeerGroup repgroup, int nclients,
                    ExecutionExtensions exectexts, InvocationExtensions cliexts)
    {
        super( master, remdoms );

        if( !app.createsFullCheckpoints() && chkptprot.getCheckpointMode().includes( CheckpointMode.APPLY ) )
            throw new UnsupportedOperationException( "Applying checkpoints is not supported by " + app );

        m_partno      = partno;
        m_application = app;
        m_chkptprot   = chkptprot;

        m_instdist      = execinstdist;
        m_rmstrat       = cliprot.getReplyModeStrategy().createInstance( repgroup );
        m_chkptmode     = chkptprot.getCheckpointMode();
        m_chkptint      = chkptprot.getCheckpointInterval();
        m_combinedsnaps = chkptprot.useCombinedSnapshots();

        m_ordered   = new HashMap<>();
        m_unordered = new ArrayDeque<>();
        m_reqready  = false;
        m_resmap    = new long[ repgroup.size() + nclients ];
        Arrays.fill( m_resmap, -1L );
        setExecutionNumber( 1 );

        m_lastchkptodr    = -1L;
        m_learnedsnapshot = null;

        m_exectreqobserver    = exectexts.getExecutionRequestObserver( m_partno );
        m_repcntxtfactory     = cliexts.getReplyContextFactory();
        m_appldchkptobserver = exectexts.getAppliedCheckpointExtension( m_partno );

        repno = repgroup.getReplicaNumber();
        usecache = cliprot.isUseCache();

    }


    private CheckpointMode checkpointMode()
    {
        return m_chkptmode;
    }


    private int checkpointInterval()
    {
        return m_chkptint;
    }


    private boolean useCombinedSnaptshots()
    {
        return m_combinedsnaps;
    }


    private void setExecutionNumber(long execloc)
    {
        m_institer    = m_instdist.getUnitIterator( m_partno, execloc );
        m_nextexecodr = m_institer.nextUnit();
        m_nextexecloc = execloc;
    }


    private void forwardExecutionNumber()
    {
        m_nextexecodr = m_institer.nextUnit();
        m_nextexecloc++;
    }


    @Override
    public String toString()
    {
        return String.format( "EXE%02d", m_partno );
    }


    @Override
    protected void processMessage(Message msg)
    {
        handleMessage( msg );
    }


    @Override
    public boolean handleMessage(Message msg)
    {
        switch( msg.getTypeID() )
        {
        case OrderMessages.COMMAND_ORDERED_ID:
            enqueueCommandOrdered( (CommandOrdered) msg );
            break;
        case InvocationMessages.REQUEST_ID:
            enqueueRequest( (Request) msg );
            break;
        case CheckpointMessages.SNAPSHOT_ID:
            enqueueSnapshot( (Snapshot) msg );
            break;
        default:
            throw new UnsupportedOperationException( msg.toString() );
        }

        return false;
    }


    private void enqueueCommandOrdered(CommandOrdered cmdordered)
    {
        if( cmdordered.getOrderNumber()<m_nextexecodr )
            return;

        assert !m_ordered.containsKey( cmdordered.getOrderNumber() );

        m_ordered.put( cmdordered.getOrderNumber(), cmdordered );
        if( cmdordered.getOrderNumber()==m_nextexecodr )
            m_reqready = true;
    }


    private void enqueueRequest(Request request)
    {
        // TODO: A client could sent an unlimited number of requests. How to deal with the client window? PULL?
        m_unordered.add( request );
        m_reqready = true;
    }


    private void enqueueSnapshot(Snapshot snapshot)
    {
        if( snapshot.getOrderNumber()<m_nextexecodr || m_learnedsnapshot!=null && m_learnedsnapshot.getOrderNumber()>snapshot.getOrderNumber() )
            return;

        m_learnedsnapshot = snapshot;
    }


    public void initPeers(AgreementPeers peers)
    {
        if( m_chkptprot.getMulticastCheckpointCreation() )
            m_chkptlearner = peers.createMulticastChannel( peers.getCheckpointShards(), domainAddress() );
        else
            m_chkptlearner = new SelectorLink<>( peers.createChannels( peers.getCheckpointShards(), domainAddress() ),
                                                    x -> peers.getInternalCheckpointCoordinator( x.getOrderNumber() ) );

        m_execlearner = new SelectorLink<>( peers.createChannels( peers.getClientShards(), domainAddress() ),
                                                x -> peers.getClientShard( x.getRequest().getSender() ) );
    }


    @Override
    protected void executeSubjects()
    {
        if( m_learnedsnapshot!=null )
            processLearnedSnapshot();   // This can change m_reqready.

        if( m_reqready )
            processCommands();
    }


    private void processCommands()
    {
        CommandOrdered cmdordered;

        while( ( cmdordered = m_ordered.remove( m_nextexecodr ) )!=null )
        {
            processRequests( cmdordered.getCommand(), m_nextexecodr, m_nextexecloc, cmdordered.getProposer() );
            forwardExecutionNumber();
        }

        m_reqready = false;

        if( !m_unordered.isEmpty() )
        {
            Request request;

            while( ( request=m_unordered.poll() )!=null )
                processReadOnlyRequest( request );
        }
    }


    private void processRequests(CommandContainer container, long execodr, long execloc, short proposer)
    {
        s_logger.debug( "{} process {} ({})", this, container, execodr );

        // Create checkpoint
        //  If there are multiple executors, successive sequence numbers for single partitions exhibit gaps.
        //  In more extreme configurations, these gaps can be larger then the checkpoint interval,
        //  which means that more than one checkpoint has to be created.
        ImmutableData svcstate = null;
        while( execodr > m_lastchkptodr+checkpointInterval() )
            svcstate = createCheckpoint( m_lastchkptodr+checkpointInterval(), svcstate );

        m_rmstrat.initOrdered( execodr, execloc, proposer );

        for( Command cmd : container.getCommands() )
            processSingleRequest( (Request) cmd, ((Request) cmd).useReadOnlyOptimization() );

        m_exectreqobserver.requestExecuted( container );

        if( execodr == m_lastchkptodr+checkpointInterval() )
            createCheckpoint( execodr, null );

        m_exectreqobserver.requestExecuted( container );
    }


    private void processReadOnlyRequest(Request request)
    {
        s_logger.debug( "{} process read-only request {}", this, request );

        m_rmstrat.initUnordered( request.getSender(), request.getNumber() );

        if (usecache && request.getSender()%3!=repno)
            return;

        processSingleRequest( request, request.useReadOnlyOptimization() );
    }


    private void processSingleRequest(Request request, boolean isspec)
    {
        if( !ensureExactlyOnce( request.getSender(), request.getNumber(), isspec ) )
        {
            s_logger.debug( "{} request {} has been already executed", this, request );
            return;
        }

        ImmutableData result = m_application.processCommand( request.getSender(), request.getCommand(), isspec );

        ReplyMode repmode  = m_rmstrat.replyMode( request, result );
        Object    extcntxt = m_repcntxtfactory.createReplyContext( request );

        m_execlearner.enqueueMessage( new RequestExecuted( request, isspec, result, repmode, extcntxt ) );
    }


    private boolean ensureExactlyOnce(short sender, long reqno, boolean isspec)
    {
        if( !isspec && reqno<=m_resmap[ sender ] )
            return false;

        if( !isspec )
            m_resmap[ sender ] = reqno;

        return true;
    }


    private void processLearnedSnapshot()
    {
        long orderno = m_learnedsnapshot.getOrderNumber();

        assert( m_learnedsnapshot!=null && orderno>=m_nextexecodr );

        s_logger.debug( "{} apply snapshot {}", this, orderno );

        if( useCombinedSnaptshots() )
            m_application.applyCheckpoint( m_learnedsnapshot.getServiceStatePartition( m_partno ) );
        else
            m_application.applyCheckpoint( m_learnedsnapshot.getServiceState() );

        if( checkpointMode().includes( CheckpointMode.NODE_PROGRESS ) )
            m_resmap = m_learnedsnapshot.getResultMap();

        long prevexecno = m_nextexecodr;

        setExecutionNumber( m_instdist.getSlotForUnit( m_partno, orderno ) );
        if( m_nextexecodr==orderno )
            forwardExecutionNumber();
        m_reqready = m_ordered.containsKey( m_nextexecodr );

        for( Iterator<Long> iterator = m_ordered.keySet().iterator(); iterator.hasNext(); )
        {
            if( iterator.next()<m_nextexecodr )
                iterator.remove();
        }

        reachedCheckpoint( orderno );

        m_appldchkptobserver.checkpointApplied( m_learnedsnapshot, prevexecno, m_nextexecodr );

        m_learnedsnapshot = null;
    }


    private ImmutableData createCheckpoint(long execodr, ImmutableData svcstate)
    {
        s_logger.debug( "{} create checkpoint for agreement sequence number {}", this, execodr );

        if( svcstate==null )
            svcstate = m_application.createCheckpoint();

        boolean isfull = m_application.createsFullCheckpoints();

        long[] rm = checkpointMode().includes( CheckpointMode.NODE_PROGRESS ) ? m_resmap.clone() : null;

        // TODO: When we had a separated internal checkpoint protocol, the replication protocol could determine,
        //       what data is required. It could also create hashes instead of cloning node progresses.
        CheckpointMessage chkptcreated = new CheckpointCreated( m_partno, execodr, svcstate, rm, isfull );

        m_chkptlearner.enqueueMessage( chkptcreated );

        reachedCheckpoint( execodr );

        return svcstate;
    }


    private void reachedCheckpoint(long orderno)
    {
        m_lastchkptodr = orderno;
    }


    @Override
    public void taskReady(ChronosTask task)
    {

    }

}

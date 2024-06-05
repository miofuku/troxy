package refit.modules.worker;

import refit.modules.worker.WorkerMessages.ClientCertificationJob;
import refit.modules.worker.WorkerMessages.ReplicaCertificationJob;
import refit.modules.worker.WorkerMessages.SendReply;
import reptor.chronos.ChronosTask;
import reptor.chronos.SchedulerContext;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.MessageHandler;
import reptor.replct.common.modules.PublicMasterActor;
import reptor.replct.connect.ConnectionCertifierCollection;
import reptor.replct.invoke.InvocationExtensions;
import reptor.replct.invoke.InvocationExtensions.FinishedReplyObserver;


// TODO: All Jobs should be carried out be dedicated processors. Currently, the worker is still needed,
//       because worker can be executed in an own thread and processors require portals.
public class Worker extends PublicMasterActor implements MessageHandler<Message>
{

    private final int                     m_workerno;
    private final MessageMapper           m_mapper;
    private final GroupConnectionCertifier   m_repcon = null;
    private final ConnectionCertifierCollection m_clicons = null;
    private final FinishedReplyObserver   m_finrepobserver;


    public Worker(SchedulerContext<? extends SelectorDomainContext> master, short replicaid, int workerno,
                  InvocationExtensions cliexts, MessageMapper mapper)
    {
        super( master, null );

        m_workerno       = workerno;
        m_mapper         = mapper;
//        m_repcon         = confac.createStandardReplicaGroupCertifier();
//        m_clicons        = confac.createReplicaToClientCertifiers();
        m_finrepobserver = cliexts.getFinishedReplyObserver( m_workerno );
    }


    @Override
    public String toString()
    {
        return String.format( "WRK%02d", m_workerno );
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
        case WorkerMessages.REPLICA_CERTIFICATION_ID:
            ((ReplicaCertificationJob) msg).execute( m_mapper, m_repcon );
            break;
        case WorkerMessages.CLIENT_CERTIFICATION_ID:
            ((ClientCertificationJob) msg).execute( m_mapper, m_clicons );
            break;
        case WorkerMessages.SEND_REPLY_ID:
            ((SendReply) msg).execute( m_mapper, m_clicons, m_finrepobserver );
            break;
        default:
            throw new IllegalStateException( msg.toString() );
        }

        return false;
    }


    @Override
    protected void executeSubjects()
    {

    }


    @Override
    public void taskReady(ChronosTask task)
    {

    }

}

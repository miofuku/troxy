package reptor.replct.execute;

import reptor.replct.agree.checkpoint.CheckpointMessages.Snapshot;
import reptor.replct.agree.order.OrderMessages.CommandContainer;


// -- That's just a trial balloon
public class ExecutionExtensions
{
    // -- Extension point type
    @FunctionalInterface
    public interface ExecutedRequestObserver
    {
        void requestExecuted(CommandContainer command);


        static final ExecutedRequestObserver EMPTY = new ExecutedRequestObserver()
                                                   {
                                                       @Override
                                                       public void requestExecuted(CommandContainer command)
                                                       {
                                                       }
                                                   };
    }

    // -- Extension
    @FunctionalInterface
    public interface ExecutedRequestExtension
    {
        ExecutedRequestObserver getExecutionRequestObserver(int exectid);
    }

    @FunctionalInterface
    public interface AppliedCheckpointObserver
    {
        void checkpointApplied(Snapshot checkpoint, long oldseqno, long newseqno);


        static final AppliedCheckpointObserver EMPTY = new AppliedCheckpointObserver()
                                                     {
                                                         @Override
                                                         public void checkpointApplied(Snapshot checkpoint,
                                                                 long oldseqno, long newseqno)
                                                         {
                                                         }
                                                     };
    }

    @FunctionalInterface
    public interface AppliedCheckpointExtension
    {
        AppliedCheckpointObserver getAppliedCheckpointObserver(int exectid);
    }


    private final ExecutedRequestExtension   m_exectreqext;
    private final AppliedCheckpointExtension m_appldchkpntext;


    public ExecutionExtensions(ExecutedRequestExtension exectreqext, AppliedCheckpointExtension appldchkpntext)
    {
        m_exectreqext = exectreqext;
        m_appldchkpntext = appldchkpntext;
    }


    public ExecutedRequestObserver getExecutionRequestObserver(int exectid)
    {
        return m_exectreqext != null ? m_exectreqext.getExecutionRequestObserver( exectid )
                : ExecutedRequestObserver.EMPTY;
    }


    public AppliedCheckpointObserver getAppliedCheckpointExtension(int exectid)
    {
        return m_appldchkpntext != null ? m_appldchkpntext.getAppliedCheckpointObserver( exectid )
                : AppliedCheckpointObserver.EMPTY;
    }
}

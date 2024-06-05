package reptor.bench.measure;

import java.util.function.LongConsumer;

import reptor.replct.agree.checkpoint.CheckpointMessages.Snapshot;
import reptor.replct.execute.ExecutionExtensions;


public class AppliedCheckpointsMeter extends TasksMeter implements ExecutionExtensions.AppliedCheckpointExtension
{
    private static class CheckpointObserver implements ExecutionExtensions.AppliedCheckpointObserver
    {
        private final int          m_nstages;
        private final LongConsumer m_consumer;


        public CheckpointObserver(int nstages, LongConsumer consumer)
        {
            m_nstages = nstages;
            m_consumer = consumer;
        }


        @Override
        public void checkpointApplied(Snapshot checkpoint, long oldseqno, long newseqno)
        {
            long skippedinsts = (newseqno - oldseqno) / m_nstages;

            m_consumer.accept( skippedinsts );
        }
    }


    private final CheckpointObserver[] m_ckpobs;


    public AppliedCheckpointsMeter(int nstages, long durwarm, long durrun, long durcool, boolean withhis)
    {
        super( nstages, 1, durwarm, durrun, durcool, withhis );

        if( !isActive() )
            m_ckpobs = null;
        else
        {
            m_ckpobs = new CheckpointObserver[nstages];
            for( int i = 0; i < m_ckpobs.length; i++ )
                m_ckpobs[i] = new CheckpointObserver( nstages, getTaskConsumer( i, 0 ) );
        }
    }


    @Override
    public ExecutionExtensions.AppliedCheckpointObserver getAppliedCheckpointObserver(int exectid)
    {
        return m_ckpobs != null ? m_ckpobs[exectid] : ExecutionExtensions.AppliedCheckpointObserver.EMPTY;
    }
}

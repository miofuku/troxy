package reptor.replct.invoke;

import reptor.distrbt.common.data.ImmutableData;
import reptor.replct.common.WorkDistribution;
import reptor.replct.invoke.InvocationMessages.Request;


public class ReplyModeStrategies
{

    public static final int NO_REPLY_HASHING = -1;


    public abstract static class AbstractReplyModeStrategy implements ReplyModeStrategy
    {
        protected final int m_hashthreshold;

        public AbstractReplyModeStrategy(int hashthreshold)
        {
            m_hashthreshold = hashthreshold;
        }

        @Override
        public String toString()
        {
            String add  = nameAddition();
            String hash = m_hashthreshold<0 ? "full" : "hashed>=" + m_hashthreshold;

            return this.getClass().getSimpleName() + "(" + (add==null ? "" : add + ",") + hash + ")";
        }

        @Override
        public boolean getUseHashedReplies()
        {
            return m_hashthreshold>=0;
        }

        protected String nameAddition()
        {
            return null;
        }
    }


    public static abstract class AbstractReplyModeStrategyInstance implements ReplyModeStrategyInstance
    {
        protected final int     m_hashthreshold;
        protected final short   m_repno;
        protected final short   m_nreplicas;

        protected ReplyMode     m_instmode = null;

        public AbstractReplyModeStrategyInstance(int hashthreshold, short repno, short nreplicas)
        {
            m_hashthreshold = hashthreshold;
            m_repno         = repno;
            m_nreplicas     = nreplicas;
        }

        @Override
        public ReplyMode replyMode(Request request, ImmutableData result)
        {
            if( request.isPanic() )
                return ReplyMode.Full;
            else if( m_instmode==ReplyMode.Hashed )
                return result.size()>=m_hashthreshold ? ReplyMode.Hashed : ReplyMode.Full;
            else
                return m_instmode;
        }

        protected ReplyMode hashMode(long hashidx)
        {
            return m_hashthreshold<0 || ( hashidx % m_nreplicas )==m_repno ? ReplyMode.Full : ReplyMode.Hashed;
        }
    }


    public static class AllReply extends AbstractReplyModeStrategy
    {
        public AllReply(int hashthreshold)
        {
            super( hashthreshold );
        }

        @Override
        public ReplyModeStrategyInstance createInstance(short repno, short nreplicas, short nfaults)
        {
            return new AllReplyInstance( m_hashthreshold, repno, nreplicas );
        }
    }


    public static class AllReplyInstance extends AbstractReplyModeStrategyInstance
    {
        public AllReplyInstance(int hashthreshold, short repno, short nreplicas)
        {
            super( hashthreshold, repno, nreplicas );
        }

        @Override
        public void initUnordered(short sender, long invno)
        {
            m_instmode = hashMode( sender+invno );
        }

       @Override
        public void initOrdered(long orderno, long locodrno, short leader)
        {
            m_instmode = hashMode( orderno );
        }
    }


    public static class AllButXReply extends AbstractReplyModeStrategy
    {
        private final WorkDistribution m_noreplydist;
        private final short            m_nnoreply;

        public AllButXReply(int hashthreshold, WorkDistribution noreplydist, short nnoreply)
        {
            super( hashthreshold );

            m_noreplydist = noreplydist;
            m_nnoreply    = nnoreply;
        }

        @Override
        public ReplyModeStrategyInstance createInstance(short repno, short nreplicas, short nfaults)
        {
            return new AllButXReplyInstance( m_hashthreshold, m_noreplydist, m_nnoreply, repno, nreplicas );
        }

        @Override
        protected String nameAddition()
        {
            return Short.toString( m_nnoreply );
        }
    }


    public static class AllButXReplyInstance extends AbstractReplyModeStrategyInstance
    {
        private final WorkDistribution m_noreplydist;
        private final short            m_nnoreply;

        public AllButXReplyInstance(int hashthreshold, WorkDistribution noreplydist, short nnoreply, short repno, short nreplicas)
        {
            super( hashthreshold, repno, nreplicas );

            m_noreplydist = noreplydist;
            m_nnoreply    = nnoreply;
        }

        @Override
        public void initUnordered(short sender, long invno)
        {
            init( sender+invno );
        }

        @Override
        public void initOrdered(long orderno, long locodrno, short leader)
        {
            init( locodrno );
        }

        private void init(long hashidx)
        {
            int noreply = m_noreplydist.getStageForUnit( hashidx );
            int d       = m_repno>=noreply ? m_repno-noreply : m_repno+m_nreplicas-noreply;

            m_instmode = d<m_nnoreply ? ReplyMode.None : hashMode( noreply+m_nnoreply );
        }
    }


    public static class AllButLeaderReply extends AbstractReplyModeStrategy
    {
        private final WorkDistribution m_fulldist;

        public AllButLeaderReply(int hashthreshold, WorkDistribution fulldist)
        {
            super( hashthreshold );

            m_fulldist = fulldist;
        }

        @Override
        public AllButLeaderReplyInstance createInstance(short repno, short nreplicas, short nfaults)
        {
            return new AllButLeaderReplyInstance( m_hashthreshold, m_fulldist, repno, nreplicas );
        }
    }


    public static class AllButLeaderReplyInstance extends AbstractReplyModeStrategyInstance
    {
        private final WorkDistribution m_fulldist;

        public AllButLeaderReplyInstance(int hashthreshold, WorkDistribution fulldist, short repno, short nreplicas)
        {
            super( hashthreshold, repno, nreplicas );

            m_fulldist = fulldist;
        }

        @Override
        public void initUnordered(short sender, long invno)
        {
            m_instmode = hashMode( sender+invno );
        }

        @Override
        public void initOrdered(long orderno, long locodrno, short leader)
        {
            if( m_repno==leader )
                m_instmode = ReplyMode.None;
            else if( m_hashthreshold<0 )
                m_instmode = ReplyMode.Full;
            else
            {
                int full = leader + 1 + m_fulldist.getStageForUnit( locodrno );

                if( full>=m_nreplicas )
                    full -= m_nreplicas;

                m_instmode = m_repno==full ? ReplyMode.Full : ReplyMode.Hashed;
            }
        }
    }

}

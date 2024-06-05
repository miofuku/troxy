package refit.hybstero.checkpoint;

import java.util.Map;

import refit.common.BallotBox;
import refit.hybstero.checkpoint.HybsterCheckpointMessages.HybsterCheckpoint;
import refit.hybstero.checkpoint.HybsterCheckpointMessages.HybsterCheckpointNetworkMessage;
import refit.hybstero.checkpoint.HybsterCheckpointMessages.HybsterTCVerification;
import reptor.chronos.Orphic;
import reptor.distrbt.certify.VerifierGroup;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.ImmutableData;
import reptor.jlib.collect.SlotsMap;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.common.checkpoint.CheckpointNetworkMessage;


public class HybsterCheckpointInstanceShardCertifier implements Orphic
{

    private final int CHECKPOINT_THRESHOLD      = 999; //Config.REGULAR_CHECKPOINT_STABILITY_THRESHOLD;
    private final int TC_VERIFICATION_THRESHOLD = 999; //Config.WEAK_QUORUM-1;

    // Shared state
    private final MessageMapper      m_mapper;
    private final VerifierGroup m_reptcverifs;
    private final VerifierGroup m_reptmverifs;

    // Configuration-dependent state
    private short m_repno   = -1;
    private short m_shardno = -1;
    // TODO: We don't need the view number here but the number of the group composition.
//    private int   m_viewno     = -1;

    // Internal state for a certification job
    private long  m_orderno = -1;

    private final BallotBox<Integer, Data, HybsterCheckpoint> m_chkpts;
    private final SlotsMap<HybsterTCVerification>       m_tcacks;

    private HybsterCheckpoint m_ownchkpt;
    private Data              m_owncert;
    private Data              m_ownvote;

    // Changing external state
    private boolean           m_isstable;
    private HybsterCheckpointCertificate m_cert;


    public HybsterCheckpointInstanceShardCertifier(MessageMapper mapper, VerifierGroup reptcverifs, VerifierGroup reptmverifs,
                                                   ReplicaPeerGroup grpconf)
    {
        m_mapper      = mapper;
        m_reptcverifs = reptcverifs;
        m_reptmverifs = reptmverifs;
        m_chkpts = new BallotBox<>( CHECKPOINT_THRESHOLD );
        m_tcacks = new SlotsMap<>( grpconf.size() );
    }


    @Override
    public String toString()
    {
        return "HYBCCERT[" + m_orderno + "]";
    }


    public HybsterCheckpointInstanceShardCertifier initConfig(short repno, short shardno)
    {
        clearJob();

        m_repno   = repno;
        m_shardno = shardno;

        return this;
    }

    public HybsterCheckpointInstanceShardCertifier initJob(long orderno)
    {
        clearJob();

        m_orderno = orderno;

        return this;
    }

    private void clearJob()
    {
        m_orderno  = -1;
        m_ownchkpt = null;
        m_owncert  = null;
        m_ownvote  = null;
        m_isstable = false;
        m_cert     = null;
        m_chkpts.clear();
        m_tcacks.clear();
    }


    // If something is wrong, it is propagated via exception. The return value here should be used like
    // for message handlers. A true means, that the external state could have changed.
    public boolean addCheckpoint(HybsterCheckpoint chkpt, boolean checkedcontent)
    {
        if( !checkMessage( chkpt, checkedcontent, m_reptcverifs, m_chkpts ) )
            return false;

        ImmutableData vote = chkpt.getServiceState()==null ? ImmutableData.EMPTY : chkpt.getServiceState();
        m_chkpts.add( (int) chkpt.getSender(), vote, chkpt );

        // m_ownchkpt is null. Otherwise, the checkMessage above would had caught this message.
        if( chkpt.getSender()==m_repno )
        {
            m_ownchkpt = chkpt;
            // TODO: It should be possible to compare certificates without copying the underlying array.
            m_owncert  = chkpt.getCertificateData();
            m_ownvote  = vote;
        }

        return checkStable();
    }


    public boolean addTCVerification(HybsterTCVerification tcack, boolean checkedcontent)
    {
        if( !checkMessage( tcack, checkedcontent, m_reptmverifs, m_tcacks ) )
            return false;

        if( m_ownchkpt==null )
            throw new UnsupportedOperationException();

        if( !m_owncert.equals( tcack.getCheckpointCertificateData() ) )
            throw new UnsupportedOperationException();

        m_tcacks.put( (int) tcack.getSender(), tcack );

        return checkStable();
    }


    public boolean isStable()
    {
        return m_isstable;
    }

    public HybsterCheckpointCertificate getCertificate()
    {
        if( m_cert==null && m_isstable )
            m_cert = createCertificate();

        return m_cert;
    }

//    public int getViewNumber()
//    {
//        return m_viewno;
//    }

    public long getOrderNumber()
    {
        return m_orderno;
    }


    private <M extends HybsterCheckpointNetworkMessage>
            boolean checkMessage(M msg, boolean checkedcontent, VerifierGroup verifs, Map<Integer, M> ballots)
    {
        if( !matchesInstance( msg ) )
            throw new UnsupportedOperationException();

        if( ballots.containsKey( (int) msg.getSender() ) )
        {
            if( msg.equals( ballots.get( (int) msg.getSender() ) ) )
                return false;
            else
                throw new UnsupportedOperationException();
        }

        if( !checkedcontent )
            verifyMessage( msg, verifs );

        return true;
    }

    private boolean matchesInstance(HybsterCheckpointNetworkMessage msg)
    {
        return msg.getOrderNumber()==m_orderno && msg.getShardNumber()==m_shardno;
    }

    private void verifyMessage(CheckpointNetworkMessage msg, VerifierGroup verifs)
    {
        // TODO: How do we verify own messages when we receive them in view changes?
        //       Do we have to compare it with our local copy?
        if( msg.getSender()!=m_repno )
            m_mapper.verifyMessage( msg, verifs );
    }


    private boolean checkStable()
    {
        if( m_isstable || m_chkpts.getDecision()==null || m_tcacks.size()<TC_VERIFICATION_THRESHOLD )
            return false;
        else if( !m_chkpts.getDecision().equals( m_ownvote ) )
            throw new UnsupportedOperationException();
        else
        {
            m_isstable = true;
            return true;
        }
    }


    private HybsterCheckpointCertificate createCertificate()
    {
        assert m_isstable;

        HybsterCheckpoint[] chkptacks = m_chkpts.getDecidingBallots().stream()
                                                .filter( b -> b!=m_ownchkpt )
                                                .limit( CHECKPOINT_THRESHOLD-1 )
                                                .toArray( HybsterCheckpoint[]::new );

        HybsterTCVerification[] tcacks = m_tcacks.values().stream()
                                                 .limit( TC_VERIFICATION_THRESHOLD )
                                                 .toArray( HybsterTCVerification[]::new );

        return new HybsterCheckpointCertificate( m_ownchkpt, chkptacks, tcacks );
    }

}

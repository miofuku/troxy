package refit.hybstero.checkpoint;

import refit.hybstero.checkpoint.HybsterCheckpointMessages.HybsterCheckpoint;
import refit.hybstero.checkpoint.HybsterCheckpointMessages.HybsterTCVerification;
import reptor.chronos.ImmutableObject;


public class HybsterCheckpointCertificate implements ImmutableObject
{

    private final HybsterCheckpoint       m_ownchkpt;
    private final HybsterCheckpoint[]     m_chkptacks;
    private final HybsterTCVerification[] m_tcacks;

    public HybsterCheckpointCertificate(HybsterCheckpoint ownchkpt, HybsterCheckpoint[] chkptacks,
                                        HybsterTCVerification[] tcacks)
    {
        m_ownchkpt  = ownchkpt;
        m_chkptacks = chkptacks;
        m_tcacks    = tcacks;
    }

    public long getOrderNumber()
    {
        return m_ownchkpt.getOrderNumber();
    }

    public short getShardNumber()
    {
        return m_ownchkpt.getShardNumber();
    }

    public HybsterCheckpoint getOwnCheckpoint()
    {
        return m_ownchkpt;
    }

    public HybsterCheckpoint[] getOtherCheckpoints()
    {
        return m_chkptacks;
    }

    public HybsterTCVerification[] getTCVerifications()
    {
        return m_tcacks;
    }

}

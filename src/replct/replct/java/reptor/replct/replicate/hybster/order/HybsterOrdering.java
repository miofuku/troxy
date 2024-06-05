package reptor.replct.replicate.hybster.order;

import reptor.distrbt.com.NetworkMessageRegistry.NetworkMessageRegistryBuilder;
import reptor.replct.agree.order.Ordering;
import reptor.replct.common.settings.SettingsReader;
import reptor.replct.replicate.hybster.Hybster;
import reptor.replct.replicate.hybster.order.HybsterOrderMessages.HybsterCommit;
import reptor.replct.replicate.hybster.order.HybsterOrderMessages.HybsterPrepare;


public class HybsterOrdering extends Ordering
{

    private HybsterOrderVariant m_variant = HybsterOrderVariant.FullProposalHashedVotes;


    public HybsterOrdering(byte nreplicas, byte nfaults, int chkptint)
    {
        super( nreplicas, chkptint, Hybster.INSTANCE.getDefaultQuorum().upperQuorumSize( nreplicas, nfaults ) );
    }


    @Override
    public HybsterOrdering load(SettingsReader reader)
    {
        super.load( reader );

        return this;
    }


    @Override
    public HybsterOrdering activate()
    {
        super.activate();

        return this;
    }


    @Override
    public void registerMessages(NetworkMessageRegistryBuilder msgreg)
    {
        super.registerMessages( msgreg );

        msgreg.addMessageType( HybsterOrderMessages.HYBSTER_PREPARE_ID, HybsterPrepare::new );

        switch( m_variant )
        {
        case FullProposalCertificateVotes:
        case FullProposalHashedVotes:
            msgreg.addMessageType( HybsterOrderMessages.HYBSTER_COMMIT_ID, HybsterCommit::readDataVersionFrom );
            break;
        case AgreeOnFullCommand:
            msgreg.addMessageType( HybsterOrderMessages.HYBSTER_COMMIT_ID, HybsterCommit::readCommandVersionFrom );
            break;
        }
    }


    public HybsterOrderVariant getVariant()
    {
        return m_variant;
    }

}

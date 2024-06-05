package reptor.replct.replicate.pbft.order;

import reptor.distrbt.com.NetworkMessageRegistry.NetworkMessageRegistryBuilder;
import reptor.replct.agree.order.Ordering;
import reptor.replct.common.settings.SettingsReader;
import reptor.replct.replicate.pbft.Pbft;
import reptor.replct.replicate.pbft.order.PbftOrderMessages.PbftCommit;
import reptor.replct.replicate.pbft.order.PbftOrderMessages.PbftPrePrepare;
import reptor.replct.replicate.pbft.order.PbftOrderMessages.PbftPrepare;


public class PbftOrdering extends Ordering
{

    private PbftOrderVariant    m_variant = PbftOrderVariant.FullProposalHashedVotes;


    public PbftOrdering(byte nreplicas, byte nfaults, int chkptint)
    {
        super( nreplicas, chkptint, Pbft.INSTANCE.getDefaultQuorum().upperQuorumSize( nreplicas, nfaults ) );
    }


    @Override
    public PbftOrdering load(SettingsReader reader)
    {
        super.load( reader );

        return this;
    }


    @Override
    public PbftOrdering activate()
    {
        super.activate();

        return this;
    }


    @Override
    public void registerMessages(NetworkMessageRegistryBuilder msgreg)
    {
        super.registerMessages( msgreg );

        msgreg.addMessageType( PbftOrderMessages.PBFT_PREPREPARE_ID, PbftPrePrepare::new );

        if( m_variant==PbftOrderVariant.AgreeOnFullCommand )
        {
            msgreg.addMessageType( PbftOrderMessages.PBFT_PREPARE_ID, PbftPrepare::readCommandVersionFrom );
            msgreg.addMessageType( PbftOrderMessages.PBFT_COMMIT_ID, PbftCommit::readCommandVersionFrom );
        }
        else
        {
            msgreg.addMessageType( PbftOrderMessages.PBFT_PREPARE_ID, PbftPrepare::readDataVersionFrom );
            msgreg.addMessageType( PbftOrderMessages.PBFT_COMMIT_ID, PbftCommit::readDataVersionFrom );
        }
    }


    public PbftOrderVariant getVariant()
    {
        return m_variant;
    }

}

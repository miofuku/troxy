package refit.pbfto.view;

import refit.pbfto.order.PBFTOrderInstanceCertifier;
import refit.pbfto.order.PBFTOrderMessages;
import refit.pbfto.order.PBFTOrderMessages.PBFTCommit;
import refit.pbfto.order.PBFTOrderMessages.PBFTPrePrepare;
import refit.pbfto.order.PBFTOrderMessages.PBFTPrepare;
import refit.pbfto.view.PBFTViewChangeMessages.PBFTViewChange;
import reptor.chronos.Orphic;
import reptor.distrbt.certify.VerifierGroup;
import reptor.distrbt.com.MessageMapper;
import reptor.replct.agree.common.checkpoint.CheckpointMessages.Checkpoint;
import reptor.replct.agree.common.order.OrderNetworkMessage;


// Certifies the correctness of one shard of a view change message from a replica.
public class PBFTViewChangeShardCertifier implements Orphic
{

    private final MessageMapper    m_mapper;
    private final VerifierGroup  m_defverif;
    private final VerifierGroup  m_strverif;
    private final VerifierGroup  m_cliverif;
    private final byte             m_locrepno;


    public PBFTViewChangeShardCertifier(MessageMapper mapper, VerifierGroup defverif, VerifierGroup strverif,
                                        VerifierGroup cliverif, byte locrepno)
    {
        m_mapper   = mapper;
        m_defverif = defverif;
        m_strverif = strverif;
        m_cliverif = cliverif;
        m_locrepno = locrepno;
    }


    public boolean addViewChange(PBFTViewChange vc)
    {
        if( vc.areInnerMessagesValid()!=null )
        {
            if( vc.areInnerMessagesValid()==false )
                throw new UnsupportedOperationException();

            return true;
        }

        // If a replica receives its own message, it has to verify that it has not been tampered with.
        // Messages locally created and sent are marked as verified, thus this call is cheap.
        m_mapper.verifyMessage( vc, m_strverif );

        // TODO: We don't need to verify the content of message we've already seen and that we received again.

        if( vc.getCheckpointCertificate().length>0 )
            verifyCheckpointCertificate( vc.getCheckpointCertificate() );

        verifyPreparedSet( vc.getViewNumber(), vc.getPrepareCertificates() );

        vc.setInnerMessagesValid( true );
        return true;
    }


    private void verifyCheckpointCertificate(Checkpoint[] chkptcert)
    {
        // TODO: verify checkpoint certificate
    }


    private void verifyPreparedSet(int viewno, OrderNetworkMessage[][] prepset)
    {
        long lastorderno = -1;

        // TODO: Method that validates complete certificates.
        // TODO: Maximum size for the set?
        // TODO: We would need the complete proposal if we proposed hashes.
        PBFTOrderInstanceCertifier cert =
                new PBFTOrderInstanceCertifier( m_locrepno, true, m_mapper, m_defverif, m_cliverif );

        for( OrderNetworkMessage[] pp : prepset )
        {
            // TODO: Real threshold
            //if( pp.length!=<threshold> )
            // TODO: Currently, the first message has to be the PREPREPARE
            long iorderno = pp[ 0 ].getOrderNumber();
            int  iviewno  = pp[ 0 ].getViewNumber();

            if( iviewno>=viewno )
                throw new UnsupportedOperationException();

            // TODO: Do we know which instances can be contained? (Currently, yes.)
            if( iorderno<=lastorderno )
                throw new UnsupportedOperationException();
            else
                lastorderno = iorderno;

            cert.init( iorderno, iviewno  );

            for( OrderNetworkMessage m : pp )
            {
                switch( m.getTypeID() )
                {
                case PBFTOrderMessages.PBFT_PRE_PREPARE_ID:
                    cert.addPrePrepare( (PBFTPrePrepare) m );
                    break;
                case PBFTOrderMessages.PBFT_PREPARE_ID:
                    cert.addPrepare( (PBFTPrepare) m );
                    break;
                case PBFTOrderMessages.PBFT_COMMIT_ID:
                    cert.addCommit( (PBFTCommit) m );
                    break;
                default:
                    throw new UnsupportedOperationException();
                }

                if( cert.isPrepared() || cert.isCommitted() )
                    break;
            }

            if( !cert.isPrepared() && !cert.isCommitted() )
                throw new UnsupportedOperationException();
        }
    }
}


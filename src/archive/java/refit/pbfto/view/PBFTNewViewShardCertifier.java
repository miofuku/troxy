package refit.pbfto.view;

import refit.pbfto.view.PBFTViewChangeMessages.PBFTNewView;
import reptor.chronos.Orphic;
import reptor.distrbt.certify.VerifierGroup;
import reptor.distrbt.com.MessageMapper;


// Certifies the correctness of one shard of a new view message from a replica.
public class PBFTNewViewShardCertifier implements Orphic
{

    private final MessageMapper      m_mapper;
    private final VerifierGroup m_strverif;


    public PBFTNewViewShardCertifier(MessageMapper mapper, VerifierGroup strverif)
    {
        m_mapper   = mapper;
        m_strverif = strverif;
    }


    public boolean addNewView(PBFTNewView nv)
    {
        if( nv.areInnerMessagesValid()!=null )
        {
            if( nv.areInnerMessagesValid()==false )
                throw new UnsupportedOperationException();

            return true;
        }

        // If a replica receives its own message, it has to verify that it has not been tampered with.
        // Messages locally created and sent are marked as verified, thus this call is cheap.
        m_mapper.verifyMessage( nv, m_strverif );

        // TODO: We don't need to verify the content of message we've already seen and that we received again.
        //       (for instance contained VIEW-CHANGEs)
        // TODO: Must contain the view change of the coordinator.

        nv.setInnerMessagesValid( true );
        return true;
    }
}

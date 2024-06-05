package reptor.replct.replicate.pbft.view;

import reptor.distrbt.com.NetworkMessageRegistry.NetworkMessageRegistryBuilder;
import reptor.replct.NetworkProtocolComponent;
import reptor.replct.replicate.pbft.view.PbftViewChangeMessages.PbftNewView;
import reptor.replct.replicate.pbft.view.PbftViewChangeMessages.PbftViewChange;


public class PbftViews implements NetworkProtocolComponent
{

    @Override
    public void registerMessages(NetworkMessageRegistryBuilder msgreg)
    {
        msgreg.addMessageType( PbftViewChangeMessages.PBFT_VIEW_CHANGE_ID, PbftViewChange::new )
              .addMessageType( PbftViewChangeMessages.PBFT_NEW_VIEW_ID, PbftNewView::new );
    }
}

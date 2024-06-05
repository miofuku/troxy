package refit.protocols.statetransfer;

import java.util.Collection;

import distrbt.com.transmit.MessageTransmitter;
import reptor.chronos.Orphic;
import reptor.distrbt.com.NetworkMessage;


public class StateTransferProtocol
{

    //-------------------------------------//
    //               Handler               //
    //-------------------------------------//

    public static class StateTransferHandler implements Orphic
    {

        private final MessageTransmitter m_transmitter;


        public StateTransferHandler(MessageTransmitter transmitter)
        {
            m_transmitter = transmitter;
        }


        public void transferMessages(short recipient, Collection<? extends NetworkMessage> msgs)
        {
            for( NetworkMessage msg : msgs )
                transferMessage( recipient, msg );
        }


        public void transferMessage(short recipient, NetworkMessage msg)
        {
            m_transmitter.unicastMessage( msg, recipient );
        }

    }

}

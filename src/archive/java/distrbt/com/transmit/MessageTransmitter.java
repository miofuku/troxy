package distrbt.com.transmit;

import java.util.Objects;

import reptor.chronos.Commutative;
import reptor.chronos.Orphic;
import reptor.chronos.message.MulticastChannel;
import reptor.distrbt.certify.MessageCertifier;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;

import com.google.common.base.Preconditions;

// Even in the case of a counting certifier, all invocations of this handler commute. The exact counter value
// does not matter; the results are equivalent to a transmitter processing messages in FIFO order.
@Commutative
@Deprecated
public class MessageTransmitter implements Orphic
{

    private final MessageMapper    m_mapper;
    private final MessageCertifier m_certifier;
    private final MulticastChannel<? super NetworkMessage> m_unicast;
    private final MulticastChannel<? super NetworkMessage> m_broadcast;


    public MessageTransmitter(MessageMapper mapper, MessageCertifier certifier,
            MulticastChannel<? super NetworkMessage> transmit)
    {
        this( mapper, certifier, transmit, transmit );
    }


    public MessageTransmitter(MessageMapper mapper, MessageCertifier certifier,
            MulticastChannel<? super NetworkMessage> unicast, MulticastChannel<? super NetworkMessage> broadcast)
    {
        Preconditions.checkArgument( unicast!=null || broadcast!=null );

        m_mapper    = Objects.requireNonNull( mapper );
        m_certifier = certifier;
        m_unicast   = unicast;
        m_broadcast = broadcast;
    }


    public void unicastMessage(NetworkMessage msg, short recipient)
    {
        prepareMessage( msg );

        m_unicast.enqueueUnicast( recipient, msg );
    }


    public void broadcastMessage(NetworkMessage msg)
    {
        prepareMessage( msg );

        m_broadcast.enqueueMessage( msg );
    }


    private void prepareMessage(NetworkMessage msg)
    {
        if( m_certifier==null )
            m_mapper.serializeMessage( msg );
        else
            m_mapper.certifyAndSerializeMessage( msg, m_certifier );
    }

}

package reptor.replct.connect;

import java.util.Objects;

import reptor.chronos.orphics.AttachableOrphic;
import reptor.distrbt.com.Message;
import reptor.jlib.NotImplementedException;
import reptor.replct.MessageCategoryID;
import reptor.replct.ProtocolID;


public class CommunicationMessages
{

    private static final int COMMUNICATION_BASE = ProtocolID.COMMON | MessageCategoryID.COMMUNICATION;
    public  static final int NEW_CONNECTION_ID  = COMMUNICATION_BASE + 1;


    public static class NewConnection implements Message, AttachableOrphic<Object>
    {
        private final StandardHandshakeState    m_hsstate;

        public NewConnection(StandardHandshakeState hsstate)
        {
            m_hsstate = Objects.requireNonNull( hsstate );
        }

        @Override
        public int getTypeID()
        {
            return NEW_CONNECTION_ID;
        }

        public RemoteEndpoint getRemoteEndpoint()
        {
            return m_hsstate.getRemoteEndpoint();
        }

        public StandardHandshakeState getState()
        {
            return m_hsstate;
        }

        @Override
        public void bindToMaster(Object master)
        {
            throw new NotImplementedException();
        }

        @Override
        public void unbindFromMaster(Object master)
        {
            throw new NotImplementedException();

        }
    }

}

package refit.protocols.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.chronos.PushMessageSink;
import reptor.chronos.message.MulticastChannel;
import reptor.distrbt.com.Message;
import reptor.replct.MessageCategoryID;
import reptor.replct.MessageHandler;
import reptor.replct.ProtocolID;


public class InternalReconfigurationProtocol
{


    //-------------------------------------//
    //         Objects and States          //
    //-------------------------------------//

    public interface Configuration
    {
        int getNumber();

        PushMessageSink<Message>  configurationCoordinator();
        MulticastChannel<Message> configurationAcceptors();
    }


    public enum InstanceState
    {
        RECONFIGURATION_REQUEST,
        PENDING,
        PREPARED,
        COMMITTED
    }


    //-------------------------------------//
    //               Handler               //
    //-------------------------------------//

    // TODO: Add request phase
    public static class InternalReconfigurationHandler implements MessageHandler<Message>
    {

        private static final Logger s_logger = LoggerFactory.getLogger( InternalReconfigurationHandler.class );

        private InstanceState m_state        = InstanceState.PENDING;
        private Configuration m_stableconfig = null;
        private Configuration m_curconfig    = null;
        private int           m_curconfno    = -1;

        private final boolean m_iscoord;
        private final String  m_cntxtname;

        // Coordinator instance data
        private int                       m_curnswitchs = 0;
        private MulticastChannel<Message> m_acceptors   = null;

        // Acceptor instance data
        private PushMessageSink<Message>   m_configcoord = null;


        public InternalReconfigurationHandler(boolean iscoord, String cntxtname)
        {
            m_iscoord   = iscoord;
            m_cntxtname = cntxtname;
        }


        @Override
        public String toString()
        {
            return "CFGHL[" + m_cntxtname + "]";
        }


        @Override
        public boolean handleMessage(Message msg)
        {
            InstanceState prevstate = m_state;

            switch( msg.getTypeID() )
            {
            case INTERNAL_NEW_CONFIGURATION_ID:
                if( m_iscoord )
                    handleReconfiguration( (InternalNewConfiguration) msg );
                else
                    handleNewConfiguration( (InternalNewConfiguration) msg );
                break;
            case INTERNAL_SWITCH_CONFIGURATION_ID:
                if( m_iscoord )
                    handleSwitchConfiguration( (InternalSwitchConfiguration) msg );
                else
                    handleSwitchConfigurationAck( (InternalSwitchConfiguration) msg );
                break;
            default:
                throw new IllegalStateException( msg.toString() );
            }

            return m_state!=prevstate;
        }


        private void advanceState(InstanceState newstate)
        {
            s_logger.debug( "{} advanced state to {} for {}", this, newstate, m_curconfno );

            m_state = newstate;
        }


        public void handleReconfiguration(InternalNewConfiguration nc)
        {
            // We got a new configuration from the outside.
            prepareNewConfiguration( nc );

            // Initialise it. We need a multicast channel to all acceptors.
            m_acceptors   = nc.getConfiguration().configurationAcceptors();
            m_curnswitchs = 0;

            // Inform acceptors about the new configuration.
            m_acceptors.enqueueMessage( nc );
        }

        public void handleNewConfiguration(InternalNewConfiguration nc)
        {
            // The coordinator has sent a new configuration.
            prepareNewConfiguration( nc );

            // Initialise it. Create a channel to the coordinator.
            m_configcoord = nc.getConfiguration().configurationCoordinator();

            // Send switch configuration to coordinator.
            m_configcoord.enqueueMessage( new InternalSwitchConfiguration( nc.getConfigNumber() ) );
        }

        public void handleSwitchConfiguration(InternalSwitchConfiguration sc)
        {
            // Another switch from an acceptor.
            assert sc.getConfigNumber()==m_curconfno;

            m_curnswitchs++;

            // Do we have a switch from all acceptors?
            if( m_curnswitchs==m_acceptors.size() )
            {
                commitNewConfiguration();

                // Acceptors can commit the new configuration too
                m_acceptors.enqueueMessage( sc );
            }
        }

        public void handleSwitchConfigurationAck(InternalSwitchConfiguration sc)
        {
            // The coordinator sent the acknowledgement, the new configuration is stable.
            assert sc.getConfigNumber()==m_curconfno;

            commitNewConfiguration();
        }


        private void prepareNewConfiguration(InternalNewConfiguration nc)
        {
            assert m_stableconfig==null;

            m_curconfig = nc.getConfiguration();
            m_curconfno = nc.getConfigNumber();

            advanceState( InstanceState.PREPARED );
        }

        private void commitNewConfiguration()
        {
            assert !isConfigStable();

            m_stableconfig = m_curconfig;

            advanceState( InstanceState.COMMITTED );
        }


        public void initConfig(Configuration config)
        {
            m_curconfig = m_stableconfig = config;
            m_curconfno = config.getNumber();

            advanceState( InstanceState.COMMITTED );
        }


        public InstanceState getState()
        {
            return m_state;
        }

        public boolean isConfigPrepared()
        {
            return m_state==InstanceState.PREPARED;
        }

        public boolean isConfigStable()
        {
            return m_state==InstanceState.COMMITTED;
        }

        public Configuration getCurrentConfig()
        {
            return m_curconfig;
        }

        public Configuration getLastStableConfig()
        {
            return m_stableconfig;
        }

    }


    //-------------------------------------//
    //              Messages               //
    //-------------------------------------//

    private static final int RECONFIGURATION_BASE             = ProtocolID.COMMON | MessageCategoryID.RECONFIGURATION;
    public  static final int NEW_DOMAIN_ID                    = RECONFIGURATION_BASE + 1;
    public  static final int INTERNAL_NEW_CONFIGURATION_ID    = RECONFIGURATION_BASE + 2;
    public  static final int INTERNAL_SWITCH_CONFIGURATION_ID = RECONFIGURATION_BASE + 3;


    public static class NewDomain implements Message
    {
        @Override
        public int getTypeID()
        {
            return NEW_DOMAIN_ID;
        }
    }


    public static abstract class InternalReconfigurationMessage implements Message
    {
        private final int m_confno;

        public InternalReconfigurationMessage(int confno)
        {
            m_confno = confno;
        }

        public final int getConfigNumber()
        {
            return m_confno;
        }
    }


    public static class InternalNewConfiguration extends InternalReconfigurationMessage
    {
        private final Configuration m_config;

        public InternalNewConfiguration(Configuration config)
        {
            super( config.getNumber() );

            m_config = config;
        }

        @Override
        public int getTypeID()
        {
            return INTERNAL_NEW_CONFIGURATION_ID;
        }

        @Override
        public String toString()
        {
            return "{INTERNAL_NEW_CONFIGURATION}";
        }

        public Configuration getConfiguration()
        {
            return m_config;
        }
    }


    public static class InternalSwitchConfiguration extends InternalReconfigurationMessage
    {
        public InternalSwitchConfiguration(int confno)
        {
            super( confno );
        }

        @Override
        public int getTypeID()
        {
            return INTERNAL_SWITCH_CONFIGURATION_ID;
        }

        @Override
        public String toString()
        {
            return "{INTERNAL_SWITCH_CONFIGURATION}";
        }

   }

}

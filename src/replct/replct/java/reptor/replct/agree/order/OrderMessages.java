package reptor.replct.agree.order;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Consumer;

import reptor.distrbt.com.AbstractNetworkMessage;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageDigestSink;
import reptor.distrbt.com.MessageDigester;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.MessageVerifier;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.VerificationException;
import reptor.replct.MessageCategoryID;
import reptor.replct.ProtocolID;


public class OrderMessages
{

    private static final int ORDER_BASE         = ProtocolID.COMMON | MessageCategoryID.ORDER;
    public  static final int NOOP_ID            = ORDER_BASE + 1;
    public  static final int COMMAND_BATCH_ID   = ORDER_BASE + 2;
    public  static final int COMMAND_ORDERED_ID = ORDER_BASE + 3;


    public interface CommandContainer extends NetworkMessage
    {
        void        verifyCommands(MessageMapper mapper, MessageVerifier<? super Command> verifier) throws VerificationException;

        int         getNumberOfCommands();
        Command[]   getCommands();

        void        forEach(Consumer<? super Command> consumer);
    }


    public interface Command extends CommandContainer
    {

    }


    // TODO: Simple command container should be no order messages but message elements.
    public static class Noop extends AbstractNetworkMessage implements CommandContainer
    {

        private static final Command[] m_emptycmds = new Command[ 0 ];

        public Noop()
        {
            setValid();
        }

        public Noop(ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException
        {
            super( in );

            setValid();
        }

        @Override
        public short getSender()
        {
            return -1;
        }

        @Override
        public int getTypeID()
        {
            return NOOP_ID;
        }

        @Override
        public String toString()
        {
            return "{NOOP}";
        }

        @Override
        public void verifyCommands(MessageMapper mapper, MessageVerifier<? super Command> verifier) throws VerificationException
        {

        }

        @Override
        public int getNumberOfCommands()
        {
            return 0;
        }


        @Override
        public Command[] getCommands()
        {
            return m_emptycmds;
        }


        @Override
        public void forEach(Consumer<? super Command> consumer)
        {

        }

    }

    public static class CommandBatch extends AbstractOrderMessage implements CommandContainer
    {

        private final Command[] m_cmds;


        public CommandBatch(short sender, long orderno, int viewno, Command[] cmds)
        {
            super( sender, orderno, viewno );

            m_cmds = cmds;
        }


        @Override
        public void writeTypeContentTo(ByteBuffer out, MessageMapper mapper)
        {
            super.writeTypeContentTo( out, mapper );

            out.putShort( (short) m_cmds.length );

            for( Command cmd : m_cmds )
                mapper.writeMessageTo( out, cmd );
        }


        @Override
        public int calculateTypePlainPrefixSize(MessageMapper mapper)
        {
            return super.calculateTypePlainPrefixSize( mapper ) + Short.BYTES;
        }


        @Override
        public int calculateTypeContentSize(MessageMapper mapper)
        {
            int size = super.calculateTypeContentSize( mapper );

            for( Command cmd : m_cmds )
                size += mapper.calculateMessageSize( cmd );

            return size;
        }


        public CommandBatch(ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException
        {
            super( in );

            short bs = in.getShort();
            m_cmds = new Command[ bs ];

            for( int i=0; i<bs; i++ )
                m_cmds[ i ] = (Command) mapper.readMessageFrom( in );
        }


        @Override
        public void prepareDigestion(MessageDigester digester)
        {
            super.prepareDigestion( digester );

            digester.prepareInnerMessagesForDigestion( m_cmds );
        }


        @Override
        public void digestTypeContentTo(MessageDigestSink sink)
        {
            sink.putInnerMessages( m_cmds );
        }


        @Override
        public int getTypeID()
        {
            return COMMAND_BATCH_ID;
        }


        @Override
        public Command[] getCommands()
        {
            return m_cmds;
        }


        @Override
        public void forEach(Consumer<? super Command> consumer)
        {
            for( Command cmd : m_cmds )
                consumer.accept( cmd );
        }


        @Override
        public void verifyCommands(MessageMapper mapper, MessageVerifier<? super Command> verifier) throws VerificationException
        {
            mapper.verifyInnerMessages( this, m_cmds, verifier );
        }


        @Override
        public int getNumberOfCommands()
        {
            return m_cmds.length;
        }


        @Override
        public String toString()
        {
            return String.format( "{%s|%d}", idString(), m_cmds.length );
        }


        @Override
        public boolean equals(Object obj)
        {
            if( !super.equals( obj ) || !( obj instanceof CommandBatch ) )
                return false;

            CommandBatch other = (CommandBatch) obj;

            return Arrays.equals( other.m_cmds, m_cmds );
        }

    }


    public static class CommandOrdered implements Message
    {
        private final byte              m_proposer;
        private final long              m_orderno;
        private final CommandContainer  m_command;

        public CommandOrdered(byte proposer, long orderno, CommandContainer command)
        {
            assert command!=null;

            m_proposer = proposer;
            m_orderno  = orderno;
            m_command  = command;
        }

        @Override
        public int getTypeID()
        {
            return COMMAND_ORDERED_ID;
        }

        @Override
        public String toString()
        {
            return String.format( "{COMMAND_ORDERED|%d:%d|%s}", m_proposer, m_orderno, m_command );
        }

        public byte getProposer()
        {
            return m_proposer;
        }

        public long getOrderNumber()
        {
            return m_orderno;
        }

        public CommandContainer getCommand()
        {
            return m_command;
        }
    }

}

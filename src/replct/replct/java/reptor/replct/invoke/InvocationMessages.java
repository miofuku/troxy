package reptor.replct.invoke;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;

import reptor.distrbt.com.AbstractNetworkMessage;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageDigestSink;
import reptor.distrbt.com.MessageDigester;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.MessageVerifier;
import reptor.distrbt.com.VerificationException;
import reptor.distrbt.common.data.ImmutableData;
import reptor.replct.MessageCategoryID;
import reptor.replct.ProtocolID;
import reptor.replct.agree.order.OrderMessages.Command;
import reptor.replct.agree.order.OrderMessages.CommandContainer;


public class InvocationMessages
{

    private static final int INVOCATION_BASE       = ProtocolID.COMMON | MessageCategoryID.CLIENT;
    public  static final int INVOKE_SERVICE_ID     = INVOCATION_BASE + 1;
    public  static final int REQUEST_ID            = INVOCATION_BASE + 2;
    public  static final int REQUEST_EXECUTED_ID   = INVOCATION_BASE + 3;
    public  static final int REPLY_ID              = INVOCATION_BASE + 4;
    public  static final int INVOKCATION_RESULT_ID = INVOCATION_BASE + 5;


    public static class InvokeService implements Message
    {
        private final long          m_invno;
        private final ImmutableData m_command;

        public InvokeService(long invno, ImmutableData command)
        {
            m_invno   = invno;
            m_command = Objects.requireNonNull( command );
        }

        @Override
        public int getTypeID()
        {
            return INVOKE_SERVICE_ID;
        }

        @Override
        public String toString()
        {
            return "{INVOKE_SERVICE}";
        }

        public long getInvocationNumber()
        {
            return m_invno;
        }

        public ImmutableData getCommand()
        {
            return m_command;
        }
    }


    public static class Request extends AbstractNetworkMessage implements Command, CommandContainer
    {

        private final short         m_sender;
        private final long          m_number;
        private final boolean       m_isread;
        private final boolean       m_useroopt;
        private final boolean       m_ispanic;
        private final ImmutableData m_command;

        private final transient Object m_extcontext;


        public Request(short sender, long number, ImmutableData command, boolean isread, boolean ispanic)
        {
            Preconditions.checkArgument( !isread || !ispanic );

            m_sender     = sender;
            m_number     = number;
            m_isread     = isread;
            m_useroopt   = isread;
            m_ispanic    = ispanic;
            m_command    = command;
            m_extcontext = null;
        }

        public Request(short sender, long number, ImmutableData command, boolean isread, boolean useroopt, boolean ispanic)
        {
            Preconditions.checkArgument( !isread || !ispanic );
            Preconditions.checkArgument(isread || !useroopt);

            m_sender     = sender;
            m_number     = number;
            m_isread     = isread;
            m_useroopt   = useroopt;
            m_ispanic    = ispanic;
            m_command    = command;
            m_extcontext = null;
        }

        @Override
        public void writeTypeContentTo(ByteBuffer out, MessageMapper mapper)
        {
            super.writeTypeContentTo( out, mapper );

            out.putShort( m_sender );
            out.putLong( m_number );

            byte flags;
            if( m_isread )
                flags = (byte) 0x02;
            else if( m_ispanic )
                flags = (byte) 0x01;
            else
                flags = (byte) 0x00;

            if (m_useroopt)
                flags |= (byte) 0x04;

            out.put( flags );

            out.putShort( (short) m_command.size() );
            m_command.writeTo( out );
        }


        @Override
        public int calculateTypePlainPrefixSize(MessageMapper mapper)
        {
            return super.calculateTypeContentSize( mapper ) + Short.BYTES + Long.BYTES + Byte.BYTES + Short.BYTES + m_command.size();
        }


        public Request(ByteBuffer in, MessageMapper mapper, Object extcontext) throws IOException
        {
            super( in );

            m_sender  = in.getShort();
            m_number  = in.getLong();

            byte flags = in.get();
            m_ispanic  = ( flags & 0x01 )!=0;
            m_isread = ( flags & 0x02 )!=0;
            m_useroopt = ( flags & 0x04 )!=0;

            if( m_ispanic && m_isread )
                throw new IOException();

            if (!m_isread && m_useroopt)
                throw new IOException();

            int plsize   = in.getShort();
            m_command    = ImmutableData.readFrom( in, plsize );
            m_extcontext = extcontext;
        }


        @Override
        public int getTypeID()
        {
            return InvocationMessages.REQUEST_ID;
        }


        public String getTypeName()
        {
            return "REQUEST";
        }


        @Override
        public String toString()
        {
            String type = m_ispanic ? "p" : ( m_isread ? "r" : "n" );
            return String.format( "{%s|%d:%d|%s|%db}", getTypeName(), m_sender, m_number, type, m_command.size() );
        }


        @Override
        public short getSender()
        {
            return m_sender;
        }


        public long getNumber()
        {
            return m_number;
        }


        public ImmutableData getCommand()
        {
            return m_command;
        }


        public boolean isReadRequest()
        {
            return m_isread;
        }


        public boolean useReadOnlyOptimization()
        {
            return m_useroopt;
        }


        public boolean isPanic()
        {
            return m_ispanic;
        }


        public Object getExtensionContext()
        {
            return m_extcontext;
        }


        @Override
        public void verifyCommands(MessageMapper mapper, MessageVerifier<? super Command> verifier) throws VerificationException
        {
            mapper.verifyInnerMessage( this, this, verifier );
        }


        @Override
        public int getNumberOfCommands()
        {
            return 1;
        }


        @Override
        public Request[] getCommands()
        {
            return new Request[] { this };
        }


        @Override
        public void forEach(Consumer<? super Command> consumer)
        {
            consumer.accept( this );
        }


        @Override
        public boolean equals(Object obj)
        {
            if( obj==this )
                return true;

            if( obj==null || !( obj instanceof Request ) )
                return false;

            Request other = (Request) obj;

            return other.m_sender==m_sender && other.m_number==m_number &&
                   m_command.equals( other.m_command );
        }


        @Override
        public int hashCode()
        {
            return Objects.hash( m_sender, m_number );
        }

    }


    public static class RequestExecuted implements Message
    {
        private final Request       m_request;
        private final boolean       m_execdspec;
        private final ImmutableData m_result;
        private final ReplyMode     m_repmode;
        private final Object        m_extcntxt;

        public RequestExecuted(Request request, boolean execdspec, ImmutableData result, ReplyMode repmode, Object extcntxt)
        {
            assert request!=null;

            m_request   = request;
            m_execdspec = execdspec;
            m_result    = result;
            m_repmode   = repmode;
            m_extcntxt  = extcntxt;
        }

        @Override
        public int getTypeID()
        {
            return InvocationMessages.REQUEST_EXECUTED_ID;
        }

        @Override
        public String toString()
        {
            return "{REQUEST_EXECUTED}";
        }

        public Request getRequest()
        {
            return m_request;
        }

        public boolean wasExecutedSpeculatively()
        {
            return m_execdspec;
        }

        public ImmutableData getResult()
        {
            return m_result;
        }

        public ReplyMode getReplyMode()
        {
            return m_repmode;
        }

        public Object getExtensionContext()
        {
            return m_extcntxt;
        }
    }


    public static class Reply extends AbstractNetworkMessage
    {

        private final short           m_sender;
        private final short           m_requester;
        private final long            m_invno;
        private final boolean         m_isfull;
        private final boolean         m_execdspec;
        private final ImmutableData   m_result;
        private final byte            m_contact;

        private final transient Object  m_extcontext;

        // TODO: Must not be part of the message itself -> Digestable message element.
        private transient ImmutableData m_resulthash;


        // TODO: The clients need a view information to detect group changes.
        public Reply(short sender, short requester, long reqno, boolean isfull, boolean execdspec, ImmutableData result,
                     byte contactno, Object extcontext)
        {
            m_sender     = sender;
            m_requester  = requester;
            m_invno      = reqno;
            m_isfull     = isfull;
            m_execdspec  = execdspec;
            m_result     = result;
            m_contact    = contactno;
            m_extcontext = extcontext;

            if( !m_isfull )
                m_resulthash = m_result;
        }


        @Override
        public void writeTypeContentTo(ByteBuffer out, MessageMapper mapper)
        {
            super.writeTypeContentTo( out, mapper );

            if (out.limit()!=0)
            {
                out.putShort( m_sender );
                out.putShort( m_requester );
                out.putLong( m_invno );
                out.put( m_contact );

                byte flags = 0x00;
                if( m_isfull )
                    flags |= 0x01;
                if( m_execdspec )
                    flags |= 0x02;
                out.put( flags );

                out.putShort( (short) m_result.size() );
            }
            else
                out.clear();
            m_result.writeTo( out );
        }


        @Override
        public int calculateTypePlainPrefixSize(MessageMapper mapper)
        {
            return super.calculateTypePlainPrefixSize( mapper ) + Short.BYTES*2 + Long.BYTES + Byte.BYTES + Byte.BYTES + Short.BYTES;
        }


        @Override
        public int calculateTypeContentSize(MessageMapper mapper)
        {
            return m_result.size();
        }


        public Reply(ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException
        {
            super( in );

            m_sender     = in.getShort();
            m_requester  = in.getShort();
            m_invno      = in.getLong();
            m_contact    = in.get();

            byte flags = in.get();
            m_isfull    = ( flags & 0x01 )!=0;
            m_execdspec = ( flags & 0x02 )!=0;

            m_result     = ImmutableData.createFor( in, in.getShort() );
            m_extcontext = null;

            if( !m_isfull )
                m_resulthash = m_result;
        }


        @Override
        public void prepareDigestion(MessageDigester digester)
        {
            super.prepareDigestion( digester );

            if( m_resulthash==null )
                m_resulthash = digester.digestData( m_result );
        }


        @Override
        public void digestTypeContentTo(MessageDigestSink sink)
        {
            // The plain prefix has been already digested be the mapper!
            sink.putData( m_resulthash );
        }


        public ImmutableData getResultHash()
        {
            return m_resulthash;
        }


        @Override
        public int getTypeID()
        {
            return InvocationMessages.REPLY_ID;
        }


        public String getTypeName()
        {
            return "REPLY";
        }


        @Override
        public String toString()
        {
            String ftype = m_isfull ? "f" : "h";
            String etype = m_execdspec ? "s" : "n";

            return String.format( "{%s|%d:%d:%d|%s%s|%db}", getTypeName(), m_sender, m_requester, m_invno, ftype, etype, m_result.size() );
        }


        @Override
        public short getSender()
        {
            return m_sender;
        }


        public short getRequester()
        {
            return m_requester;
        }


        public long getInvocationNumber()
        {
            return m_invno;
        }


        public boolean isFullResult()
        {
            return m_isfull;
        }


        public boolean wasExecutedSpeculatively()
        {
            return m_execdspec;
        }


        public ImmutableData getResult()
        {
            return m_result;
        }


        public byte[] getPayload()
        {
            byte[] payload = new byte[m_result.array().length-21-32];
            for (int i=0;i<payload.length;i++)
            {
                payload[i] = m_result.array()[i+21];
            }
            return payload;
        }


        public byte getContactReplica()
        {
            return m_contact;
        }


        public Object getExtensionContext()
        {
            return m_extcontext;
        }


        @Override
        public boolean equals(Object obj)
        {
            if( obj==this )
                return true;

            if( obj==null || !( obj instanceof Reply ) )
                return false;

            // TODO: Include payload.
            Reply other = (Reply) obj;

            return other.m_sender==m_sender && other.m_requester==m_requester && other.m_invno==m_invno;
        }


        @Override
        public int hashCode()
        {
            return Objects.hash( m_sender, m_requester, m_invno );
        }

    }


    public static class InvocationResult implements Message
    {
        private final long          m_invno;
        private final ImmutableData m_result;

        public InvocationResult(long invno, ImmutableData result)
        {
            m_invno  = invno;
            m_result = result;
        }

        @Override
        public int getTypeID()
        {
            return INVOKCATION_RESULT_ID;
        }

        @Override
        public String toString()
        {
            return "{INVOCATION_RESULT}";
        }

        public long getInvocationNumber()
        {
            return m_invno;
        }

        public ImmutableData getResult()
        {
            return m_result;
        }
    }

}

package reptor.distrbt.com.map;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.IntFunction;

import reptor.chronos.Commutative;
import reptor.distrbt.certify.Certifier;
import reptor.distrbt.certify.Verifier;
import reptor.distrbt.certify.VerifierGroup;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.MessageVerifier;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.NetworkMessageRegistry;
import reptor.distrbt.com.TypedMessageDeserializer;
import reptor.distrbt.com.VerificationException;
import reptor.distrbt.com.map.CommonHeaderMapper.CommonHeaderHolder;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.DataBuffer;
import reptor.distrbt.common.data.ImmutableData;
import reptor.distrbt.common.data.ImmutableDataBuffer;
import reptor.distrbt.common.data.MutableData;
import reptor.jlib.hash.HashAlgorithm;


// TODO: Decouple MessageMapper, CommonHeaderMapper, ExternalMessageFactory, etc.
// TODO: Separate basic MessageMapper for single messages and strategies for
//       more complex formats (arrays of messages, complete message structures)
@Commutative
public class BasicMessageMapper implements MessageMapper
{

    private final CommonHeaderMapper            m_hdrmapper;
    private final BasicMessageDigestionStrategy m_digester;

    private final DeserializationContext m_scratchcntxt = new DeserializationContext( null, null );


    public BasicMessageMapper(NetworkMessageRegistry msgreg,
                              BasicMessageDigestionStrategy.Variant digvariant, HashAlgorithm digalgo)
    {
        m_hdrmapper = new SizeFirstHeaderMapper( msgreg );
        m_digester  = new BasicMessageDigestionStrategy( digvariant, digalgo, m_hdrmapper );
    }


    @Override
    public HashAlgorithm getHashAlgorithm()
    {
        return m_digester.getHashAlgorithm();
    }


    @Override
    public void prepareInnerMessageForDigestion(NetworkMessage innermsg)
    {
        m_digester.prepareInnerMessageForDigestion( innermsg );
    }


    @Override
    public <M extends NetworkMessage> void prepareInnerMessagesForDigestion(M[] innermsgs)
    {
        m_digester.prepareInnerMessagesForDigestion( innermsgs );
    }


    @Override
    public ImmutableData digestData(Data data)
    {
        return m_digester.digestData( data );
    }


    @Override
    public ImmutableData digestTypeContent(NetworkMessage msg, int offset, int size)
    {
        return m_digester.digestTypeContent( msg, offset, size );
    }


    @Override
    public NetworkMessage digestMessageContent(NetworkMessage msg)
    {
        return m_digester.digestMessageContent( msg );
    }


    @Override
    public NetworkMessage digestMessage(NetworkMessage msg)
    {
        return m_digester.digestMessage( msg );
    }


    @Override
    public int calculateMessageSize(NetworkMessage msg)
    {
        if( !msg.hasContentSize() )
            initContentSizes( msg );

        return msg.getMessageSize();
    }


    protected int initContentSizes(NetworkMessage msg)
    {
        int presize = m_hdrmapper.calculateCommonHeaderSize( msg ) + msg.calculateTypePlainPrefixSize( this );
        int cntsize = presize + msg.calculateTypeContentSize( this );

        msg.setContentSizes( presize, cntsize );

        return cntsize;
    }


    @Override
    public <M extends NetworkMessage> int calculateMessageSizes(M[] msgs)
    {
        int size = 0;

        for( NetworkMessage msg : msgs )
            size += calculateMessageSize( msg );

        return size;
    }


    @Override
    public void serializeMessage(NetworkMessage msg)
    {
        serializeMessage( msg, null );
    }


    @Override
    public void certifyAndSerializeMessage(NetworkMessage msg, Certifier certifier)
    {
        serializeMessage( msg, Objects.requireNonNull( certifier ) );
    }


    protected void serializeMessage(NetworkMessage msg, Certifier certifier)
    {
        int cntsize;

        if( msg.hasContentSize() )
            cntsize = msg.getContentSize();
        else
            cntsize = initContentSizes( msg );

        int certsize = certifier==null ? 0 : certifier.getCertificateSize();
        msg.setCertificateSize( certsize );

        DataBuffer msgbuffer = new DataBuffer( cntsize+certsize );
        msg.setMessageData( msgbuffer );

        ByteBuffer out = msgbuffer.byteBuffer();

        writeMessageContentTo( out, msg );

        if( certifier!=null )
        {
            Data msgdata = certifier.requiresDigestedData() ? digestMessageContent( msg ).getContentDigest() :
                                                              msg.getContentData();

            msgbuffer.adaptSlice( out );

            certifier.createCertificate( msgdata, msgbuffer );

            msgbuffer.adaptSlice( 0, cntsize+certsize );
        }
    }


    @Override
    public boolean writeMessageTo(ByteBuffer out, NetworkMessage msg)
    {
        if( out.remaining()<msg.getMessageSize() )
            return false;
        else if( msg.getMessageData()==null )
        {
            assert msg.getCertificateSize()==0;

            DataBuffer msgbuffer = new DataBuffer( out, 0, msg.getMessageSize() );
            msg.setMessageData( msgbuffer );

            writeMessageContentTo( out, msg );
            return true;
        }
        else
        {
            msg.getMessageData().writeTo( out );
            return true;
        }
    }


    @Override
    public boolean writeMessageContentTo(ByteBuffer out, NetworkMessage msg)
    {
        if( out.remaining()<msg.getContentSize() )
            return false;
        else
        {
            m_hdrmapper.writeCommonHeaderTo( out, msg );
            msg.writeTypeContentTo( out, this );
            return true;
        }
    }


    @Override
    public <M extends NetworkMessage> void writeMessagesTo(ByteBuffer out, M[] msgs)
    {
        for( NetworkMessage msg : msgs )
            writeMessageTo( out, msg );
    }


    @Override
    public ByteBuffer outputBuffer(NetworkMessage msg)
    {
        return msg.getMessageData().byteBuffer();
    }


    @Override
    public void verifyMessage(NetworkMessage msg, Verifier verifier) throws VerificationException
    {
        int errcode = 0;

        if( msg.isCertificateValid()!=null )
            errcode = msg.isCertificateValid() ? 0 : 1;
        else
        {
            if( msg.getCertificateSize() < verifier.getCertificateSize() )
                errcode = 2;
            // TODO: In that case, the verification should be deactivate for that message type or
            //       we talk about CFT, respectively.
            else if( verifier.getCertificateSize()>0 )
            {
                Data msgdata = verifier.requiresDigestedData() ? digestMessageContent( msg ).getContentDigest() :
                                                                 msg.getContentData();

                errcode = verifier.verifyCertificate( msgdata, msg.getCertificateData() ) ? 0 : 3;
            }

            msg.setCertificateValid( errcode==0 );
        }

        if( errcode!=0 )
            throw new VerificationException( String.format( "%s (%d)", msg, errcode ) );
    }


    @Override
    public void verifyInnerMessage(NetworkMessage msg, NetworkMessage innermsg, Verifier verifier)
            throws VerificationException
    {
        if( msg.areInnerMessagesValid()==null )
        {
            try
            {
                verifyMessage( innermsg, verifier );

                msg.setInnerMessagesValid( true );
            }
            catch( VerificationException e )
            {
                msg.setInnerMessagesValid( false );
                throw new VerificationException( msg.toString(), e );
            }
        }
        else if( msg.areInnerMessagesValid()==Boolean.FALSE )
        {
            throw new VerificationException( msg.toString() );
        }
    }


    @Override
    public <M extends NetworkMessage, V extends MessageVerifier<? super M>>
            void verifyInnerMessage(NetworkMessage msg, M innermsg, V verifier) throws VerificationException
    {
        if( msg.areInnerMessagesValid()==null )
        {
            try
            {
                verifier.verifyMessage( innermsg );

                msg.setInnerMessagesValid( true );
            }
            catch( VerificationException e )
            {
                msg.setInnerMessagesValid( false );
                throw new VerificationException( msg.toString(), e );
            }
        }
        else if( msg.areInnerMessagesValid()==Boolean.FALSE )
        {
            throw new VerificationException( msg.toString() );
        }
    }


    @Override
    public <M extends NetworkMessage>
            void verifyInnerMessages(NetworkMessage msg, M[] innermsgs, VerifierGroup verifiers)
                    throws VerificationException
    {
        if( msg.areInnerMessagesValid()==null )
        {
            try
            {
                for( M innermsg : innermsgs )
                    verifyMessage( innermsg, verifiers.getVerifier( innermsg.getSender() ) );

                msg.setInnerMessagesValid( true );
            }
            catch( VerificationException e )
            {
                msg.setInnerMessagesValid( false );
                throw new VerificationException( msg.toString(), e );
            }
        }
        else if( msg.areInnerMessagesValid()==Boolean.FALSE )
        {
            throw new VerificationException( msg.toString() );
        }
    }


    @Override
    public <M extends NetworkMessage, V extends MessageVerifier<? super M>>
            void verifyInnerMessages(NetworkMessage msg, M[] innermsgs, V verifier)
                    throws VerificationException
    {
        if( msg.areInnerMessagesValid()==null )
        {
            try
            {
                verifier.verifyMessages( innermsgs );

                msg.setInnerMessagesValid( true );
            }
            catch( VerificationException e )
            {
                msg.setInnerMessagesValid( false );
                throw new VerificationException( msg.toString(), e );
            }
        }
        else if( msg.areInnerMessagesValid()==Boolean.FALSE )
        {
            throw new VerificationException( msg.toString() );
        }
    }


    @Override
    public Object createSourceContext(Integer srcid, IntFunction<Object> msgcntxtfac)
    {
        return new DeserializationContext( srcid, msgcntxtfac );
    }


    @Override
    public NetworkMessage tryReadMessageFrom(ByteBuffer in, Object srccntxt) throws IOException
    {
        DeserializationContext cntxt = (DeserializationContext) srccntxt;

        if( cntxt.getDeserializer()==null && !m_hdrmapper.tryReadCommonHeader( in, cntxt ) )
            return null;

        assert !cntxt.isMessageComplete();

        if( !copyBuffer( in, cntxt ) )
            return null;

        TypedMessageDeserializer deserializer = cntxt.getDeserializer();
        Data   msgdata  = cntxt.getBuffer();
        int    hdrsize  = cntxt.getHeaderSize();
        Object msgcntxt = cntxt.createMessageContext();

        NetworkMessage msg = deserializeMessage( deserializer, msgdata, hdrsize, msgdata.byteBuffer(), msgcntxt );

        cntxt.clear();

        return msg;
    }


    public NetworkMessage tryReadMessageFrom(ByteBuffer in, int msgtypeid, Object srccntxt) throws IOException
    {
        return confirmRead( tryReadMessageFrom( in, srccntxt ), msgtypeid );
    }


    public NetworkMessage
            tryReadMessageFrom(ByteBuffer in, int msgtypeid, int msgsize, Object srccntxt) throws IOException
    {
        return confirmRead( tryReadMessageFrom( in, srccntxt ), msgtypeid, msgsize );
    }


    @Override
    public NetworkMessage readMessageFrom(ByteBuffer in) throws IOException
    {
        if( !m_hdrmapper.tryReadCommonHeader( in, m_scratchcntxt ) )
            throw new IOException();

        TypedMessageDeserializer deserializer = m_scratchcntxt.getDeserializer();
        int msgsize = m_scratchcntxt.getMessageSize();
        int hdrsize = m_scratchcntxt.getHeaderSize();

        Data msgdata = new ImmutableDataBuffer( in, 0, msgsize );

        return deserializeMessage( deserializer, msgdata, hdrsize, in, null );
    }


    public NetworkMessage readMessageFrom(ByteBuffer in, int msgtypeid) throws IOException
    {
        return confirmRead( readMessageFrom( in ), msgtypeid );
    }


    public NetworkMessage readMessageFrom(ByteBuffer in, int msgtypeid, int msgsize) throws IOException
    {
        return confirmRead( readMessageFrom( in ), msgtypeid, msgsize );
    }


    @Override
    public NetworkMessage readMessageFrom(Data msgdata) throws IOException
    {
        ByteBuffer in = msgdata.byteBuffer();

        if( !m_hdrmapper.tryReadCommonHeader( in, m_scratchcntxt ) )
            throw new IOException();

        TypedMessageDeserializer deserializer = m_scratchcntxt.getDeserializer();
        int msgsize = m_scratchcntxt.getMessageSize();
        int hdrsize = m_scratchcntxt.getHeaderSize();

        if( msgdata.size()!=msgsize )
            msgdata = msgdata.slice( 0, msgsize );

        return deserializeMessage( deserializer, msgdata, hdrsize, in, null );
    }


    public NetworkMessage readMessageFrom(Data msgdata, int msgtypeid) throws IOException
    {
        return confirmRead( readMessageFrom( msgdata ), msgtypeid );
    }


    public NetworkMessage readMessageFrom(Data msgdata, int msgtypeid, int msgsize) throws IOException
    {
        return confirmRead( readMessageFrom( msgdata ), msgtypeid, msgsize );
    }


    @Override
    @SuppressWarnings("unchecked")
    public <M extends NetworkMessage>
            M[] readMessagesFrom(ByteBuffer in, M[] out, Class<? extends NetworkMessage> clazz) throws IOException
    {
        for( int i=0; i<out.length; i++ )
        {
            NetworkMessage msg = readMessageFrom( in );

            if( !clazz.isAssignableFrom( msg.getClass() ) )
                throw new IOException();

            out[ i ] = (M) msg;
        }

        return out;
    }


    private static NetworkMessage confirmRead(NetworkMessage msg, int msgtypeid) throws IOException
    {
        if( msg!=null && msg.getTypeID()!=msgtypeid )
            throw new IOException();

        return msg;
    }


    private static NetworkMessage confirmRead(NetworkMessage msg, int msgtypeid, int msgsize) throws IOException
    {
        if( msg!=null )
        {
            confirmRead( msg, msgtypeid );

            if( msg.getMessageSize()!=msgsize )
                throw new IOException();
        }

        return msg;
    }


    public boolean copyBuffer(ByteBuffer in, DeserializationContext cntxt) throws IOException
    {
        int         msgsize   = cntxt.getMessageSize();
        MutableData msgbuffer = cntxt.getBuffer();

        if( msgbuffer==null )
            cntxt.setBuffer( msgbuffer = new DataBuffer( msgsize ) );

        int bufpos = cntxt.getBufferPosition();
        int nbytes = Math.min( msgsize-bufpos, in.remaining() );

        msgbuffer.readFrom( in, nbytes, bufpos );

        return cntxt.advanceBufferPosition( nbytes );
    }


    public NetworkMessage
            deserializeMessage(TypedMessageDeserializer deserializer, Data msgdata, int hdrsize, ByteBuffer in, Object msgcntxt)
                    throws IOException
    {
        NetworkMessage msg;

        in.position( in.position()+hdrsize );

        try
        {
            msg = deserializer.readMessageFrom( in, this, msgcntxt );
        }
        catch( BufferUnderflowException e )
        {
            throw new IOException( e );
        }

        int cntsize  = initContentSizes( msg );
        int certsize = msgdata.size() - cntsize;

        msg.setMessageData( msgdata );
        msg.setCertificateSize( certsize );
        // TODO: Messages are actually message contents; they are not really aware of their certificates
        in.position( in.position() + certsize );

        return msg;
    }


    static class DeserializationContext implements CommonHeaderHolder
    {

        private final IntFunction<Object> m_msgcntxtfac;
        private final int   m_srcid;

        private TypedMessageDeserializer m_deserializer;
        private int         m_msgsize = -1;
        private int         m_hdrsize = -1;

        private MutableData m_msgbuffer;
        private int         m_bufpos;


        public DeserializationContext(Integer srcid, IntFunction<Object> msgcntxtfac)
        {
            m_srcid       = srcid==null ? -1 : srcid;
            m_msgcntxtfac = msgcntxtfac;
        }


        public void clear()
        {
            m_deserializer = null;
            m_msgsize      = -1;
            m_hdrsize      = -1;
            m_msgbuffer    = null;
            m_bufpos      = 0;
        }


        @Override
        public void setHeaderInformation(TypedMessageDeserializer deserializer, int msgsize, int hdrsize)
        {
            m_deserializer = deserializer;
            m_msgsize      = msgsize;
            m_hdrsize      = hdrsize;
        }


        public TypedMessageDeserializer getDeserializer()
        {
            return m_deserializer;
        }


        public int getMessageSize()
        {
            return m_msgsize;
        }


        public int getHeaderSize()
        {
            return m_hdrsize;
        }


        public void setBuffer(MutableData msgbuffer)
        {
            m_msgbuffer = msgbuffer;
        }


        public MutableData getBuffer()
        {
            return m_msgbuffer;
        }


        public int getBufferPosition()
        {
            return m_bufpos;
        }


        public boolean advanceBufferPosition(int nbytes)
        {
            m_bufpos += nbytes;

            return isMessageComplete();
        }


        public boolean isMessageComplete()
        {
            return m_bufpos==m_msgsize;
        }


        public Object createMessageContext()
        {
            return m_msgcntxtfac==null ? null : m_msgcntxtfac.apply( m_srcid );
        }

    }

}

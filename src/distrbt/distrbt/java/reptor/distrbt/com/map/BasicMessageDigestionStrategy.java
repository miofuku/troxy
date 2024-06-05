package reptor.distrbt.com.map;

import java.security.MessageDigest;
import java.util.Objects;

import reptor.distrbt.com.MessageDigestionStrategy;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.ImmutableData;
import reptor.jlib.hash.HashAlgorithm;

public class BasicMessageDigestionStrategy implements MessageDigestionStrategy
{

    public enum Variant
    {
        Plain,                                  // Digestions: 0.
        DigestedContent,                        // Digestions: 1. Sets content digest as side effect.
        DigestedMessage,                        // Digestions: 1. Sets message digest as side effect.
        DigestedMessageOverDigestedContent      // Digestions: 2. Sets content digest and message digest as side effect.
    }


    private final Variant            m_variant;
    private final HashAlgorithm      m_hashalgo;
    private final MessageDigest      m_digest;
    private final CommonHeaderMapper m_hdrmapper;


    public BasicMessageDigestionStrategy(Variant variant, HashAlgorithm hashalgo, CommonHeaderMapper hdrmapper)
    {
        m_hashalgo  = Objects.requireNonNull( hashalgo );
        m_digest    = hashalgo.digester();
        m_hdrmapper = Objects.requireNonNull( hdrmapper );
        m_variant   = Objects.requireNonNull( variant );
    }


    @Override
    public HashAlgorithm getHashAlgorithm()
    {
        return m_hashalgo;
    }


    @Override
    public NetworkMessage digestMessage(NetworkMessage msg)
    {
        if( msg.getMessageDigest()==null )
        {
            prepareMessageDigestion( msg );

            putMessage( msg );

            msg.setMessageDigest( ImmutableData.wrap( m_digest.digest() ) );
        }

        return msg;
    }


    @Override
    public NetworkMessage digestMessageContent(NetworkMessage msg)
    {
        if( msg.getContentDigest()==null )
        {
            prepareMessageContentDigestion( msg );

            putMessageContent( msg );

            msg.setContentDigest( ImmutableData.wrap( m_digest.digest() ) );
        }

        return msg;
    }

    @Override
    public ImmutableData digestData(Data data)
    {
        data.writeTo( m_digest );

        return ImmutableData.wrap( m_digest.digest() );
    }


    @Override
    public ImmutableData digestTypeContent(NetworkMessage msg, int offset, int size)
    {
        putTypeContentData( msg, offset, size );

        return ImmutableData.wrap( m_digest.digest() );
    }


    protected void prepareMessageDigestion(NetworkMessage msg)
    {
        if( m_variant==Variant.DigestedContent || m_variant==Variant.DigestedMessageOverDigestedContent )
            digestMessageContent( msg );
        else
            msg.prepareDigestion( this );
    }


    protected void putMessage(NetworkMessage msg)
    {
        if( m_variant==Variant.DigestedContent || m_variant==Variant.DigestedMessageOverDigestedContent )
        {
            msg.getContentDigest().writeTo( m_digest );

            if( msg.getCertificateSize()>0 )
                msg.getCertificateData().writeTo( m_digest );
        }
        else
        {
            msg.getMessageData().writeTo( m_digest );
        }
    }


    protected void prepareMessageContentDigestion(NetworkMessage msg)
    {
        msg.prepareDigestion( this );
    }


    protected void putMessageContent(NetworkMessage msg)
    {
        putPlainPrefix( msg );

        if( msg.getPlainPrefixSize()!=msg.getContentSize() )
            msg.digestTypeContentTo( this );
    }


    @Override
    public void prepareInnerMessageForDigestion(NetworkMessage innermsg)
    {
        if( m_variant==Variant.DigestedMessage || m_variant==Variant.DigestedMessageOverDigestedContent )
            digestMessage( innermsg );
        else
            prepareMessageDigestion( innermsg );
    }


    @Override
    public void putInnerMessage(NetworkMessage innermsg)
    {
        if( m_variant==Variant.DigestedMessage || m_variant==Variant.DigestedMessageOverDigestedContent )
            innermsg.getMessageDigest().writeTo( m_digest );
        else
            putMessage( innermsg );
    }


    @Override
    public <M extends NetworkMessage>
            void prepareInnerMessagesForDigestion(M[] innermsgs)
    {
        for( M msg : innermsgs )
            prepareInnerMessageForDigestion( msg );
    }


    @Override
    public <M extends NetworkMessage> void putInnerMessages(M[] innermsgs)
    {
        for( M inner : innermsgs )
            putInnerMessage( inner );
    }


    // header must not be contained in the plain prefix.
    @Override
    public void putHolderMessage(NetworkMessage msg, int header, NetworkMessage innermsg)
    {
        putTypeContentData( msg, 0, header );

        putInnerMessage( innermsg );
    }


    @Override
    public <M extends NetworkMessage>
            void putCollectionMessage(NetworkMessage msg, int header, M[] innermsgs)
    {
        putTypeContentData( msg, 0, header );

        putInnerMessages( innermsgs );
    }


    @Override
    public void putData(Data data)
    {
        data.writeTo( m_digest );
    }


    protected void putCommonHeader(NetworkMessage msg)
    {
        msg.getMessageData().writeTo( m_digest, 0, m_hdrmapper.calculateCommonHeaderSize( msg ) );
    }


    protected void putPlainPrefix(NetworkMessage msg)
    {
        msg.getMessageData().writeTo( m_digest, 0, msg.getPlainPrefixSize() );
    }


    @Override
    public void putTypeContentData(NetworkMessage msg, int offset, int size)
    {
        int prefix = msg.getPlainPrefixSize();

        assert size <= msg.getContentSize()-prefix-offset;

        msg.getMessageData().writeTo( m_digest, prefix+offset, size );
    }

}

package reptor.distrbt.certify.mac;

import java.security.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

import javax.crypto.Mac;

import com.google.common.base.Preconditions;

import reptor.chronos.Immutable;
import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.certify.BidirectionalConnectionCertifier;
import reptor.distrbt.certify.CompoundGroupCertifier;
import reptor.distrbt.certify.ConnectionCertifier;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.jlib.hash.HashAlgorithm;

@Immutable
public class JavaMacCertification implements MacCertification, MacProvider
{

    protected final MacAuthorityInstance m_authinst;
    protected final SingleMacFormat      m_certformat;


    public JavaMacCertification(MacAuthorityInstance authinst, SingleMacFormat certformat)
    {
        m_authinst   = authinst;
        m_certformat = Objects.requireNonNull( certformat );
    }


    public JavaMacCertification(MacAuthorityInstance authinst, MacAlgorithm proofalgo, HashAlgorithm digalgo)
    {
        this( authinst, new PlainSingleMacFormat( proofalgo, digalgo ) );
    }


    @Override
    public String toString()
    {
        return m_certformat.methodName( m_authinst==null ? "java" : m_authinst.toString() );
    }


    @Override
    public SingleMacFormat getCertificateFormat()
    {
        return m_certformat;
    }


    @Override
    public MacAlgorithm getProofAlgorithm()
    {
        return m_certformat.getProofAlgorithm();
    }


    @Override
    public JavaMacCertification createCertificationProvider(MacAuthorityInstance authinst)
    {
        Preconditions.checkArgument( authinst==m_authinst );

        return this;
    }


    @Override
    public BidirectionalCertifier createMessageCertifier(Key key)
    {
        Mac mac = m_certformat.getProofAlgorithm().macCreator( key );

        return new MacCertifier( mac, m_certformat );
    }


    @Override
    public BidirectionalConnectionCertifier createUnicastCertifier(SharedKeyHolder key)
    {
        return new BidirectionalConnectionCertifier( createCertifier( key ) );
    }


    @Override
    public GroupConnectionCertifier createGroupCertifier(Key key, int grpsize)
    {
        Preconditions.checkArgument( key!=null );

        BidirectionalCertifier   grpcertif = createMessageCertifier( key );
        BidirectionalCertifier[] grpverifs = new BidirectionalCertifier[ grpsize ];
        Arrays.fill( grpverifs, grpcertif );

        return new CompoundGroupCertifier<>( grpcertif, grpverifs );
    }


    @Override
    public GroupConnectionCertifier createItoNGroupCertifier(Collection<? extends SharedKeyHolder> keys)
    {
        return createGroupCertifier( determineKey( -1, keys ), keys.size() );
    }


    @Override
    public GroupConnectionCertifier createNtoNGroupCertifier(int locidx, Collection<? extends SharedKeyHolder> keys)
    {
        Preconditions.checkArgument( locidx>=0 && locidx<keys.size() );

        return createGroupCertifier( determineKey( locidx, keys ), keys.size() );
    }


    @Override
    public ConnectionCertifier createNtoICertifier(int locidx, int nprocs, SharedKeyHolder key)
    {
        return createUnicastCertifier( key );
    }


    protected Key determineKey(int locidx, Collection<? extends SharedKeyHolder> keys)
    {
        Key key = null;
        int i   = 0;

        for( SharedKeyHolder holder : keys )
        {
            if( i!=locidx )
            {
                if( key==null )
                    key = holder.getSharedKey();
                else
                    Preconditions.checkArgument( holder.getSharedKey().equals( key ) );
            }

            i++;
        }

        return key;
    }

}

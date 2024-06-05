package reptor.distrbt.certify.trusted;

import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.certify.SingleProofFormat;
import reptor.distrbt.certify.trusted.TrinxCommands.UnserializedTrinxCommand;
import reptor.jlib.hash.HashAlgorithm;


public abstract class AbstractCounterCertifier extends UnserializedTrinxCommand implements BidirectionalCertifier
{

    protected final Trinx                   m_trinx;
    protected final short                   m_tssid;
    protected final short                   m_remtssid;
    protected final SingleProofFormat       m_certformat;
    protected final boolean                 m_reqdigest;
    protected final int                     m_certsize;


    public AbstractCounterCertifier(Trinx trinx, short remtssid, SingleProofFormat certformat)
    {
        this( trinx, remtssid, certformat, trinx.getCounterCertificateSize() );
    }


    public AbstractCounterCertifier(Trinx trinx, short remtssid, SingleProofFormat certformat, int certsize)
    {
        Preconditions.checkArgument( certformat.getProofAlgorithm().getProofType().equals( TrustedCertifying.TRUSTED_COUNTER ) );
        Preconditions.checkArgument( certformat.getCertificateSize()==certsize );

        m_trinx      = trinx;
        m_tssid      = trinx.getID();
        m_remtssid   = remtssid;
        m_certformat = Objects.requireNonNull( certformat );
        m_reqdigest  = certformat.getDigestAlgorithm()!=null;
        m_certsize   = certformat.getCertificateSize();
    }


    @Override
    public final int getCertificateSize()
    {
        return m_certsize;
    }


    @Override
    public boolean requiresDigestedData()
    {
        return m_reqdigest;
    }


    @Override
    public HashAlgorithm getDigestAlgorithm()
    {
        return m_certformat.getDigestAlgorithm();
    }


    @Override
    public SingleProofFormat getCertificateFormat()
    {
        return m_certformat;
    }

}

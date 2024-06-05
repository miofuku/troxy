package reptor.distrbt.certify.trusted;

import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.certify.trusted.TrinxCommands.UnserializedTrinxCommand;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.MutableData;
import reptor.jlib.hash.HashAlgorithm;


public class TrustedMacCertifier implements BidirectionalCertifier
{

    private final Trinx                     m_trinx;
    private final short                     m_tssid;
    private final short                     m_remtssid;
    private final SingleTrustedMacFormat    m_certformat;
    private final boolean                   m_reqdigest;
    private final int                       m_certsize;
    private final UnserializedTrinxCommand  m_cmd = new UnserializedTrinxCommand();


    public TrustedMacCertifier(Trinx trinx, short remtssid, SingleTrustedMacFormat certformat)
    {
        Preconditions.checkArgument( certformat.getProofAlgorithm().getProofType().equals( TrustedCertifying.TRUSTED_MAC ) );
        Preconditions.checkArgument( certformat.getCertificateSize()==trinx.getMacCertificateSize() );

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
    public SingleTrustedMacFormat getCertificateFormat()
    {
        return m_certformat;
    }


    @Override
    public void createCertificate(Data data, MutableData out)
    {
        m_trinx.executeCommand( m_cmd.createTrustedMac().tss( m_tssid ).message( data, out ) );
    }


    @Override
    public boolean verifyCertificate(Data data, Data certdata)
    {
        m_trinx.executeCommand( m_cmd.verifyTrustedMac().tss( m_remtssid ).message( data, certdata ) );
        return m_cmd.isCertificateValid();
    }

}

package reptor.replct.common.settings;

import com.google.common.base.Preconditions;

import reptor.distrbt.certify.BidirectionalCertification;
import reptor.distrbt.certify.CertificationMethod;
import reptor.distrbt.certify.ProofAlgorithm;
import reptor.distrbt.certify.debug.DebugCertification;
import reptor.distrbt.certify.debug.DebugCertifying;
import reptor.distrbt.certify.debug.DigestMacAlgorithm;
import reptor.distrbt.certify.debug.PlainSingleDigestMacFormat;
import reptor.distrbt.certify.hash.HashCertifying;
import reptor.distrbt.certify.hash.HashProofAlgorithm;
import reptor.distrbt.certify.hash.PlainSingleHashFormat;
import reptor.distrbt.certify.mac.Authenticating;
import reptor.distrbt.certify.mac.AuthenticatorCertification;
import reptor.distrbt.certify.mac.MacAlgorithm;
import reptor.distrbt.certify.mac.PlainSingleMacFormat;
import reptor.distrbt.certify.signature.PlainSingleSignatureFormat;
import reptor.distrbt.certify.signature.SignatureAlgorithm;
import reptor.distrbt.certify.signature.Signing;
import reptor.distrbt.certify.suites.AuthorityInstances;
import reptor.distrbt.certify.suites.ConnectionKeys;
import reptor.distrbt.certify.trusted.TrustedAlgorithm;
import reptor.distrbt.certify.trusted.TrustedCertifying;
import reptor.distrbt.certify.trusted.TrustedMacCertification;
import reptor.jlib.hash.HashAlgorithm;


public class CertificationMethodBuilder
{

    private boolean         m_usedefault    = true;
    private boolean         m_dummy         = false;
    private ProofAlgorithm  m_proofalgo;
    private boolean         m_predigest;
    private HashAlgorithm   m_predigalgo;
    private CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> m_defcert = null;


    public CertificationMethodBuilder(ProofAlgorithm proofalgo, HashAlgorithm predigalgo)
    {
        m_proofalgo  = proofalgo;
        m_predigest  = predigalgo!=null;
        m_predigalgo = predigalgo;
    }


    public CertificationMethodBuilder load(SettingsReader reader, String basekey)
    {
        String algoname = reader.getString( basekey + ".cert_algo", "default" );

        if( algoname.equals( "default" ) )
            m_usedefault = true;
        else
        {
            m_proofalgo = parseProofAlgorith( algoname );
            m_dummy     = reader.getBool( basekey + ".dummy_certs", m_dummy );
            m_predigest = reader.getBool( basekey + ".pre_digest", m_predigest );

            Preconditions.checkState( !m_predigest || m_predigalgo!=null );
        }

        return this;
    }


    public CertificationMethodBuilder
            defaultCertification(CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> defcert)
    {
        m_defcert = defcert;

        return this;
    }


    public CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> create()
    {
        if( m_usedefault && m_defcert!=null  )
            return m_defcert;
        else if( m_proofalgo==null )
            return new DebugCertification( null, 0, m_predigalgo );
        else
            return createCertification( m_dummy, m_proofalgo, m_predigest ? m_predigalgo : null );
    }


    private ProofAlgorithm parseProofAlgorith(String algoname)
    {
        if( algoname.equals( "none" ) )
            return null;

        ProofAlgorithm proofalgo;

        if( ( proofalgo = Authenticating.tryParseMacAlgorithm( algoname ) )!=null )
            return proofalgo;

        if( ( proofalgo = Signing.tryParseSignatureAlgorithm( algoname ) )!=null )
            return proofalgo;

        if( ( proofalgo = TrustedCertifying.tryParseTrustedAlgorithm( algoname ) )!=null )
            return proofalgo;

        if( ( proofalgo = DebugCertifying.tryParseDigestMacAlgorithm( algoname ) )!=null )
            return proofalgo;

        if( ( proofalgo = HashCertifying.tryParseHashProofAlgorithm( algoname ) )!=null )
            return proofalgo;

        throw new IllegalArgumentException( "Unknown certification algorithm " + algoname );
    }


    private CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys>
            createCertification(boolean dummy, ProofAlgorithm proofalgo, HashAlgorithm predigalgo)
    {
        if( proofalgo instanceof MacAlgorithm )
        {
            MacAlgorithm macalgo = (MacAlgorithm) proofalgo;

            BidirectionalCertification<? super AuthorityInstances, ? super ConnectionKeys> certmethod =
                    dummy ? new DebugCertification( macalgo, predigalgo ) :
                            new PlainSingleMacFormat( macalgo, predigalgo );

            return new AuthenticatorCertification<>( certmethod );
        }
        else if( proofalgo instanceof SignatureAlgorithm )
        {
            SignatureAlgorithm sigalgo = (SignatureAlgorithm) proofalgo;

            return dummy ? new DebugCertification( sigalgo, predigalgo ) :
                           new PlainSingleSignatureFormat( sigalgo, predigalgo );
        }
        else if( proofalgo instanceof TrustedAlgorithm )
        {
            TrustedAlgorithm tssalgo = (TrustedAlgorithm) proofalgo;

            return dummy ? new DebugCertification( tssalgo, predigalgo ) :
                           new TrustedMacCertification( tssalgo, predigalgo );
        }
        else if( proofalgo instanceof DigestMacAlgorithm )
        {
            DigestMacAlgorithm digalgo = (DigestMacAlgorithm) proofalgo;

            BidirectionalCertification<? super AuthorityInstances, ? super ConnectionKeys> certmethod =
                    dummy ? new DebugCertification( digalgo, predigalgo ) :
                            new PlainSingleDigestMacFormat( digalgo, predigalgo );

            return new AuthenticatorCertification<>( certmethod );
        }
        else if( proofalgo instanceof HashProofAlgorithm )
        {
            HashProofAlgorithm hasalgo = (HashProofAlgorithm) proofalgo;

            return dummy ? new DebugCertification( hasalgo, predigalgo ) :
                           new PlainSingleHashFormat( hasalgo, predigalgo );
        }
        else
        {
            throw new IllegalStateException();
        }
    }

}

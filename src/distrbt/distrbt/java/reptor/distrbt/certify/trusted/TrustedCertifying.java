package reptor.distrbt.certify.trusted;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import com.google.common.base.Preconditions;

import reptor.distrbt.certify.Certifier;
import reptor.distrbt.certify.Certifiying;
import reptor.distrbt.certify.KeyType;
import reptor.distrbt.certify.ProofAlgorithm;
import reptor.distrbt.certify.ProofType;
import reptor.distrbt.certify.mac.Authenticating;
import reptor.distrbt.common.data.MutableData;
import reptor.jlib.hash.HashAlgorithm;


public class TrustedCertifying extends Certifiying
{

    public static final ProofType TRUSTED_GROUP     = proofType( "TGRP", true, true, false, false );
    public static final ProofType TRUSTED_MAC       = proofType( "TMAC", true, true, true, false );
    public static final ProofType TRUSTED_COUNTER   = proofType( "TCTR", true, true, true, true );

    public static final KeyType TSS_ID = new KeyType() {};

    public static final TrustedAlgorithm TMAC_HMAC_SHA256 = trustedAlgorithm( TRUSTED_MAC, Authenticating.HMAC_SHA256 );
    public static final TrustedAlgorithm TCTR_HMAC_SHA256 = trustedAlgorithm( TRUSTED_COUNTER, Authenticating.HMAC_SHA256 );


    public static TrustedAlgorithm tryParseTrustedAlgorithm(String s)
    {
        switch( s.toUpperCase() )
        {
        case "TMAC_HMAC_SHA256":
            return TMAC_HMAC_SHA256;
        case "TCTR_HMAC_SHA256":
            return TCTR_HMAC_SHA256;
        default:
            return null;
        }
    }


    public static TrustedAlgorithm trustedAlgorithm(ProofType type, ProofAlgorithm basealgo)
    {
        return new TrustedAlgorithm()
        {
            @Override
            public String toString()
            {
                return getName();
            }

            @Override
            public String getName()
            {
                return algorithmName( type, basealgo );
            }

            @Override
            public ProofType getProofType()
            {
                return type;
            }

            @Override
            public int getMaximumProofSize()
            {
                return basealgo.getMaximumProofSize();
            }

            @Override
            public Set<KeyType> getRequiredKeyTypes()
            {
                return Collections.singleton( TSS_ID );
            }

            @Override
            public ProofAlgorithm getBaseAlgorithm()
            {
                return basealgo;
            }

            @Override
            public boolean equals(Object obj)
            {
                if( obj==this )
                    return true;

                if( obj==null || !( obj instanceof TrustedAlgorithm ) )
                    return false;

                TrustedAlgorithm other = (TrustedAlgorithm) obj;

                return other.getBaseAlgorithm().equals( getBaseAlgorithm() );
            }

            @Override
            public int hashCode()
            {
                return Objects.hash( TrustedAlgorithm.class, getBaseAlgorithm() );
            }
        };
    }

    public static SingleTrustedMacFormat plainTrustedMacFormat(TrustedAlgorithm proofalgo, HashAlgorithm digalgo)
    {
        Preconditions.checkArgument( proofalgo.getProofType().equals( TRUSTED_MAC ) );

        return new SingleTrustedMacFormat()
        {
            @Override
            public String toString()
            {
                return formatName( proofalgo, digalgo );
            }

            @Override
            public HashAlgorithm getDigestAlgorithm()
            {
                return digalgo;
            }

            @Override
            public int getCertificateSize()
            {
                return proofalgo.getMaximumProofSize();
            }

            @Override
            public int getMaximumProofSize()
            {
                return proofalgo.getMaximumProofSize();
            }

            @Override
            public int getProofOffset()
            {
                return 0;
            }

            @Override
            public TrustedAlgorithm getProofAlgorithm()
            {
                return proofalgo;
            }

            @Override
            public void writeCertificateTo(MutableData out, Certifier certifier, byte[] proofdata, int proofoffset, int proofsize)
            {
                out.readFrom( proofdata, proofoffset, proofsize, 0 );
            }
        };
    }

    public static SingleCounterFormat plainTrustedCounterFormat(TrustedAlgorithm proofalgo, HashAlgorithm digalgo)
    {
        Preconditions.checkArgument( proofalgo.getProofType().equals( TRUSTED_COUNTER ) );

        return new SingleCounterFormat()
        {
            @Override
            public String toString()
            {
                return formatName( proofalgo, digalgo );
            }

            @Override
            public HashAlgorithm getDigestAlgorithm()
            {
                return digalgo;
            }

            @Override
            public int getCertificateSize()
            {
                return proofalgo.getMaximumProofSize()+Integer.BYTES;
            }

            @Override
            public int getMaximumProofSize()
            {
                return proofalgo.getMaximumProofSize();
            }

            @Override
            public int getProofOffset()
            {
                return 0;
            }

            @Override
            public int getCounterOffset()
            {
                return proofalgo.getMaximumProofSize();
            }

            @Override
            public TrustedAlgorithm getProofAlgorithm()
            {
                return proofalgo;
            }

            @Override
            public void writeCertificateTo(MutableData out, Certifier certifier, byte[] proofdata, int proofoffset, int proofsize)
            {
                out.readFrom( proofdata, proofoffset, proofsize, 0 );
            }

        };
    }
}

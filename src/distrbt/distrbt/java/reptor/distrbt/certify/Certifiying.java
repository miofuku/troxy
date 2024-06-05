package reptor.distrbt.certify;

import java.util.Objects;

import com.google.common.base.Joiner;

import reptor.jlib.entities.Named;
import reptor.jlib.hash.HashAlgorithm;


public class Certifiying
{

    public static String algorithmName(Named type, Named mode)
    {
        return algorithmName( type.getName(), mode.getName() );
    }


    public static String algorithmName(String typename, String modename)
    {
        return String.format( "%s_%s", typename, modename );
    }


    public static String algorithmName(String ... parts)
    {
        return Joiner.on( '_' ).join( parts );
    }


    public static String formatName(ProofAlgorithm proofalgo, HashAlgorithm digalgo)
    {
        return formatName( proofalgo.getName(), digalgo==null ? null : digalgo.getName() );
    }


    public static String formatName(String proofalgoname, String digalgoname)
    {
        return digalgoname==null ? String.format( "[%s]", proofalgoname ) :
                                   String.format( "[%s<%s]", proofalgoname, digalgoname );
    }


    public static String methodName(String authority, CertificateFormat certformat)
    {
        return String.format( "%s(%s)", certformat, authority );
    }


    public static String modifierName(CertificationMethod<?, ?> method, String moddesc)
    {
        return String.format( "%s(%s)", method, moddesc );
    }


    public static String compoundName(String cert, String verf)
    {
        return formatName( compoundMethod( cert, verf ), null );
    }


    public static String compoundMethod(String cert, String verf)
    {
        return String.format( "%s<>%s", cert, verf );
    }


    public static ProofType proofType(String name, boolean integrity, boolean isauthentic,
                                      boolean isnonrepudiable, boolean isunique)
    {
        Objects.requireNonNull( name );

        return new ProofType()
        {
            @Override
            public String toString()
            {
                return name;
            }

            @Override
            public String getName()
            {
                return name;
            }

            @Override
            public boolean ensuresIntegrity()
            {
                return integrity;
            }

            @Override
            public boolean isAuthentic()
            {
                return isauthentic;
            }

            @Override
            public boolean isNonRepudiable()
            {
                return isnonrepudiable;
            }

            @Override
            public boolean isUnique()
            {
                return isunique;
            }
        };
    }

}

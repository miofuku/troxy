package reptor.jlib.hash;

import java.security.MessageDigest;

import reptor.jlib.entities.Named;


public interface HashAlgorithm extends Named
{

    int             getHashSize();

    MessageDigest   digester();

    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();

}

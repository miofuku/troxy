package reptor.jlib.hash;

import java.util.Objects;


public abstract class AbstractHashAlgorithm implements HashAlgorithm
{

    @Override
    public boolean equals(Object obj)
    {
        if( obj==this )
            return true;

        if( obj==null || !( obj instanceof HashAlgorithm ) )
            return false;

        HashAlgorithm other = (HashAlgorithm) obj;

        return other.getName().equals( getName() ) && other.getHashSize()==getHashSize();
    }


    @Override
    public int hashCode()
    {
        return Objects.hash( getName(), getHashSize() );
    }

}

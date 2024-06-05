package reptor.distrbt.certify;

import reptor.chronos.ImmutableObject;


public interface KeyType extends ImmutableObject
{
    @Override
    boolean equals(Object obj);
    @Override
    int     hashCode();
}

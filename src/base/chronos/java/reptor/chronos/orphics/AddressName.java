package reptor.chronos.orphics;

import java.util.Objects;

import reptor.chronos.ChronosAddress;


public class AddressName implements ChronosAddress
{

    private final String    m_name;


    public AddressName(String name)
    {
        m_name = Objects.requireNonNull( name );
    }


    @Override
    public String toString()
    {
        return m_name;
    }

}

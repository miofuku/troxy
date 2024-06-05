package reptor.replct.common.settings;

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;


public class SettingsReader
{

    private final Properties    m_props;


    public SettingsReader(Properties props)
    {
        m_props = Objects.requireNonNull( props );
    }


    public boolean isDefault(String key)
    {
        String val = m_props.getProperty( key );

        return val==null || val.equals( "default" );
    }


    public boolean getBool(String key, boolean def)
    {
        String val = m_props.getProperty( key );

        return val!=null ? Boolean.parseBoolean( val ) : def;
    }


    public byte getByte(String key, byte def)
    {
        String val = m_props.getProperty( key );

        return val!=null ? Byte.parseByte( val ) : def;
    }


    public short getShort(String key, short def)
    {
        String val = m_props.getProperty( key );

        return val!=null ? Short.parseShort( val ) : def;
    }


    public int getInt(String key, int def)
    {
        String val = m_props.getProperty( key );

        return val!=null ? Integer.parseInt( val ) : def;
    }


    public long getLong(String key, long def)
    {
        String val = m_props.getProperty( key );

        return val!=null ? Long.parseLong( val ) : def;
    }


    public String getString(String key, String def)
    {
        String val = m_props.getProperty( key );

        return val!=null ? val : def;
    }


    public InetSocketAddress getAddress(String key)
    {
        String[] ep = m_props.getProperty( key ).split( ":" );

        return new InetSocketAddress( ep[0], Integer.parseInt( ep[1] ) );
    }


    public int[] getIntArray(String key, int[] def)
    {
        String val = m_props.getProperty( key );

        if( val==null || val.isEmpty() )
            return def;
        else
        {
            List<Integer> array = new LinkedList<>();

            String[] parts = val.split( "\\s*,\\s*" );

            for( String p : parts )
            {
                if( !p.contains( "-" ) )
                    array.add( Integer.parseInt( p ) );
                else
                {
                    String[] r = p.split( "\\s*-\\s*", 2 );
                    int c = Integer.parseInt( r[ 0 ] );
                    int l = Integer.parseInt( r[ 1 ] );

                    while( c<=l )
                        array.add( c++ );
                }
            }

            int[] ret = new int[ array.size() ];
            int i = 0;
            for( Integer p : array )
                ret[ i++ ] = p;

            return ret;
        }
    }

}

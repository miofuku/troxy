package reptor.replct.common.modules;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Shorts;

import reptor.replct.common.settings.SettingsReader;


public abstract class AbstractProtocolComponent
{

    protected int       m_nworkers = 1;
    protected boolean   m_isactive = false;


    public int getNumberOfWorkers()
    {
        return m_nworkers;
    }


    public AbstractProtocolComponent activate()
    {
        Preconditions.checkState( !m_isactive );

        m_isactive = true;

        return this;
    }


    protected int[] uniformMapping(int linkedworker)
    {
        int[] map = new int[ m_nworkers ];
        Arrays.fill( map, linkedworker );

        return map;
    }


    protected int[][] uniformArrayMapping(int linkedworker)
    {
        int[]   val = new int[] { linkedworker };
        int[][] map = new int[ m_nworkers ][];
        Arrays.fill( map, val );

        return map;
    }


    protected void loadBasicSettings(SettingsReader reader, String group)
    {
        Preconditions.checkState( !m_isactive );

        m_nworkers = reader.getInt( getKey( group, "number" ), m_nworkers );
    }


    protected int[] loadMapping(SettingsReader reader, String group, String name, int defmap)
    {
        int[] range = reader.getIntArray( getKey( group, name ), null );
        int[] map   = new int[ m_nworkers ];

        if( range==null )
        {
            for( int wrkno=0; wrkno<m_nworkers; wrkno++ )
                map[ wrkno ] = reader.getInt( getKey( group, wrkno + "." + name ), defmap );
        }
        else if( range.length==1 )
        {
            Arrays.fill( map, Shorts.checkedCast( range[ 0 ] ) );
        }
        else if( range.length<m_nworkers )
        {
            throw new IllegalStateException( getKey( group, name ) );
        }
        else
        {
            for( int wrkno=0; wrkno<m_nworkers; wrkno++ )
                map[ wrkno ] = range[ wrkno ];
        }

        return map;
    }


    protected int[][] loadArrayMapping(SettingsReader reader, String group, String name, int[] def)
    {
        int[]   range = reader.getIntArray( getKey( group, name ), null );
        int[][] map   = new int[ m_nworkers ][];


        if( range==null )
        {
            for( int wrkno=0; wrkno<m_nworkers; wrkno++ )
                map[ wrkno ] = reader.getIntArray( getKey( group, wrkno + "." + name ), def );
        }
        else if( range.length==1 )
        {
            Arrays.fill( map, new int[] { range[ 0 ] } );
        }
        else if( range.length<m_nworkers )
        {
            throw new IllegalStateException( getKey( group, name ) );
        }
        else
        {
            for( int wrkno=0; wrkno<m_nworkers; wrkno++ )
                map[ wrkno ] = new int[] { range[ wrkno ] };
        }

        return map;
    }


    protected String getKey(String group, String add)
    {
        return "stages." + group + "." + add;
    }


    protected int[][] reverseIndex(int[] idx, int targetno)
    {
        int[] cnt = new int[ targetno ];

        for( int e : idx )
            cnt[ e ]++;

        int[][] revidx = new int[ targetno ][];

        for( int target=0; target<targetno; target++ )
        {
            revidx[ target ] = new int[ cnt[ target ] ];

            for( int source=0, i=0; source<idx.length; source++ )
                if( idx[ source ]==target )
                    revidx[ target ][ i++ ] = source;
        }

        return revidx;
    }


    protected int[][] reverseIndex(int[][] idx, int targetno)
    {
        int[] cnt = new int[ targetno ];

        for( int[] es : idx)
        for( int e : es )
            cnt[ e ]++;

        int[][] revidx = new int[ targetno ][];

        for( int target=0; target<targetno; target++ )
        {
            revidx[ target ] = new int[ cnt[ target ] ];

            for( int source=0, i=0; source<idx.length; source++ )
                for( int e : idx[ source ] )
                if( e==target )
                    revidx[ target ][ i++ ] = source;
        }

        return revidx;
    }

}

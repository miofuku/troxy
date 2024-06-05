package reptor.replct.common.quorums;

import java.util.Arrays;

import com.google.common.base.Preconditions;

import reptor.jlib.collect.Slots;


public class Votes<V>
{

    private final Slots<V> m_voteclss;
    private final int[]    m_votecnts;

    private int m_leadingclass;


    public Votes(int capacity)
    {
        Preconditions.checkArgument( capacity>0 );

        m_voteclss     = new Slots<>( capacity );
        m_votecnts     = new int[ capacity ];
        m_leadingclass = 0;
    }


    public int addVote(V vote)
    {
        assert vote!=null;

        int clsidx = classIndex( vote );

        int cnt = ++m_votecnts[ clsidx ];

        if( clsidx!=m_leadingclass && cnt>getLeadingCount() )
            m_leadingclass = clsidx;

        return clsidx;
    }


    public int getNumberOfClasses()
    {
        return m_voteclss.size();
    }


    public int getLeadingClass()
    {
        return m_leadingclass;
    }


    public int getLeadingCount()
    {
        return m_votecnts[ m_leadingclass ];
    }


    public V getLeadingVote()
    {
        return m_voteclss.size()==0 ? null : m_voteclss.get( m_leadingclass );
    }


    public boolean isUnanimous()
    {
        return m_voteclss.size()<=1;
    }


    public void clear()
    {
        m_voteclss.clear();
        Arrays.fill( m_votecnts, 0 );
        m_leadingclass = 0;
    }


    private int classIndex(V vote)
    {
        for( int i=0; i<m_voteclss.size(); i++ )
        {
            V cls = m_voteclss.get( i );

            if( vote.equals( cls ) )
                return i;
        }

        int idx = m_voteclss.size();

        m_voteclss.put( idx, vote );

        return idx;
    }

}

package refit.pbfto.order;

import java.util.ArrayList;
import java.util.LinkedList;

import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.NetworkMessageRegistry;


public class OrderInstanceMessageStore<T extends NetworkMessage>
{
    protected long                           m_curseq;

    protected final ArrayList<LinkedList<T>> m_currentmsgs;

    protected final NetworkMessageRegistry m_msgreg;

    protected int m_size = 0;
//    protected final Map<Long, Collection<T>> m_futuremsgs;


    public OrderInstanceMessageStore(NetworkMessageRegistry msgreg)
    {
        m_msgreg = msgreg;

        int nbuckets = msgreg.capacity();

        m_currentmsgs = new ArrayList<>( nbuckets );
        for( int i = 0; i < nbuckets; i++ )
            m_currentmsgs.add( new LinkedList<>() );

//        m_futuremsgs = new HashMap<>();
    }


    public boolean init(long newseqno)
    {
        // Garbage-collect buffered messages for skipped instances
//        m_futuremsgs.keySet().removeIf( no -> no < newseqno );

        // Prepare message store for the new instance and demand instance execution if there are any buffered messages
        for( LinkedList<T> bucket : m_currentmsgs )
            bucket.clear();

        m_curseq = newseqno;
        m_size = 0;

//        Collection<T> curmsgs = m_futuremsgs.remove( m_curseq );
//
//        if( curmsgs != null )
//            for( T msg : curmsgs )
//                add( m_curseq, msg );
//
//        return curmsgs != null;
        return false;
    }


    public int size()
    {
        return m_size;
    }

    public void clear()
    {
        for( LinkedList<T> l : m_currentmsgs )
            l.clear();

        m_size = 0;
//        m_futuremsgs.clear();
    }


    public boolean add(long seqno, T msg)
    {
        assert seqno==m_curseq;

        m_currentmsgs.get( m_msgreg.magic( msg.getTypeID() ) ).add( msg );
        m_size++;

        return true;

//        else
//        {
//            // Buffer future messages
//            if( seqno > m_curseq )
//            {
//                Collection<T> futmsgs = m_futuremsgs.get( seqno );
//
//                if( futmsgs == null )
//                {
//                    futmsgs = new LinkedList<T>();
//                    m_futuremsgs.put( seqno, futmsgs );
//                }
//
//                futmsgs.add( msg );
//            }
//
//            return false;
//        }
    }


    public T remove(int typeid)
    {
        T msg = m_currentmsgs.get( m_msgreg.magic( typeid ) ).poll();

        if( msg!=null )
            m_size--;

        return msg;
    }
}

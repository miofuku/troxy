package reptor.distrbt.com.connect;

import java.util.function.IntFunction;

import reptor.chronos.ChronosTask;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.link.MulticastLink;
import reptor.chronos.orphics.AbstractMaster;
import reptor.chronos.orphics.Actor;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.map.NetworkMessageSink;
import reptor.distrbt.domains.SelectorDomainContext;


public class NetworkGroupConnection extends AbstractMaster<SelectorDomainContext>
                                    implements Actor, MulticastLink<NetworkMessage>
{

    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private final SchedulerContext<? extends SelectorDomainContext> m_master;

    private final int                       m_groupid;
    private final NetworkConnectionProvider m_connprov;
    private final PushNetworkTransmissionConnection[]       m_conns;
    private final NetworkMessageSink[]      m_outbound;


    public NetworkGroupConnection(SchedulerContext<? extends SelectorDomainContext> master, short groupid, short nconns,
                                  NetworkConnectionProvider connprov)
    {
        m_master   = master;
        m_groupid  = groupid;
        m_connprov = connprov;
        m_conns    = new PushNetworkTransmissionConnection[ nconns ];
        m_outbound = new NetworkMessageSink[ nconns ];
    }


    @Override
    protected SchedulerContext<? extends SelectorDomainContext> master()
    {
        return m_master;
    }


    //-------------------------------------//
    //           External State            //
    //-------------------------------------//

    @Override
    public String toString()
    {
        return String.format( "GRCONN[%04d]", m_groupid );
    }


    public int getGroupID()
    {
        return m_groupid;
    }


    @Override
    public int size()
    {
        return m_outbound.length;
    }


    public PushNetworkTransmissionConnection getConnection(int conno)
    {
        return m_conns[ conno ];
    }


    @Override
    public NetworkMessageSink getLink(int conno)
    {
        return m_outbound[ conno ];
    }


    //-------------------------------------//
    //       Configuration Interface       //
    //-------------------------------------//

    public void initGroup(IntFunction<Integer> connidfac, IntFunction<Object> msgcntxtfac)
    {
        for( int i=0; i<m_conns.length; i++)
        {
            Integer connid = connidfac.apply( i );

            if( connid!=null )
                initConnection( i, connid, msgcntxtfac );
        }
    }


    public void initConnection(int conno, int connid, IntFunction<Object> msgcntxtfac)
    {
        m_conns[ conno ] = m_connprov.connection( m_master.getDomainContext(), connid, msgcntxtfac );
        m_outbound[ conno ] = m_conns[ conno ].getOutbound();

        m_conns[ conno ].bindToMaster( this );
    }


    public void removeConnection(int conno)
    {
        m_conns[ conno ]    = null;
        m_outbound[ conno ] = null;
    }


    public void initReceiver(PushMessageSink<? super NetworkMessage> receiver)
    {
        for( PushNetworkTransmissionConnection conn : m_conns )
            if( conn!=null )
                conn.getInbound().initReceiver( receiver );
    }


    //-------------------------------------//
    //            Actor Interface          //
    //-------------------------------------//

    @Override
    public void enqueueUnicast(int conno, NetworkMessage msg)
    {
        m_outbound[ conno ].enqueueMessage( msg );
    }


    @Override
    public void enqueueMessage(NetworkMessage msg)
    {
        for( NetworkMessageSink conn : m_outbound )
            if( conn!=null )
                conn.enqueueMessage( msg );
    }


    @Override
    public boolean execute()
    {
        boolean isdone = true;

        for( PushNetworkTransmissionConnection conn : m_conns )
            if( conn!=null && conn.isReady() )
                isdone = conn.execute() && isdone;

        return isDone( isdone );
    }


    //-------------------------------------//
    //          Master Interface           //
    //-------------------------------------//

    @Override
    public void taskReady(ChronosTask task)
    {
        notifyReady();
    }

}

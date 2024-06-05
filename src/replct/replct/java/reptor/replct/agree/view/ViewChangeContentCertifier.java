package reptor.replct.agree.view;

import java.util.Collection;

import reptor.chronos.Orphic;
import reptor.jlib.collect.Slots;

// TODO: Rename to ...Instance?
public class ViewChangeContentCertifier<M extends ViewChangeNetworkMessage> implements Orphic
{

    public static enum State
    {
        STABLE,
        INITIATED,
        COMPLETE,
        CONFIRMED
    }

    private final byte     m_repno;
    private final Slots<M> m_msgshards;

    private int  m_viewno;
    private int  m_remconf;

    private State m_state;


    public ViewChangeContentCertifier(byte repno, int nshards)
    {
        m_repno     = repno;
        m_msgshards = new Slots<>( nshards );

        clearJob();
    }

    public ViewChangeContentCertifier<M> initJob(int viewno)
    {
        clearJob();

        m_viewno = viewno;

        return this;
    }

    private void clearJob()
    {
        m_viewno  = 0;
        m_remconf = m_msgshards.capacity();
        m_state   = State.STABLE;
        m_msgshards.clear();
    }

    public byte getReplicaNumber()
    {
        return m_repno;
    }

    public int getViewNumber()
    {
        return m_viewno;
    }

    public State getState()
    {
        return m_state;
    }

    public boolean isComplete()
    {
        return m_state==State.COMPLETE;
    }

    public boolean isConfirmed()
    {
        return m_state==State.CONFIRMED;
    }

    public Collection<M> getMessages()
    {
        return m_msgshards;
    }


    public boolean addMessage(M msg)
    {
        if( !matchesJob( msg ) )
            throw new UnsupportedOperationException();

        if( m_msgshards.containsKey( msg.getShardNumber() ) )
        {
            if( msg.equals( m_msgshards.get( msg.getShardNumber() ) ) )
                return false;
            else
                throw new UnsupportedOperationException();
        }

        m_msgshards.put( msg.getShardNumber(), msg );

        if( msg.isCertificateValid()==Boolean.TRUE )
            m_remconf--;

        return checkState();
    }

    public boolean messageConfirmed(M msg)
    {
        // TODO: If someone sent us a forged message with invalid certificated and if we didn't checked
        //       the certificate before we added it to the certifier or while adding it to the certifier,
        //       we would have to remove it here and reset the state of the certifier.
        //       Related problem: If we received two shards for the same message, we would have to find
        //       the correct one.
        if( msg.isCertificateValid()!=true )
            throw new UnsupportedOperationException();

        m_remconf--;

        return checkState();
    }

    private boolean matchesJob(M msg)
    {
        return msg.getViewNumber()==m_viewno && msg.getSender()==m_repno;
    }

    private boolean checkState()
    {
        State nextstate;

        if( m_msgshards.size()==0 )
            nextstate = State.STABLE;
        else if( m_remconf==0 )
            nextstate = State.CONFIRMED;
        else if( m_msgshards.emptySlotsCount()==0 )
            nextstate = State.COMPLETE;
        else
            nextstate = State.INITIATED;

        if( nextstate==m_state )
            return false;
        else
        {
            m_state = nextstate;
            return true;
        }
    }

}

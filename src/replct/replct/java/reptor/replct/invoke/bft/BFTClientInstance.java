package reptor.replct.invoke.bft;

import reptor.distrbt.com.Message;
import reptor.distrbt.common.data.ImmutableData;
import reptor.replct.MessageHandler;
import reptor.replct.invoke.InvocationMessages;
import reptor.replct.invoke.InvocationMessages.Reply;
import reptor.replct.invoke.InvocationMessages.Request;
import reptor.replct.service.ServiceCommand;


public abstract class BFTClientInstance implements MessageHandler<Message>
{

    //-------------------------------------//
    //               Types                 //
    //-------------------------------------//

    public enum State
    {
        INITIALIZED,
        REQUESTED,
        STABLE;
    }


    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private final short    m_clino;
    private final int      m_slotno;

    private State          m_state     = null;
    private long           m_invno     = -1;
    private short          m_contact   = -1;
    private ServiceCommand m_command   = null;
    private boolean        m_isbarrier = false;


    public BFTClientInstance(short clino, int slotno)
    {
        m_clino  = clino;
        m_slotno = slotno;
    }


    //-------------------------------------//
    //       Configuration Interface       //
    //-------------------------------------//

    public abstract void init(long invno, short contactno, ServiceCommand command, boolean resent);


    protected void initInvocation(long invno, short contactno, ServiceCommand command, boolean isbarrier)
    {
        m_invno     = invno;
        m_contact   = contactno;
        m_command   = command;
        m_isbarrier = isbarrier;

        invocationInitilized();
    }


    protected short initialContact()
    {
        return m_contact;
    }


    private void invocationInitilized()
    {
        advanceState( State.INITIALIZED );
    }


    //-------------------------------------//
    //           External State            //
    //-------------------------------------//

    @Override
    public String toString()
    {
        return String.format( "INVCI[%03d-%03d][%1$d:%d]", m_clino, m_slotno, m_invno );
    }


    public int getSlotNumber()
    {
        return m_slotno;
    }


    public long getInvocationNumber()
    {
        return m_invno;
    }


    public boolean isBarrier()
    {
        return m_isbarrier;
    }


    public State getState()
    {
        return m_state;
    }


    public boolean isStable()
    {
        return m_state==State.STABLE;
    }


    public ServiceCommand getCommand()
    {
        return m_command;
    }


    public abstract byte getCurrentContactReplica();


    //-------------------------------------//
    //          Handler Interface          //
    //-------------------------------------//

    @Override
    public boolean handleMessage(Message msg)
    {
        switch( msg.getTypeID() )
        {
        case InvocationMessages.REPLY_ID:
            return handleReply( (Reply) msg );
        default:
            throw new UnsupportedOperationException( msg.toString() );
        }
    }


    public abstract void startInvocation();


    protected void commandRequested()
    {
        advanceState( State.REQUESTED );
    }


    protected Request createRequest(boolean useroopt, boolean ispanic)
    {
        Request request = new Request( m_clino, m_invno, m_command.getData(), useroopt, ispanic );
        request.setValid();

        return request;
    }


    public abstract boolean handleReply(Reply reply);


    protected void resultStable(ImmutableData result)
    {
        m_command.setResult( result );

        advanceState( State.STABLE );
    }


    //-------------------------------------//
    //        Additional Internals         //
    //-------------------------------------//

    private void advanceState(State state)
    {
        m_state = state;
    }


    protected void setBarrier(boolean value)
    {
        m_isbarrier = value;
    }

}

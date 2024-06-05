package reptor.tbft;

import java.nio.ByteBuffer;

import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.io.CompactingSinkBuffers;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.replct.invoke.InvocationMessages.Request;
import reptor.replct.invoke.bft.BFTInvocation;


public class TroxyClientResults extends TroxyNetworkResults
{

    private final ByteBuffer m_msgoutbuf;
    private int              m_reqmsgbuf = UnbufferedDataSource.NO_PENDING_DATA;
    private long             m_lastinv   = BFTInvocation.NO_INVOCATION;


    public TroxyClientResults(ByteBuffer msgoutbuf)
    {
        m_msgoutbuf = msgoutbuf;

        CompactingSinkBuffers.clear( m_msgoutbuf );
    }


    public int getRequiredMessageBufferSize()
    {
        return m_reqmsgbuf;
    }


    public void setRequiredMessageBufferSize(int reqmsgbuf)
    {
        assert reqmsgbuf<=m_msgoutbuf.capacity() || reqmsgbuf==UnbufferedDataSource.NO_PENDING_DATA :
            "capacity " + m_msgoutbuf.capacity() + " required " + reqmsgbuf;

        m_reqmsgbuf = reqmsgbuf;
    }


    public ByteBuffer getMessageBuffer()
    {
        return m_msgoutbuf;
    }


    public boolean hasMessageData()
    {
        return CompactingSinkBuffers.hasData( m_msgoutbuf );
    }


    public ByteBuffer startMessageDataProcessing()
    {
        return CompactingSinkBuffers.startDataProcessing( m_msgoutbuf );
    }


    public void finishMessageDataProcessing()
    {
        CompactingSinkBuffers.finishDataProcessing( m_msgoutbuf );
    }


    public long getLastFinishedInvocation()
    {
        return m_lastinv;
    }


    public void setLastFinishedInvocation(long invno)
    {
        m_lastinv = invno;
    }


    public Request createWriteRequest(Request request, MessageMapper mapper, BidirectionalCertifier propcertif)
    {
        Request surrequest = new Request( request.getSender(), request.getNumber(), request.getCommand(), true, false, false );
        surrequest.setValid();
        mapper.certifyAndSerializeMessage( surrequest, propcertif );
        return surrequest;
    }


    @Override
    public String toString()
    {
        return String.format("{ bb: %s, reqmsgbuf: %s, lastinv: %s, net: %s }", m_msgoutbuf, m_reqmsgbuf, m_lastinv, super.toString());
    }

}
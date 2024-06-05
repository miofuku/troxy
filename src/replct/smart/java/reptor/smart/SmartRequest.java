package reptor.smart;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import bftsmart.tom.core.messages.TOMMessage;


public class SmartRequest extends Request
{

    private final TOMMessage m_tommsg;
    private final byte[]     m_msgcnt;


    public SmartRequest(TOMMessage tommsg)
    {
        super( (short) tommsg.getSession(), tommsg.getSequence(),
                ImmutableData.wrap( tommsg.getContent() ), false );

        this.m_tommsg = tommsg;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream( baos );

        try
        {
            tommsg.wExternal( dos );
            dos.flush();
        }
        catch( IOException e )
        {
            throw new IllegalStateException( e );
        }

        m_msgcnt = baos.toByteArray();
    }


    private SmartRequest(SmartRequest org)
    {
        super( org.getSender(), org.getNumber(), org.getCommand(), false );

        m_tommsg = org.m_tommsg;
        m_msgcnt = org.m_msgcnt;
    }


    @Override
    public int getTypeID()
    {
        return ClientMessages.REQUEST_ID;
    }


    @Override
    public SmartRequest clone()
    {
        SmartRequest msg = new SmartRequest( this );
        msg.setValid();

//        msg.setMessageSize( getMessageSize() );
        msg.setContentSizes( getPlainPrefixSize(), getContentSize() );
        msg.setCertificateSize( getCertificateSize() );
        msg.setMessageData( getMessageData().copy() );

        return msg;
    }

    @Override
    public int calculateTypePlainPrefixSize(MessageMapper mapper)
    {
        return m_msgcnt.length;
    }


    @Override
    public void writeTypeContentTo(ByteBuffer out, MessageMapper mapper)
    {
        out.put( m_msgcnt );
    }


    public TOMMessage getTOMMessage()
    {
        return m_tommsg;
    }

}

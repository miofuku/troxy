package reptor.tbft.adapt;

import com.google.common.base.Preconditions;

import reptor.distrbt.com.MessageVerifier;
import reptor.distrbt.com.VerificationException;
import reptor.distrbt.common.data.Data;
import reptor.replct.agree.order.OrderMessages.Command;
import reptor.tbft.Troxy;


public class TroxyProposalVerifier implements MessageVerifier<Command>
{

    private final Troxy     m_troxy;
    private final int       m_osno;

    private final Data[]    m_cmdbuf;


    public TroxyProposalVerifier(Troxy troxy, int osno, int maxbatchsize)
    {
        Preconditions.checkArgument( maxbatchsize>0 );

        m_troxy  = troxy;
        m_osno   = osno;
        m_cmdbuf = new Data[ maxbatchsize ];
    }


    @Override
    public void verifyMessage(Command msg) throws VerificationException
    {
        m_cmdbuf[ 0 ] = msg.getMessageData();
        m_troxy.verifyProposal( m_osno, m_cmdbuf, 1 );
        msg.setCertificateValid( true );
    }


    @Override
    public void verifyMessages(Command[] msgs) throws VerificationException
    {
        for( int i=0; i<msgs.length; i++ )
            m_cmdbuf[ i ] = msgs[ i ].getMessageData();

        m_troxy.verifyProposal( m_osno, m_cmdbuf, msgs.length );

        for( int i=0; i<msgs.length; i++ )
            msgs[ i ].setCertificateValid( true );
    }

}

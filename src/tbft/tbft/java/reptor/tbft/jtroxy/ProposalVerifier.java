package reptor.tbft.jtroxy;

import java.io.IOException;

import reptor.distrbt.certify.Verifier;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.VerificationException;
import reptor.distrbt.common.data.Data;
import reptor.replct.invoke.InvocationMessages;


public class ProposalVerifier
{

    private final MessageMapper m_mapper;
    private final Verifier      m_propverif;


    public ProposalVerifier(MessageMapper mapper, Verifier propverif)
    {
        m_mapper    = mapper;
        m_propverif = propverif;
    }


    public void verifyProposal(Data[] cmdbuf, int ncmds, boolean usecache, byte repno, ClientHandler[] clients) throws VerificationException
    {
        for( int i=0; i<ncmds; i++ )
        {
            try
            {
                NetworkMessage cmd = m_mapper.readMessageFrom( cmdbuf[ i ] );

                if (usecache)
                {
                    if (cmd.getTypeID() == InvocationMessages.REQUEST_ID)
                    {
                        if (cmd.getSender()%3!=repno && !((InvocationMessages.Request) cmd).isReadRequest())
                        {
                            clients[cmd.getSender()].addToRequests((InvocationMessages.Request) cmd);
//                            System.out.println("Verify write request, store: "+cmd.toString());
                        }
                    }
                }

                m_mapper.verifyMessage( cmd, m_propverif );
            }
            catch( IOException e )
            {
                throw new VerificationException( "Could not decode command " + i, e );
            }
        }
    }

}

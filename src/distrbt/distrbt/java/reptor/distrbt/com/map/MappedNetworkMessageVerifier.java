package reptor.distrbt.com.map;

import java.util.Objects;

import reptor.distrbt.certify.ArrayVerifierGroup;
import reptor.distrbt.certify.Verifier;
import reptor.distrbt.certify.VerifierGroup;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.MessageVerifier;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.VerificationException;


public class MappedNetworkMessageVerifier<M extends NetworkMessage> implements MessageVerifier<M>
{

    private final MessageMapper m_mapper;
    private final VerifierGroup m_verifiers;


    public MappedNetworkMessageVerifier(MessageMapper mapper, Verifier[] verifiers)
    {
        m_mapper    = Objects.requireNonNull( mapper );
        m_verifiers = new ArrayVerifierGroup( verifiers );
    }


    public MappedNetworkMessageVerifier(MessageMapper mapper, VerifierGroup verifiers)
    {
        m_mapper    = Objects.requireNonNull( mapper );
        m_verifiers = Objects.requireNonNull( verifiers );
    }


    @Override
    public void verifyMessage(M msg) throws VerificationException
    {
        m_mapper.verifyMessage( msg, m_verifiers.getVerifier( msg.getSender() ) );
    }


    @Override
    public void verifyMessages(M[] msgs) throws VerificationException
    {
        for( M msg : msgs )
            verifyMessage( msg );
    }

}

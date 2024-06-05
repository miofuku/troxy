package reptor.distrbt.com;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.IntFunction;

import reptor.chronos.Commutative;
import reptor.distrbt.certify.Certifier;
import reptor.distrbt.certify.Verifier;
import reptor.distrbt.certify.VerifierGroup;
import reptor.distrbt.common.data.Data;


// TODO: Decouple MessageMapper, CommonHeaderMapper, ExternalMessageFactory, etc.
// TODO: Separate basic MessageMapper for single messages and strategies for
//       more complex formats (arrays of messages, complete message structures)
@Commutative
public interface MessageMapper extends MessageDigester
{

    int calculateMessageSize(NetworkMessage msg);

    <M extends NetworkMessage>
        int calculateMessageSizes(M[] msgs);


    void serializeMessage(NetworkMessage msg);

    void certifyAndSerializeMessage(NetworkMessage msg, Certifier certifier);


    boolean writeMessageTo(ByteBuffer out, NetworkMessage msg);

    boolean writeMessageContentTo(ByteBuffer out, NetworkMessage msg);

    <M extends NetworkMessage>
        void writeMessagesTo(ByteBuffer out, M[] msgs);


    ByteBuffer outputBuffer(NetworkMessage msg);


    void verifyMessage(NetworkMessage msg, Verifier verifier) throws VerificationException;

    default void verifyMessage(NetworkMessage msg, VerifierGroup verifiers) throws VerificationException
    {
        verifyMessage( msg, verifiers.getVerifier( msg.getSender() ) );
    }

    void verifyInnerMessage(NetworkMessage msg, NetworkMessage innermsg, Verifier verifier)
            throws VerificationException;

    default void verifyInnerMessage(NetworkMessage msg, NetworkMessage innermsg, VerifierGroup verifiers)
            throws VerificationException
    {
        verifyInnerMessage( msg, innermsg, verifiers.getVerifier( msg.getSender() ) );
    }

    <M extends NetworkMessage, V extends MessageVerifier<? super M>>
        void verifyInnerMessage(NetworkMessage msg, M innermsg, V verifier)
                throws VerificationException;

    <M extends NetworkMessage>
        void verifyInnerMessages(NetworkMessage msg, M[] innermsgs, VerifierGroup verifiers)
                throws VerificationException;

    <M extends NetworkMessage, V extends MessageVerifier<? super M>>
        void verifyInnerMessages(NetworkMessage msg, M[] innermsgs, V verifier)
                throws VerificationException;


    Object createSourceContext(Integer srcid, IntFunction<Object> msgcntxtfac);


    NetworkMessage tryReadMessageFrom(ByteBuffer in, Object srccntxt) throws IOException;

    NetworkMessage readMessageFrom(ByteBuffer in) throws IOException;

    NetworkMessage readMessageFrom(Data msgdata) throws IOException;

    <M extends NetworkMessage>
            M[] readMessagesFrom(ByteBuffer in, M[] out, Class<? extends NetworkMessage> clazz) throws IOException;

}

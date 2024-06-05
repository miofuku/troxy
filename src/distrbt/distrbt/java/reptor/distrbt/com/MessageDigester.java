package reptor.distrbt.com;

import reptor.chronos.Commutative;
import reptor.chronos.Orphic;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.ImmutableData;
import reptor.jlib.hash.HashAlgorithm;


@Commutative
public interface MessageDigester extends Orphic
{

    HashAlgorithm getHashAlgorithm();

    void prepareInnerMessageForDigestion(NetworkMessage innermsg);

    <M extends NetworkMessage>
        void prepareInnerMessagesForDigestion(M[] innermsgs);

    ImmutableData   digestData(Data data);
    ImmutableData   digestTypeContent(NetworkMessage msg, int offset, int size);

    NetworkMessage digestMessageContent(NetworkMessage msg);

    NetworkMessage digestMessage(NetworkMessage msg);

}

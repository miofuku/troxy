package reptor.distrbt.com;

import reptor.chronos.Commutative;
import reptor.chronos.Orphic;
import reptor.distrbt.common.data.Data;


@Commutative
public interface MessageDigestSink extends Orphic
{

    void putData(Data data);

    void putInnerMessage(NetworkMessage innermsg);

    <M extends NetworkMessage>
        void putInnerMessages(M[] innermsgs);

    <M extends NetworkMessage>
        void putCollectionMessage(NetworkMessage msg, int headersize, M[] innermsgs);

    void putHolderMessage(NetworkMessage msg, int headersize, NetworkMessage innermsg);

    // Offset is relative to the plain prefix, that is, offset 0 means the byte right after the plain prefix.
    void putTypeContentData(NetworkMessage msg, int offset, int size);

}

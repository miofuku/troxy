package reptor.distrbt.com;

import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.ImmutableData;

public interface MessageRecord extends MessageDocument
{

    void        setContentSizes(int presize, int cntsize);
    void        setCertificateSize(int certsize);

    void        setContentDigest(ImmutableData cntdig);
    void        setMessageDigest(ImmutableData msgdig);

    void        setMessageData(Data msgdata);
    Data        getMessageData();

    Data        getContentData();
    Data        getCertificateData();

    void        setCertificateValid(boolean iscrtval);
    void        setInnerMessagesValid(boolean chldvalid);
    void        setValid();
    void        clearValid();

}

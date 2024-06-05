package reptor.distrbt.com;

import reptor.distrbt.common.data.ImmutableData;


// |                                 envelope                               |
// | preamble |                       message                    | epilogue |
// |          |               content              | certificate |          |
// |          |            header           | body |             |          |
// |          | common header | type header |      |             |          |
public interface MessageDocument
{

    int             getMessageTypeID();

    NetworkMessage getMessage();

    boolean         hasMessageSize();
    int             getMessageSize();
    boolean         hasPlainPrefixSize();
    int             getPlainPrefixSize();
    boolean         hasContentSize();
    int             getContentSize();
    boolean         hasCertificateSize();
    short           getCertificateSize();

    ImmutableData   getContentDigest();
    ImmutableData   getMessageDigest();

    Boolean         isCertificateValid();
    Boolean         areInnerMessagesValid();

}

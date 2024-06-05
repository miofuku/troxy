package reptor.distrbt.com.map;

import java.util.function.Supplier;

import reptor.chronos.Immutable;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessageRegistry;
import reptor.jlib.hash.HashAlgorithm;


@Immutable
public class BasicMessageMapperFactory implements Supplier<MessageMapper>
{

    private final NetworkMessageRegistry                    m_msgreg;
    private final BasicMessageDigestionStrategy.Variant     m_digvariant;
    private final HashAlgorithm                             m_digalgo;


    public BasicMessageMapperFactory(NetworkMessageRegistry msgreg,
                                     BasicMessageDigestionStrategy.Variant digvariant, HashAlgorithm digalgo)
    {
        m_msgreg     = msgreg;
        m_digvariant = digvariant;
        m_digalgo    = digalgo;
    }

    public NetworkMessageRegistry getMessageRegistry()
    {
        return m_msgreg;
    }

    public BasicMessageDigestionStrategy.Variant getDigestionVariant()
    {
        return m_digvariant;
    }

    public HashAlgorithm getDigestionAlgorithm()
    {
        return m_digalgo;
    }

    @Override
    public MessageMapper get()
    {
        return new BasicMessageMapper( m_msgreg, m_digvariant, m_digalgo );
    }

}

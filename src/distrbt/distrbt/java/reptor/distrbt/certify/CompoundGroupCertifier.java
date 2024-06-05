package reptor.distrbt.certify;

import java.util.Objects;


public class CompoundGroupCertifier<C extends Certifier, V extends Verifier>
                    implements GroupConnectionCertifier
{

    private final C   m_grpcertif;
    private final V[] m_grpverifs;


    public CompoundGroupCertifier(C grpcertif, V[] grpverifs)
    {
        m_grpcertif = Objects.requireNonNull( grpcertif );
        m_grpverifs = Objects.requireNonNull( grpverifs );
    }


    @Override
    public C getCertifier()
    {
        return m_grpcertif;
    }


    @Override
    public V getVerifier(int index)
    {
        return m_grpverifs[ index ];
    }


    @Override
    public int size()
    {
        return m_grpverifs.length;
    }

}

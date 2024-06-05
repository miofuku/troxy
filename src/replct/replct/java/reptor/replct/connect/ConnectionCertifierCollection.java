package reptor.replct.connect;

import java.util.Arrays;
import java.util.Objects;

import reptor.chronos.Commutative;
import reptor.distrbt.certify.BidirectionalCertifier;
import reptor.distrbt.certify.ConnectionCertifier;
import reptor.distrbt.certify.Verifier;
import reptor.distrbt.certify.VerifierGroup;


@Commutative
public class ConnectionCertifierCollection implements VerifierGroup
{

    private final ConnectionCertifier[] m_certifs;


    public ConnectionCertifierCollection(BidirectionalCertifier certif, int grpsize)
    {
        m_certifs = new ConnectionCertifier[ grpsize ];
        Arrays.fill( m_certifs, Objects.requireNonNull( certif ) );
    }


    public ConnectionCertifierCollection(ConnectionCertifier[] certifs)
    {
        m_certifs = Objects.requireNonNull( certifs );
    }

    // Connection certifiers have a commutative interface but they can get obsolete. Everyone who possesses
    // a reference must be informed if that happens. Options: 1) It is known in advance who has a reference
    // 2) some kind of broadcast is sent 3) caller if this method have to hand over a channel where they
    // are called back when the connection state changed.
    public ConnectionCertifier getCertifier(int index)
    {
        return m_certifs[ index ];
    }


    @Override
    public Verifier getVerifier(int index)
    {
        return m_certifs[ index ].getVerifier();
    }


    @Override
    public int size()
    {
        return m_certifs.length;
    }

}

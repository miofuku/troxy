package reptor.distrbt.certify;

import java.util.Objects;

public class ArrayVerifierGroup implements VerifierGroup
{

    private final Verifier[]    m_verifiers;


    public ArrayVerifierGroup(Verifier[] verifiers)
    {
        m_verifiers = Objects.requireNonNull( verifiers );
    }


    @Override
    public Verifier getVerifier(int index)
    {
        return m_verifiers[ index ];
    }


    @Override
    public int size()
    {
        return m_verifiers.length;
    }

}

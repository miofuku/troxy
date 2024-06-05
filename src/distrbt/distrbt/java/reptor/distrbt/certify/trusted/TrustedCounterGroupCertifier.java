package reptor.distrbt.certify.trusted;

import reptor.distrbt.certify.CompoundGroupCertifier;

public class TrustedCounterGroupCertifier extends CompoundGroupCertifier<CounterCertifier, CounterCertifier>
{

    public TrustedCounterGroupCertifier(CounterCertifier grpcertif, CounterCertifier[] grpverifs)
    {
        super( grpcertif, grpverifs );
    }

}

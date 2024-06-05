package reptor.distrbt.certify.trusted;

import reptor.distrbt.certify.CompoundGroupCertifier;

public class SequentialCounterGroupCertifier extends CompoundGroupCertifier<SequentialCounterCertifier, SequentialCounterCertifier>
{

    public SequentialCounterGroupCertifier(SequentialCounterCertifier grpcertif, SequentialCounterCertifier[] grpverifs)
    {
        super( grpcertif, grpverifs );
    }

}

package reptor.distrbt.certify.trusted;

import reptor.distrbt.certify.CompoundGroupCertifier;

public class TrustedMacGroupCertifier extends CompoundGroupCertifier<TrustedMacCertifier, TrustedMacCertifier>
{

    public TrustedMacGroupCertifier(TrustedMacCertifier grpcertif, TrustedMacCertifier[] grpverifs)
    {
        super( grpcertif, grpverifs );
    }

}

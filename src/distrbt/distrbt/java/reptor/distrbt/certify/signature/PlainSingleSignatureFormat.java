package reptor.distrbt.certify.signature;

import reptor.distrbt.certify.PlainSingleProofFormat;
import reptor.jlib.hash.HashAlgorithm;


public class PlainSingleSignatureFormat extends PlainSingleProofFormat implements SingleSignatureFormat
{

    public PlainSingleSignatureFormat(SignatureAlgorithm proofalgo, HashAlgorithm digalgo)
    {
        super( proofalgo, digalgo );
    }

    @Override
    public SignatureAlgorithm getProofAlgorithm()
    {
        return (SignatureAlgorithm) super.getProofAlgorithm();
    }

}

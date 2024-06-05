package reptor.distrbt.certify.debug;

import reptor.distrbt.certify.PlainSingleProofFormat;
import reptor.jlib.hash.HashAlgorithm;


public class PlainSingleDigestMacFormat extends PlainSingleProofFormat implements SingleDigestMacFormat
{

    public PlainSingleDigestMacFormat(DigestMacAlgorithm proofalgo, HashAlgorithm digalgo)
    {
        super( proofalgo, digalgo );
    }

    @Override
    public DigestMacAlgorithm getProofAlgorithm()
    {
        return (DigestMacAlgorithm) super.getProofAlgorithm();
    }

}

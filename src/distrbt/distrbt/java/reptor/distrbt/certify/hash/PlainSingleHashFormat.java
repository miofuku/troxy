package reptor.distrbt.certify.hash;

import reptor.distrbt.certify.PlainSingleProofFormat;
import reptor.jlib.hash.HashAlgorithm;


public class PlainSingleHashFormat extends PlainSingleProofFormat implements SingleHashFormat
{

    public PlainSingleHashFormat(HashProofAlgorithm proofalgo, HashAlgorithm digalgo)
    {
        super( proofalgo, digalgo );
    }

    @Override
    public HashProofAlgorithm getProofAlgorithm()
    {
        return (HashProofAlgorithm) super.getProofAlgorithm();
    }

}

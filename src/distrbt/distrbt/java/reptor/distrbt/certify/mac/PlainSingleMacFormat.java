package reptor.distrbt.certify.mac;

import reptor.distrbt.certify.PlainSingleProofFormat;
import reptor.jlib.hash.HashAlgorithm;


public class PlainSingleMacFormat extends PlainSingleProofFormat implements SingleMacFormat
{

    public PlainSingleMacFormat(MacAlgorithm proofalgo, HashAlgorithm digalgo)
    {
        super( proofalgo, digalgo );
    }

    @Override
    public MacAlgorithm getProofAlgorithm()
    {
        return (MacAlgorithm) super.getProofAlgorithm();
    }

}

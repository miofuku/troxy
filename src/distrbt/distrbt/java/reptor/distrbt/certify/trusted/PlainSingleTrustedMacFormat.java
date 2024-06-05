package reptor.distrbt.certify.trusted;

import reptor.distrbt.certify.PlainSingleProofFormat;
import reptor.jlib.hash.HashAlgorithm;

import com.google.common.base.Preconditions;


public class PlainSingleTrustedMacFormat extends PlainSingleProofFormat implements SingleTrustedMacFormat
{

    public PlainSingleTrustedMacFormat(TrustedAlgorithm proofalgo, HashAlgorithm digalgo)
    {
        super( proofalgo, digalgo );

        Preconditions.checkArgument( proofalgo.getProofType()==TrustedCertifying.TRUSTED_MAC ||
                                     proofalgo.getProofType()==TrustedCertifying.TRUSTED_COUNTER );
    }

    @Override
    public TrustedAlgorithm getProofAlgorithm()
    {
        return (TrustedAlgorithm) super.getProofAlgorithm();
    }

}

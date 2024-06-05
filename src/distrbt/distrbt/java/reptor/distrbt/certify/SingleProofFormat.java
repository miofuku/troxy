package reptor.distrbt.certify;

import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.MutableData;


public interface SingleProofFormat extends CertificateFormat
{

    int            getProofOffset();
    int            getMaximumProofSize();
    ProofAlgorithm getProofAlgorithm();

    default void writeCertificateTo(MutableData out, Certifier certifier, byte[] proofdata)
    {
        assert proofdata.length<=getMaximumProofSize();

        writeCertificateTo( out, certifier, proofdata, 0, proofdata.length );
    }

    void writeCertificateTo(MutableData out, Certifier certifier, byte[] proofdata, int proofoffset, int proofsize);

    default void writeCertificateTo(MutableData out, Certifier certifier, Data proofdata)
    {
        writeCertificateTo( out, certifier, proofdata.array(), proofdata.arrayOffset(), proofdata.size() );
    }

}

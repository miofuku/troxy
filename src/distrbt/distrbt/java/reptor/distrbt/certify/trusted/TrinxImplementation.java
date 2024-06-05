package reptor.distrbt.certify.trusted;

import java.io.IOException;


public interface TrinxImplementation
{
    int     getMacCertificateSize();
    int     getCounterCertificateSize();

    Trinx   createTrinx(short tssid, int ncounters) throws IOException;
}

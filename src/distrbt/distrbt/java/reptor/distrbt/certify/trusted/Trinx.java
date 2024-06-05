package reptor.distrbt.certify.trusted;

import reptor.distrbt.com.Message;


public interface Trinx extends TrustedAuthorityInstance, AutoCloseable
{

    String  getImplementationName();

    short   getID();

    void    executeCommand(Message cmd);

    // TODO: Create commands.
    int     getNumberOfCounters();
    int     getCounterCertificateSize();
    int     getMacCertificateSize();

    TrustedCounterValue counterValue(int ctrno);

    void    touch();


    @Override
    default TrustedMacProvider createTrustedMacProvider(SingleTrustedMacFormat certformat)
    {
        return new TrustedMacProvider( this, certformat );
    }


    @Override
    default TrustedCounterProvider createTrustedCounterProvider(SingleTrustedMacFormat certformat, int ctrno)
    {
        return new TrustedCounterProvider( this, certformat, ctrno );
    }


    @Override
    default SequentialCounterProvider createSequentialCounterProvider(SingleCounterFormat certformat, int ctrno)
    {
        return new SequentialCounterProvider( this, certformat, ctrno );
    }

}

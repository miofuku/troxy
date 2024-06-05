package reptor.distrbt.com;

import reptor.chronos.Orphic;


public interface MessageVerifier<M> extends Orphic
{
    void    verifyMessage(M msg) throws VerificationException;
    void    verifyMessages(M[] msgs) throws VerificationException;
}

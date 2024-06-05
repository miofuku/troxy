package reptor.replct.common.quorums;

import reptor.distrbt.com.VerificationException;

public class MessageCollectors
{

    public static <M> boolean isMessageAlreadyKnown(M msg, M curmsg) throws VerificationException
    {
        if( curmsg==null )
            return false;
        else if( curmsg.equals( msg ) )
            return true;
        else
            throw new VerificationException( msg.toString() );
    }


    private MessageCollectors()
    {

    }

}

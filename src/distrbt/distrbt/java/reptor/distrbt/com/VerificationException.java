package reptor.distrbt.com;


public class VerificationException extends UnsupportedOperationException
{

    private static final long serialVersionUID = 5345037431431294311L;


    public VerificationException()
    {
    }


    public VerificationException(String message)
    {
        super( message );
    }


    public VerificationException(Throwable cause)
    {
        super( cause );
    }


    public VerificationException(String message, Throwable cause)
    {
        super( message, cause );
    }

}

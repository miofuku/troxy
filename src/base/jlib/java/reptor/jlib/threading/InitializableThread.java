package reptor.jlib.threading;

import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Ints;

import reptor.jlib.strings.ArrayFormat;


public class InitializableThread extends Thread
{

    private static final Logger s_logger = LoggerFactory.getLogger( InitializableThread.class );

    private final int[]                 m_affinity;
    private final Semaphore             m_initsignal  = new Semaphore( 0 );
    private final Semaphore             m_startsignal = new Semaphore( 0 );


    public InitializableThread(Runnable target, String name, int[] affinity)
    {
        super( target, name );

        m_affinity = affinity;
    }


    public int[] getAffinity()
    {
        return m_affinity;
    }


    public void execute()
    {
        initContext();

        runMain();
    }


    public void init()
    {
        super.start();

        m_initsignal.acquireUninterruptibly();
    }


    @Override
    public void start()
    {
        m_startsignal.release();
    }


    @Override
    public void run()
    {
        initContext();

        waitForGo();

        runMain();
    }


    protected void initContext()
    {
        String affstr;

        if( m_affinity==null )
            affstr = "none";
        else
        {
            affstr = ArrayFormat.DEFAULT.valuesToString( Ints.asList( m_affinity ) );
            SystemThread.setThreadAffinity( m_affinity );
        }

        s_logger.info( "{} Started thread with ID {} and affinity {}", this, SystemThread.getSystemThreadID(), affstr );
    }


    protected void waitForGo()
    {
        m_initsignal.release();
        m_startsignal.acquireUninterruptibly();
    }


    protected void runMain()
    {
        super.run();
    }

}

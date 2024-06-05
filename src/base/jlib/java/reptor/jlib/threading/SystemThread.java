package reptor.jlib.threading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;


public final class SystemThread
{

    private static final Logger s_logger = LoggerFactory.getLogger( SystemThread.class );


    private interface CLib extends Library
    {
        int getpid();


        int syscall(int number, Object... args);


        int sched_setaffinity(int pid, long setsize, Pointer mask);
    }


    private static final CLib clib;

    static
    {
        String os = System.getProperty( "os.name", "generic" ).toLowerCase();

        if( os.contains( "nux" ) )
            clib = (CLib) Native.loadLibrary( "c", CLib.class );
        else
        {
            clib = null;
            s_logger.warn( "Affinity configuration is disabled." );
        }
    }


    public static void setProcessAffinity(int[] affinity)
    {
        if( clib == null )
            return;

        setAffinity( clib.getpid(), affinity );
    }


    public static int getSystemThreadID()
    {
        return clib != null ? clib.syscall( 186, (Object) null ) : -1;
    }


    public static void setThreadAffinity(int[] affinity)
    {
        if( clib == null )
            return;

        setAffinity( 0, affinity );
    }


    private static void setAffinity(int pid, int[] affinity)
    {
        Memory m = getMask( affinity );
        int s = clib.sched_setaffinity( pid, m.size(), m );

        if( s != 0 )
            throw new IllegalStateException( "Affinity for the process could not be set! (" + s + ")" );
    }


    private static Memory getMask(int[] affinity)
    {
        Memory mask = new Memory( 32 );

        long val = 0;

        for( int c : affinity )
            val = val | (1 << c);

        mask.setLong( 0, val );

        return mask;
    }
}

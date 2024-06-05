package reptor.start;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.bench.compose.DomainGroup;
import reptor.bench.compose.ReplicationBenchmark;
import reptor.chronos.ChronosDomain;
import reptor.chronos.Explorable;
import reptor.chronos.Orphic;
import reptor.chronos.domains.DomainThread;
import reptor.replct.common.settings.SettingsReader;


public class AbstractHost
{

    private static final Logger s_logger = LoggerFactory.getLogger( AbstractHost.class );


    protected static SettingsReader settingsReader(File cfgfile) throws IOException
    {
        try( FileInputStream cfs = new FileInputStream( cfgfile ) )
        {
            Properties props = new Properties();
            props.load( cfs );

            return new SettingsReader( props );
        }
    }


    protected static File environmentPath(File cfgfile)
    {
        return new File( cfgfile.getParentFile().getParentFile().getParentFile(), "config" );
    }


    protected static ReplicationBenchmark loadBenchmark(String benchname, File cfgfile, SettingsReader reader)
    {
        return new ReplicationBenchmark( benchname, environmentPath( cfgfile ) ).load( reader ).activate();
    }


    protected static void printSchedulingConfig(DomainGroup schedconf, DomainThread[] domains)
    {
        if( !s_logger.isInfoEnabled() )
            return;

        String aff = getIntArrayPropString( schedconf.getProcessAffinity() );

        s_logger.info( "Process affinity: {}", aff );

        for( int i=0; i<domains.length; i++ )
        {
            DomainThread  thread = domains[ i ];
            ChronosDomain dom    = thread.getDomain();

            aff = getIntArrayPropString( thread.getAffinity() );
            s_logger.info( String.format( "Domain %3d: %s, affinity %s", i, dom, aff ) );

            Explorable sched = (Explorable) dom.listSubordinates().get( 0 );

            int tid = 0;
            for( Orphic t : sched.listSubordinates() )
                s_logger.info( String.format( "  Task %3d: %-20s (%s)", tid++, t, t.toString() ) );
        }
    }


    private static String getIntArrayPropString(int[] array)
    {
        if( array == null )
            return "-";

        StringBuilder sb = new StringBuilder();
        int l = 0;
        boolean r = false;

        for( int i = 0; i < array.length; i++ )
        {
            int c = array[i];

            if( i > 0 && l == c - 1 )
            {
                r = true;

                if( i == array.length - 1 )
                    sb.append( "-" ).append( c );
            }
            else
            {
                if( r )
                    sb.append( "-" ).append( l );

                if( i > 0 )
                    sb.append( ", " );

                sb.append( c );
                r = false;
            }

            l = c;
        }

        return sb.toString();
    }

}

package reptor.bench.measure;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.LongConsumer;

import reptor.distrbt.io.net.NetworkExtensions.ConnectionExtension;
import reptor.distrbt.io.net.NetworkExtensions.ConnectionObserver;


public class TransmissionMeter extends TasksMeter implements ConnectionExtension
{
    private static class ConObserver implements ConnectionObserver
    {
        private SocketAddress      m_locaddr = null;
        private SocketAddress      m_remaddr = null;

        private final LongConsumer m_sentcons;
        private final LongConsumer m_recvcons;


        public ConObserver(LongConsumer sentcons, LongConsumer recvcons)
        {
            m_sentcons = sentcons;
            m_recvcons = recvcons;
        }


        @Override
        public void connectionInitialised(SocketChannel channel)
        {
            assert m_locaddr == null && m_remaddr == null;

            try
            {
                m_locaddr = channel.getLocalAddress();
                m_remaddr = channel.getRemoteAddress();
            }
            catch( IOException e )
            {
                throw new IllegalStateException( e );
            }
        }


        @Override
        public void dataSent(int nbytes)
        {
            m_sentcons.accept( nbytes );
        }


        @Override
        public void dataReceived(int nbytes)
        {
            m_recvcons.accept( nbytes );
        }
    }


    private final ConObserver[] m_conobs;
    private int                 m_curobs = 0;


    public TransmissionMeter(int ncons, long durwarm, long durrun, long durcool, boolean withhis)
    {
        super( ncons, 2, durwarm, durrun, durcool, withhis );

        if( !isActive() )
            m_conobs = null;
        else
        {
            m_conobs = new ConObserver[ncons];
            for( int i = 0; i < m_conobs.length; i++ )
                m_conobs[i] = new ConObserver( getTaskConsumer( i, 0 ), getTaskConsumer( i, 1 ) );
        }

    }


    @Override
    public ConnectionObserver getConnectionObserver()
    {
        synchronized( m_conobs )
        {
            return m_conobs[m_curobs++];
        }
    }


    public SocketAddress getLocalAddress(int taskno)
    {
        return m_conobs[taskno].m_locaddr;
    }


    public SocketAddress getRemoteAddress(int taskno)
    {
        return m_conobs[taskno].m_remaddr;
    }


    public void saveResults(Path respath) throws IOException
    {
        try( BufferedWriter bufwriter = Files.newBufferedWriter( respath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING );
                PrintWriter writer = new PrintWriter( bufwriter ) )
        {
            CSVResultsWriter csvwriter = new CSVResultsWriter( writer );

            csvwriter.writeField( "loc_addr" );
            csvwriter.writeField( "rem_addr" );
            csvwriter.writeSumStatsIntervalHeader( "sent_" );
            csvwriter.writeSumStatsIntervalHeader( "recv_" );
            csvwriter.newLine();

            for( int t = 0; t < getNumberOfTasks(); t++ )
            {
                if( getLocalAddress( t ) != null )
                {
                    csvwriter.writeField( getLocalAddress( t ).toString() );
                    csvwriter.writeField( getRemoteAddress( t ).toString() );
                }
                else
                {
                    csvwriter.writeField( "" );
                    csvwriter.writeField( "" );
                }
                csvwriter.writeSumStatsIntervalSummary( getResult( t, 0 ).getSummary() );
                csvwriter.writeSumStatsIntervalSummary( getResult( t, 1 ).getSummary() );
                csvwriter.newLine();
            }
        }
    }
}

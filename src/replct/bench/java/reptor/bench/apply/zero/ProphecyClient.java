package reptor.bench.apply.zero;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reptor.bench.BenchmarkCommand;
import reptor.bench.BlockingBenchmarkClient;
import reptor.bench.ByteArrayCommand;
import reptor.bench.CommandGenerator;
import reptor.bench.CommandResultProcessor;
import reptor.bench.compose.BlockingBenchmarker;
import reptor.bench.compose.ReplicationBenchmark;
import reptor.distrbt.common.data.ImmutableData;
import reptor.distrbt.common.data.ImmutableDataBuffer;
import reptor.distrbt.io.net.NetworkExtensions;
import reptor.replct.common.settings.SettingsReader;
import reptor.replct.invoke.BlockingInvocationHandler;
import reptor.replct.invoke.InvocationClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

/**
 * Created by bli on 15.08.17.
 */
public class ProphecyClient implements CommandGenerator
{
    private static String path = "/home/bli/git/reptor/wrkdir/run/config/system.cfg";
    private static ReplicationBenchmark repbench;
    private static SSLServerSocket s;
    private static SSLContext context;
    private final Random m_rand = new Random();
    private final int m_writerate;
    private final CommandResultProcessor<? super ByteArrayCommand> m_resproc;

    public ProphecyClient(CommandResultProcessor<? super ByteArrayCommand> resproc, int writerate)
    {
        m_resproc = resproc;
        m_writerate = writerate;
    }

    @Override
    public BenchmarkCommand nextCommand()
    {
        return null;
    }

    public void transfer(BlockingBenchmarkClient cli, BlockingInvocationHandler invoc, Socket sock) throws IOException
    {
        SSLSocket clientSocket = (SSLSocket) sock;
        InputStream inputStream = clientSocket.getInputStream();
        DataOutputStream outToClient = new DataOutputStream(clientSocket.getOutputStream());
        byte[] tmp = new byte[32*1024];
        int count;

        while (true)
        {
            count = inputStream.read(tmp);

            if (count==-1)
            {
                inputStream.close();
                outToClient.close();
                clientSocket.close();
                break;
            }

            boolean isreadonly = m_rand.nextInt( 100 )+1 > m_writerate;
            ImmutableData m_command = new ImmutableDataBuffer( count );
            ByteArrayCommand data = new ByteArrayCommand( cli, m_command, isreadonly );
            ByteArrayCommand received = (ByteArrayCommand) invoc.invokeService(data);
            byte[] reply = new byte[received.getResult().size()];
            received.getResult().writeTo(reply);
            outToClient.write(reply);
        }
    }

    private static void load() throws IOException
    {
        Logger s_logger = LoggerFactory.getLogger( ProphecyClient.class );
        NetworkExtensions.ConnectionObserver connobs = NetworkExtensions.ConnectionObserver.EMPTY;

        // load and parse config file
        File cfgfile = new File(path);
        FileInputStream cfs = new FileInputStream( cfgfile );
        Properties props = new Properties();
        props.load( cfs );
        SettingsReader reader = new SettingsReader( props );
        File envpath = new File( cfgfile.getParentFile().getParentFile().getParentFile(), "config" );


        // create replication benchmark and client
        repbench = new ReplicationBenchmark( "zero", envpath ).load( reader ).activate();
        InvocationClient invcli = repbench.getInvocation().createClient(s_logger.isInfoEnabled()).connectionObserver(connobs);

        int nbenhosts = Integer.parseInt(props.getProperty("clients.hosts"));
        int nclients = Integer.parseInt(props.getProperty("clients.number"));

        List<Short> clients = determineClientsForBenchmarker( repbench, 0, nbenhosts, nclients, repbench.getReplicaGroup().size() );

        for( short clino : clients )
            repbench.getCryptography().initKeysForClient( clino );
        repbench.getCryptography().activate();

        BlockingBenchmarker bb = new BlockingBenchmarker( repbench, repbench.getBenchmarking(), invcli, clients, 0, -1, 0, 0 );

        bb.start();

        int cnt=0;
        while (true)
        {
            SSLSocket cliSocket = (SSLSocket) s.accept();
            bb.processSocket(cnt, cliSocket);
            cnt++;
        }

    }

    private static List<Short> determineClientsForBenchmarker(ReplicationBenchmark sysconf, int benchid, int nbenhosts, int nclients, int clientidbase)
    {
        List<Short> clientids = new ArrayList<>();
        Random rand      = new Random( 42 );

        for( short i=0; i<nclients; i+=nbenhosts )
        {
            int cliidoff = i + ( ( rand.nextInt( 100 )+benchid ) % nbenhosts );
            if( cliidoff>=nclients )
                break;

            clientids.add( (short) ( clientidbase+cliidoff ) );
        }
        return clientids;
    }

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, KeyManagementException, CertificateException, KeyStoreException, UnrecoverableKeyException {
        System.out.println("Open socket and listen to: 5000");
        context = SSLContext.getInstance("TLSv1.2");
        context.init(null, null, null);
        SSLServerSocketFactory factory = context.getServerSocketFactory();
        s = (SSLServerSocket) factory.createServerSocket(5000, 500, InetAddress.getByName("127.0.0.1"));
        s.setEnabledCipherSuites(s.getSupportedCipherSuites());
        System.out.println("Load system cfg...");
        load();
    }
}

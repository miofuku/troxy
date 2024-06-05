package reptor.bench.apply.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reptor.bench.BenchmarkCommand;
import reptor.bench.BlockingBenchmarkClient;
import reptor.bench.ByteArrayCommand;
import reptor.bench.CommandGenerator;
import reptor.bench.compose.BlockingBenchmarker;
import reptor.bench.compose.ReplicationBenchmark;
import reptor.distrbt.common.data.ImmutableData;
import reptor.distrbt.io.net.NetworkExtensions;
import reptor.replct.common.settings.SettingsReader;
import reptor.replct.invoke.BlockingInvocationHandler;
import reptor.replct.invoke.InvocationClient;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Random;

/**
 * Created by bli on 3/23/17.
 */
public class HttpClient implements CommandGenerator
{
    private static String path = "/home/bli/git/reptor/wrkdir/run/config/system.cfg";
    private static ReplicationBenchmark repbench;
    private static ServerSocket s;


    private enum Type
    {
        GET,
        POST,
        ALERT
    }

    public HttpClient() { }

    private Type getMessageType(byte[] data)
    {
        switch (data[0]) {
            case 71:
                return Type.GET;
            case 80:
                return Type.POST;
            default:
                return Type.ALERT;
        }
    }

    public void transfer(BlockingBenchmarkClient cli, BlockingInvocationHandler invoc, Socket sock) throws IOException
    {
        Socket clientSocket = sock;
        InputStream inputStream = clientSocket.getInputStream();
        byte[] tmp = new byte[32*1024];
        int count = 0;

        while (true)
        {
            count = inputStream.read(tmp);
            Type type = getMessageType(tmp);

            if (count==-1 || type==Type.ALERT)
            {
                inputStream.close();
                clientSocket.close();
                break;
            }

            ByteArrayCommand data = new ByteArrayCommand(cli, ImmutableData.createFor(ByteBuffer.wrap(tmp), count), type == Type.GET ? true : false);
            ByteArrayCommand received = (ByteArrayCommand) invoc.invokeService(data);
            byte[] reply = new byte[received.getResult().size()];
            received.getResult().writeTo(reply);
            DataOutputStream outToClient = new DataOutputStream(clientSocket.getOutputStream());
            outToClient.write(reply);
        }
/*        Socket clientSocket = sock;
        InputStream inputStream = clientSocket.getInputStream();
        byte[] tmp = new byte[32*1024];
        int count = inputStream.read(tmp);
        Type type = getMessageType(tmp);

        while (true)
        {
            if (type!=Type.ALERT) {
                byte[] input = Arrays.copyOfRange(tmp, 0, count);
                ByteArrayCommand data = new ByteArrayCommand(cli, ImmutableData.createFor(input), type == Type.GET ? true : false);
                ByteArrayCommand received = (ByteArrayCommand) invoc.invokeService(data);
                byte[] reply = new byte[received.getResult().size()];
                received.getResult().writeTo(reply);
                DataOutputStream outToClient = new DataOutputStream(clientSocket.getOutputStream());
                outToClient.write(reply);
                inputStream = clientSocket.getInputStream();
                tmp = new byte[32*1024];
                inputStream.read(tmp);
                type = getMessageType(tmp);
            }
            else
            {
                inputStream.close();
                clientSocket.close();
                break;
            }
        }*/
    }

    @Override
    public BenchmarkCommand nextCommand() {
        return null;
    }

    private static void load() throws IOException
    {
        Logger s_logger = LoggerFactory.getLogger( HttpClient.class );
        NetworkExtensions.ConnectionObserver connobs = NetworkExtensions.ConnectionObserver.EMPTY;

        // load and parse config file
        File cfgfile = new File(path);
        FileInputStream cfs = new FileInputStream( cfgfile );
        Properties props = new Properties();
        props.load( cfs );
        SettingsReader reader = new SettingsReader( props );
        File envpath = new File( cfgfile.getParentFile().getParentFile().getParentFile(), "config" );


        // create replication benchmark and client
        repbench = new ReplicationBenchmark( "http", envpath ).load( reader ).activate();
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
            Socket cliSocket = s.accept();
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

    public static void main(String[] args) throws IOException {
        System.out.println("Open socket and listen to: 5000");
        s = new ServerSocket(5000, 500, InetAddress.getByName("127.0.0.1"));

        System.out.println("Load system cfg...");
        load();
    }
}

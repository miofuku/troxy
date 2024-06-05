package reptor.bench.apply.ycsb;

import com.yahoo.ycsb.ByteIterator;
import com.yahoo.ycsb.DB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reptor.bench.BlockingBenchmarkClient;
import reptor.bench.ByteArrayCommand;
import reptor.bench.compose.ReplicationBenchmark;
import reptor.distrbt.common.data.ImmutableData;
import reptor.distrbt.io.net.NetworkExtensions;
import reptor.replct.common.settings.SettingsReader;
import reptor.replct.invoke.BlockingInvocationHandler;
import reptor.replct.invoke.InvocationClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Created by bli on 5/8/17.
 */
public class YCSBClient extends DB{

    private static short cnt = 4;
    private BlockingInvocationHandler invocationHandler;
    private InvocationClient invcli;
    private ReplicationBenchmark repbench;
    private SettingsReader reader;
    private File cfgfile, envpath;
    private FileInputStream cfs;
    private BlockingBenchmarkClient client;
    private Properties props;

    public void init() {
        short id;
        Logger s_logger = LoggerFactory.getLogger( YCSBClient.class );
        NetworkExtensions.ConnectionObserver connobs = NetworkExtensions.ConnectionObserver.EMPTY;

        synchronized (YCSBClient.class) {
            id = cnt;
            cnt++;
        }
        String path = "/home/bli/git/reptor/wrkdir/run/config/system.cfg";
        try {
            // load and parse config file
            cfgfile = new File(path);
            cfs = new FileInputStream( cfgfile );
            props = new Properties();
            props.load( cfs );
            reader = new SettingsReader( props );
            envpath = new File( cfgfile.getParentFile().getParentFile().getParentFile(), "config" );
            // create replication benchmark and client
            repbench = new ReplicationBenchmark( "ycsb", envpath ).load( reader ).activate();
            invcli = repbench.getInvocation().createClient(s_logger.isInfoEnabled()).connectionObserver(connobs);
            // initialize keys
            repbench.getCryptography().initKeysForClient( id );
            repbench.getCryptography().activate();
            // create invocation handler and benchmark client
            invocationHandler = new BlockingInvocationHandler(id, invcli, null);
            client = new BlockingBenchmarkClient(id, new YCSBBenchmark(true), invocationHandler, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int read(String table, String key,
                    Set<String> fields, HashMap<String, ByteIterator> result) {
        HashMap<String, byte[]> results = new HashMap<String, byte[]>();
        YCSBMessage request = YCSBMessage.newReadRequest(table, key, fields, results);
        byte[] reply = new byte[0];
        try {
            ByteArrayCommand data = new ByteArrayCommand(client, ImmutableData.createFor(request.getBytes()),true);
            ByteArrayCommand received = (ByteArrayCommand) invocationHandler.invokeService(data);
            reply = new byte[received.getResult().size()];
            received.getResult().writeTo(reply);
        } catch (Exception e) {
            e.printStackTrace();
        }
        YCSBMessage replyMsg = YCSBMessage.getObject(reply);
        return replyMsg.getResult();
    }

    @Override
    public int scan(String table, String startkey, int recordcount, Set<String> fields,
                    Vector<HashMap<String, ByteIterator>> result) {
        Vector<HashMap<String, byte[]>> results = new Vector<>();
        YCSBMessage request = YCSBMessage.newScanRequest(table, startkey, recordcount, fields, results);
        byte[] reply = null;
        try {
            ByteArrayCommand data = new ByteArrayCommand(client, ImmutableData.createFor(request.getBytes()),true);
            ByteArrayCommand received = (ByteArrayCommand) invocationHandler.invokeService(data);
            reply = new byte[received.getResult().size()];
            received.getResult().writeTo(reply);
        } catch (Exception e) {
            e.printStackTrace();
        }
        YCSBMessage replyMsg = YCSBMessage.getObject(reply);
        return replyMsg.getResult();
    }

    @Override
    public int update(String table, String key,
                      HashMap<String, ByteIterator> values) {
        Iterator<String> keys = values.keySet().iterator();
        HashMap<String, byte[]> map = new HashMap<String, byte[]>();
        while(keys.hasNext()) {
            String field = keys.next();
            map.put(field, values.get(field).toArray());
        }
        YCSBMessage request = YCSBMessage.newUpdateRequest(table, key, map);
        byte[] reply = new byte[0];
        try {
            ByteArrayCommand data = new ByteArrayCommand(client, ImmutableData.createFor(request.getBytes()),false);
            ByteArrayCommand received = (ByteArrayCommand) invocationHandler.invokeService(data);
            reply = new byte[received.getResult().size()];
            received.getResult().writeTo(reply);
        } catch (Exception e) {
            e.printStackTrace();
        }
        YCSBMessage replyMsg = YCSBMessage.getObject(reply);
        return replyMsg.getResult();
    }

    @Override
    public int insert(String table, String key,
                      HashMap<String, ByteIterator> values) {

        Iterator<String> keys = values.keySet().iterator();
        HashMap<String, byte[]> map = new HashMap<String, byte[]>();
        while(keys.hasNext()) {
            String field = keys.next();
            map.put(field, values.get(field).toArray());
        }
        YCSBMessage request = YCSBMessage.newInsertRequest(table, key, map);
        byte[] reply = new byte[0];
        try {
            ByteArrayCommand data = new ByteArrayCommand(client, ImmutableData.createFor(request.getBytes()),false);
            ByteArrayCommand received = (ByteArrayCommand) invocationHandler.invokeService(data);
            reply = new byte[received.getResult().size()];
            received.getResult().writeTo(reply);
        } catch (Exception e) {
            e.printStackTrace();
        }
        YCSBMessage replyMsg = YCSBMessage.getObject(reply);
        return replyMsg.getResult();
    }

    @Override
    public int delete(String s, String s1) {
        throw new UnsupportedOperationException();
    }
}

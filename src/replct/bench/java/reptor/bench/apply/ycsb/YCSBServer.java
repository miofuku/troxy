package reptor.bench.apply.ycsb;

import reptor.distrbt.common.data.ImmutableData;
import reptor.distrbt.common.data.ImmutableDataBuffer;
import reptor.replct.service.ServiceInstance;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Vector;

/**
 * Created by bli on 5/8/17.
 */
public class YCSBServer implements ServiceInstance{

    private TreeMap<String, YCSBTable> mTables;
    private YCSBPersistentStorage storage;
    private boolean load;
    private int count = 1;
    private int record = 0;

    public YCSBServer(boolean load) {

        this.storage = new YCSBPersistentStorage();
//        this.load = load;

        if (load) {
            this.mTables = new TreeMap<>();
        } else {
            this.mTables = storage.readFromFile();
        }
//        this.record = Integer.parseInt(getYCSBProperties().getProperty("recordcount", "0"));
    }

    private Properties getYCSBProperties() {
        String ycsb = System.getProperty("ycsbcfg");
        Properties prop = new Properties();
        try {
            prop.load(new FileInputStream(ycsb));
        } catch (IOException ioe) {
            System.out.println(ioe.getMessage());
            System.exit(0);
        }
        return prop;
    }

    @Override
    public ImmutableData processCommand(int clino, ImmutableData command, boolean readonly) {
        YCSBMessage aRequest = YCSBMessage.getObject(command.array());

        switch (aRequest.getType()) {
            case CREATE:
                return processCREATERequest(aRequest);
            case UPDATE:
                return processUPDATERequest(aRequest);
            case READ:
                return processREADRequest(aRequest);
            case SCAN:
                return processSCANRequest(aRequest);
            default:
                throw new UnsupportedOperationException("Unknown request received.");
        }
    }

    private ImmutableData processCREATERequest(YCSBMessage aRequest) {

        YCSBMessage reply = YCSBMessage.newErrorMessage("");

        switch (aRequest.getEntity()) {
            case RECORD: // ##### entity: record #####
                if (!mTables.containsKey(aRequest.getTable())) {
                    mTables.put(aRequest.getTable(), new YCSBTable());
                }
                if (!mTables.get(aRequest.getTable()).containsKey(
                        aRequest.getKey())) {
                    mTables.get(aRequest.getTable()).put(aRequest.getKey(),
                            aRequest.getValues());
                    reply = YCSBMessage.newInsertResponse(0);
                }
//                if (load && count == record) {
//                    storage.writeToFile(mTables);
//                }
                break;
            default:
                break;
        }
//        count++;
        return new ImmutableDataBuffer(reply.getBytes());
    }

    private ImmutableData processREADRequest(YCSBMessage aRequest) {

        YCSBMessage reply = YCSBMessage.newErrorMessage("");

        switch (aRequest.getEntity()) {
            case RECORD: // ##### entity: record #####
                if (!mTables.containsKey(aRequest.getTable())) {
                    reply = YCSBMessage.newErrorMessage("Table not found");
                    break;
                }
                if (!mTables.get(aRequest.getTable()).containsKey(
                        aRequest.getKey())) {
                    reply = YCSBMessage.newErrorMessage("Record not found");
                    break;
                } else {
                    reply = YCSBMessage.newReadResponse(
                            mTables.get(aRequest.getTable()).get(
                                    aRequest.getKey()), 0);
                    break;
                }
            default: // Only read records
                break;
        }
        return new ImmutableDataBuffer(reply.getBytes());
    }

    private ImmutableData processUPDATERequest(YCSBMessage aRequest) {

        YCSBMessage reply = YCSBMessage.newErrorMessage("");

        switch (aRequest.getEntity()) {
            case RECORD: // ##### entity: record #####
                if (!mTables.containsKey(aRequest.getTable())) {
                    mTables.put(aRequest.getTable(), new YCSBTable());
                }
                mTables.get(aRequest.getTable())
                        .put(String.valueOf(aRequest.getKey()),
                                aRequest.getValues());
                reply = YCSBMessage.newUpdateResponse(1);
                break;
            default: // Only update records
                break;
        }
        return new ImmutableDataBuffer(reply.getBytes());
    }

    private ImmutableData processSCANRequest(YCSBMessage aRequest) {
        String startkey = aRequest.getStartkey();
        int range = aRequest.getRecordcount();
        Vector<HashMap<String, byte[]>> result = new Vector<>();
        YCSBMessage reply = YCSBMessage.newErrorMessage("");

        switch (aRequest.getEntity()) {
            case RECORD: // ##### entity: record #####
                if (!mTables.containsKey(aRequest.getTable())) {
                    reply = YCSBMessage.newErrorMessage("Table not found");
                    break;
                }
                String getKey = mTables.get(aRequest.getTable()).ceilingKey(
                        startkey);
                while (range > 0 && getKey != null) {
                    result.add(mTables.get(aRequest.getTable()).get(getKey));
                    range--;
                    getKey = mTables.get(aRequest.getTable()).higherKey(getKey);
                }
                reply = YCSBMessage.newScanResponse(result, 0);
                break;
            default:
                break;
        }
        return new ImmutableDataBuffer(reply.getBytes());
    }

    @Override
    public void applyUpdate(ImmutableData update) { throw new UnsupportedOperationException(); }

    @Override
    public ImmutableData createCheckpoint() {
        try {
            System.out.println("Create checkpoint");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = new ObjectOutputStream(bos);
            out.writeObject(mTables);
            out.flush();
            out.close();
            bos.close();
            return ImmutableData.wrap(bos.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ImmutableDataBuffer(ByteBuffer.allocate(0).array());
    }

    @Override
    public void applyCheckpoint(ImmutableData checkpoint) {
        try {
            System.out.println("Apply checkpoint");
            ByteArrayInputStream bis = new ByteArrayInputStream(checkpoint.array());
            ObjectInput in = new ObjectInputStream(bis);
            mTables = (TreeMap<String, YCSBTable>) in.readObject();
            in.close();
            bis.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean createsFullCheckpoints() {
        return true;
    }
}

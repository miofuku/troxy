package reptor.bench.apply.ycsb;

import java.io.*;
import java.util.TreeMap;

/**
 * Created by bli on 5/8/17.
 */
public class YCSBPersistentStorage {

    public YCSBPersistentStorage() {}

    public void writeToFile(TreeMap<String, YCSBTable> wTable) {
        try {
//            String hostname = InetAddress.getLocalHost().getHostName();
//            String username = System.getProperty("user.name");
            ObjectOutputStream oout = new ObjectOutputStream(new FileOutputStream("/tmp/keyset.txt"));
            oout.writeObject(wTable);
            oout.flush();
            oout.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }


    public TreeMap<String, YCSBTable> readFromFile() {
        TreeMap<String, YCSBTable> rTable = null;
        try {
            String path = "/home/bli/git/reptor/config/ycsb/keyset.txt";
            ObjectInputStream oin = new ObjectInputStream(new FileInputStream(path));
            rTable = (TreeMap<String,YCSBTable>) oin.readObject();
            oin.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        return rTable;
    }
}

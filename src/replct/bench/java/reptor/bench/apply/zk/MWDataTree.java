package reptor.bench.apply.zk;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class MWDataTree implements Serializable
{

    private static final long             serialVersionUID = 1L;

    private final Map<String, MWDataNode> nodes;
    private final Map<Integer, String>    pathsByIndex;
    private final Map<Long, Set<String>>  ephemerals;


    public MWDataTree()
    {
        this.nodes = new HashMap<String, MWDataNode>();
        this.pathsByIndex = new HashMap<Integer, String>();
        this.ephemerals = new ConcurrentHashMap<Long, Set<String>>();

        // Create root node
        MWStat stat = new MWStat( 0, -1 );
        stat.setLastModified( 0 );

        MWDataNode root = new MWDataNode( stat );
        nodes.put( "/", root );
    }


    public String getPathByIndex(int index)
    {
        return pathsByIndex.get( index );
    }


    public MWDataNode getNode(String path, int version) throws MWZooKeeperException
    {
        // Get node
        MWDataNode node = nodes.get( path );
        if( node == null )
            throw new MWZooKeeperException( "ERROR: node " + path + " does not exist" );

        // Check version
        if( version >= 0 )
        {
            int currentVersion = node.getStat().getVersion();
            if( version != currentVersion )
                throw new MWZooKeeperException( "WARNING: bad version " + version + " vs. " + currentVersion );
        }

        return node;
    }


    public String create(String path, byte[] data, long time, long ephemeralOwner) throws MWZooKeeperException
    {
        // Check whether node already exists
        if( nodes.get( path ) != null )
            throw new MWZooKeeperException( "ERROR: node " + path + " already exists" );

        // Check whether parent node exists
        // String parentName = path.substring(0, path.lastIndexOf('/') + 1);
        // MWDataNode parent = nodes.get(parentName);
        // if(parent == null) throw new MWZooKeeperException("ERROR: parent node for " + path + " does not exist");

        // Create new node
        MWStat stat = new MWStat( 0, ephemeralOwner );
        stat.setLastModified( time );

        MWDataNode node = new MWDataNode( stat );
        node.setData( data );
        nodes.put( path, node );
        pathsByIndex.put( node.getIndex(), path );

        // Handle ephemeral nodes
        if( ephemeralOwner != 0 )
        {
            Set<String> ephemeralNodes = ephemerals.get( ephemeralOwner );
            if( ephemeralNodes == null )
            {
                ephemeralNodes = new HashSet<String>();
                ephemerals.put( ephemeralOwner, ephemeralNodes );
            }

            synchronized( ephemeralNodes )
            {
                ephemeralNodes.add( path );
                System.out.println( "ADDED EPHEMERAL: " + path );
            }
        }

        // System.out.println("CREATED: " + path + " (" + (data!=null ? data.length : 0) + " byte)" );
        return path;
    }


    public void delete(String path, int version) throws MWZooKeeperException
    {
        // Check version
        MWDataNode node = getNode( path, version );

        // Versions match -> remove node
        nodes.remove( path );
        pathsByIndex.remove( node.getIndex() );

        // Handle ephemeral nodes
        long ephemeralOwner = node.getStat().getEphemeralOwner();
        if( ephemeralOwner != 0 )
        {
            Set<String> ephemeralNodes = ephemerals.get( ephemeralOwner );
            if( ephemeralNodes != null )
            {
                synchronized( ephemeralNodes )
                {
                    ephemeralNodes.remove( path );
                    System.out.println( "DELETED EPHEMERAL[" + version + "]: " + path );
                }
            }
        }

        System.out.println( "DELETED[" + version + "]: " + path );
    }


    public MWStat setData(String path, byte[] data, int version, long time) throws MWZooKeeperException
    {
        // Get node and check version
        MWDataNode node = getNode( path, version );

        // Set data
        node.setData( data );

        // Update stat; new object in original ZooKeeper implementation
        MWStat stat = node.getStat();
        stat.setVersion( version + 1 );
        stat.setLastModified( time );

        // System.out.println("DATA SET[" + version + "]: " + path + " -> " + data);
        return stat;
    }


    public byte[] getData(String path, MWStat stat) throws MWZooKeeperException
    {
        MWDataNode node = getNode( path, -1 );
        MWStat.copy( node.getStat(), stat );
        return node.getData();
    }


    public void killSession(long ephemeralOwner) throws MWZooKeeperException
    {
        // Get ephemeral nodes of this session
        Set<String> ephemeralNodes = ephemerals.get( ephemeralOwner );
        if( ephemeralNodes == null )
            return;

        // Delete all ephemeral nodes of this session
        for( String path : ephemeralNodes )
        {
            delete( path, -1 );
        }

        System.out.println( "SESSION KILLED: " + ephemeralOwner );
    }

}

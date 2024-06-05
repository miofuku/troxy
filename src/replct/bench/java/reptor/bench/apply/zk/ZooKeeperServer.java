package reptor.bench.apply.zk;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.distrbt.common.data.ImmutableData;
import reptor.replct.service.ServiceInstance;


public class ZooKeeperServer implements ServiceInstance
{

    private static final Logger s_logger = LoggerFactory.getLogger( ZooKeeperServer.class );


    public static final boolean ZIP_PAYLOAD = false;
    public static final String  NODE_BASE   = "/";

    private final MWDataTree    m_datatree  = new MWDataTree();

    private int                 m_hashblock;
    private byte[][]            m_hashes;
    private final MessageDigest m_md;
    private final int           m_nnodes;

    private byte[]              m_unzipbuffer;


    public ZooKeeperServer()
    {
        try
        {
            m_hashblock = getIntProp( "zk.hashblock", 100 );
            m_md = MessageDigest.getInstance( "MD5" );

            m_nnodes = getIntProp( "zk.nnodes", 1000 );
            int dsmin = getIntProp( "zk.dsmin", 0 );
            int dsmax = getIntProp( "zk.dsmax", 2048 );

            s_logger.info( "zk.nnodes     {}", m_nnodes );
            s_logger.info( "zk.dsmin      {}", dsmin );
            s_logger.info( "zk.dsmax      {}", dsmax );
            s_logger.info( "zk.hashblock  {}", m_hashblock );

            m_unzipbuffer = new byte[dsmax + 1024];

            createStructure( m_nnodes, (dsmin + dsmax) / 2 );
        }
        catch( Exception e )
        {
            throw new IllegalStateException( e );
        }
    }


    static final int getIntProp(String key, int def)
    {
        String val = System.getProperty( key );

        return val != null ? Integer.parseInt( val ) : def;
    }


    @Override
    public ImmutableData processCommand(int clino, ImmutableData reqdata, boolean readonly)
    {
        try
        {
            assert reqdata.arrayOffset()==0;

            // FIXME: This does not take arrayOffset() into account!
            MWZooKeeperRequest request = MWZooKeeperRequest.deserialize( reqdata.array() );

            request.setSessionID( clino );
            request.setTime( -1L );

            byte[] res = processRequest( request ).serialize();

            if( request.getOperation() != MWZooKeeperOperation.GET_DATA && m_hashblock != 0 )
            {
                int nno = Integer.parseInt( request.getPath().substring( NODE_BASE.length() ) );
                calcBlockHash( nno / m_hashblock );
            }

            return ImmutableData.wrap( res );
        }
        catch( IOException e )
        {
            throw new IllegalStateException( e );
        }
    }


    @Override
    public void applyUpdate(ImmutableData update)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public ImmutableData createCheckpoint()
    {
        if( m_hashblock != 0 )
        {
            for( byte[] bh : m_hashes )
                m_md.update( bh );
        }
        else
        {
            try
            {
                for( int i = 0; i < m_nnodes; i++ )
                {
                    MWDataNode node = m_datatree.getNode( NODE_BASE + i, -1 );
                    // -- We actually would have to hash the metadata too.
                    m_md.update( node.getData() );
                }
            }
            catch( MWZooKeeperException e )
            {
                throw new IllegalStateException( e );
            }
        }

        return ImmutableData.wrap( m_md.digest() );
    }


    @Override
    public boolean createsFullCheckpoints()
    {
        return false;
    }


    @Override
    public void applyCheckpoint(ImmutableData checkpoint)
    {
        throw new UnsupportedOperationException();
    }


    private void createStructure(int nnodes, int datasize) throws MWZooKeeperException, IOException,
            InterruptedException
    {
        if( m_hashblock != 0 && (nnodes % m_hashblock) != 0 )
            throw new MWZooKeeperException( "Number of nodes has to be a multiple of the hash block size." );

        s_logger.info( "{} Create structure ({} à {} bytes)...", this, nnodes, datasize );

        for( int i = 0; i < nnodes; i++ )
            m_datatree.create( NODE_BASE + i, new byte[datasize], 0, 0 );

        if( m_hashblock != 0 )
        {
            int nhb = nnodes / m_hashblock;
            m_hashes = new byte[nhb][];

            for( int i = 0; i < nhb; i++ )
                calcBlockHash( i );
        }

        s_logger.info( "{} Structure complete", this );
    }


    private void calcBlockHash(int bno)
    {
        try
        {
            for( int i = 0; i < m_hashblock; i++ )
            {
                MWDataNode node = m_datatree.getNode( NODE_BASE + (bno * m_hashblock + i), -1 );
                m_md.update( node.getData() );
            }
        }
        catch( MWZooKeeperException e )
        {
            throw new IllegalStateException( e );
        }

        m_hashes[bno] = m_md.digest();
    }


    protected MWZooKeeperResponse processRequest(MWZooKeeperRequest request)
    {
        MWZooKeeperResponse response = new MWZooKeeperResponse();

        try
        {
            // Process request
            switch( request.getOperation() )
            {
            case CREATE:
            {
                byte[] initialData = request.getData();
                boolean ephemeral = request.getEphemeral();

                // Unzip payload
                if( ZIP_PAYLOAD )
                {
                    try
                    {
                        ByteArrayInputStream byteInputStream = new ByteArrayInputStream( initialData );
                        InflaterInputStream zipInputStream = new InflaterInputStream( byteInputStream );

                        int nbytes = zipInputStream.read( m_unzipbuffer, 0, m_unzipbuffer.length );
                        initialData = new byte[nbytes];

                        System.arraycopy( m_unzipbuffer, 0, initialData, 0, nbytes );

                        byteInputStream.close();
                        zipInputStream.close();
                    }
                    catch( IOException ioe )
                    {
                        ioe.printStackTrace();
                    }
                }

                if( ephemeral )
                    throw new UnsupportedOperationException();

                long ephemeralOwner = ephemeral ? request.getSessionID() : 0;
                String path = m_datatree.create( request.getPath(), initialData, request.getTime(), ephemeralOwner );
                response.setPath( path );
            }
                break;
            case DELETE:
                m_datatree.delete( request.getPath(), request.getVersion() );
                break;
            case SET_DATA:
            {
                byte[] newData = request.getData();
                // Serializable[] setDataPayload = (Serializable[]) request.getPayload();
                // byte[] newData = (byte[]) setDataPayload[1];

                // Unzip payload
                if( ZIP_PAYLOAD )
                {
                    try
                    {
                        ByteArrayInputStream byteInputStream = new ByteArrayInputStream( newData );
                        InflaterInputStream zipInputStream = new InflaterInputStream( byteInputStream );

                        int nbytes = zipInputStream.read( m_unzipbuffer, 0, m_unzipbuffer.length );

                        newData = new byte[nbytes];

                        System.arraycopy( m_unzipbuffer, 0, newData, 0, nbytes );

                        byteInputStream.close();
                        zipInputStream.close();
                    }
                    catch( IOException ioe )
                    {
                        ioe.printStackTrace();
                    }
                }

                MWStat setStat = m_datatree.setData( request.getPath(), newData, request.getVersion(),
                        request.getTime() );
                response.setStat( setStat );
            }
                break;
            case GET_DATA:
            {
                MWStat stat = new MWStat();
                byte[] nodeData = m_datatree.getData( request.getPath(), stat );

                // Zip payload
                if( ZIP_PAYLOAD )
                {
                    try
                    {
                        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
                        DeflaterOutputStream zipOutputStream = new DeflaterOutputStream( byteOutputStream );

                        zipOutputStream.write( nodeData );

                        zipOutputStream.close();
                        byteOutputStream.close();

                        nodeData = byteOutputStream.toByteArray();
                    }
                    catch( IOException ioe )
                    {
                        ioe.printStackTrace();
                    }
                }

                response.setData( nodeData );
                response.setStat( stat );
            }
                break;
            case CLOSE_SESSION:
                long sessionID = request.getSessionID();
                m_datatree.killSession( sessionID );
                break;
            }
        }
        catch( MWZooKeeperException zke )
        {
            // m_log.warning( zke.toString() );

            response.setException( zke );
        }

        return response;
    }
}

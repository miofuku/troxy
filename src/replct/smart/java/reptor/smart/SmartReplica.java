package reptor.smart;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultRecoverable;


public class SmartReplica extends DefaultRecoverable
{
    private final int         id;
    private final String      cfgdir;
    private final Application app;


    public SmartReplica(int id, String cfgdir, Application app)
    {
        this.id = id;
        this.cfgdir = cfgdir;
        this.app = app;
    }


    @Override
    public byte[] executeUnordered(byte[] command, MessageContext msgCtx)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public byte[][] appExecuteBatch(byte[][] commands, MessageContext[] msgCtxs)
    {
        byte[][] replies = new byte[commands.length][];

        for( int i = 0; i < commands.length; i++ )
            replies[i] = app.processRequest( msgCtxs[i].getSender(), ImmutableData.wrap( commands[i] ), msgCtxs[i].getConsensusId() ).array();

        return replies;
    }


    @Override
    public byte[] getSnapshot()
    {
        return app.createCheckpoint().array();
    }


    @Override
    public void installSnapshot(byte[] state)
    {
        app.applyCheckpoint( ImmutableData.wrap( state ) );
    }


    public void start()
    {
        new ServiceReplica( id, cfgdir, this, this );
    }


    public static void main(String[] args) throws Exception
    {
        int id = Integer.parseInt( args[0] );
        String cfgdir = args[1];
        String appname = args[2];
        int clientcnt = Integer.parseInt( args[3] );
        int clientidbase = Integer.parseInt( args[4] );
        String cf = args[5];

        Config.load( (short) id, cf );

        Application app;

        switch( appname )
        {
        case "counter":
            app = new CounterServer( 0, 1, clientidbase, clientcnt, Config.REPLY_SIZE );
            break;
        case "zero":
            app = new ZeroServer( Config.REPLY_SIZE, Config.STATE_SIZE );
            break;
        case "zk":
            app = new ZooKeeperServer();
            break;
        default:
            throw new IllegalStateException( "Unknown application " + appname );
        }

        SmartReplica replica = new SmartReplica( id, cfgdir, app );

        replica.start();
    }
}

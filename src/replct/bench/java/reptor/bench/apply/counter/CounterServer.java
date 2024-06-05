package reptor.bench.apply.counter;

import java.nio.ByteBuffer;

import reptor.distrbt.common.data.ImmutableData;
import reptor.replct.service.ServiceInstance;


public class CounterServer implements ServiceInstance
{

    private final int[] counters;    // -- One dedicated counter for each client
    private final int   clientidbase; // -- Where does the range of client ids start?
                                      // private final int clientcnt; //-- Number of clients and thus the number of
                                      // available counters
    private final int   partid;      // -- ID of this state partition
    private final int   partcnt;     // -- Number of state partitions
    private final int   chkptsize;   // -- Calculated size of each checkpoint
    private final int   replysize;   // -- Minimum size of each reply.


    public CounterServer(int partid, int partcnt, int clientidbase, int clientcnt, int replysize)
    {
        this.partid = partid;
        this.partcnt = partcnt;
        this.clientidbase = clientidbase;
        this.replysize = replysize;

        this.counters = new int[clientcnt];
        this.chkptsize = ((clientcnt + partcnt - 1) / partcnt) * (Integer.SIZE >> 3);
    }


    public void init()
    {
        // Do nothing
    }


    @Override
    public ImmutableData processCommand(int clino, ImmutableData command, boolean readonly)
    {
        // Create reply
        short counterID = (short) (clino - clientidbase);
        ByteBuffer result = ByteBuffer.allocate( Math.max( replysize, Integer.SIZE >> 3 ) );
        result.putInt( ++counters[counterID] );
        return ImmutableData.wrap( result.array() );
    }


    @Override
    public void applyUpdate(ImmutableData update)
    {
        ByteBuffer updateBuffer = update.byteBuffer();
        short counterID = updateBuffer.getShort();
        counters[counterID] = updateBuffer.getInt();
    }


    @Override
    public ImmutableData createCheckpoint()
    {
        ByteBuffer checkpoint = ByteBuffer.allocate( chkptsize );
        for( int i = 0; i < counters.length; i++ )
            if( partcnt == 1 || (i + clientidbase) % partcnt == partid )
                checkpoint.putInt( counters[i] );
        return ImmutableData.wrap( checkpoint.array() );
    }


    @Override
    public void applyCheckpoint(ImmutableData checkpoint)
    {
        ByteBuffer checkpointBuffer = checkpoint.byteBuffer();
        for( int i = 0; i < counters.length; i++ )
            if( partcnt == 1 || (i + clientidbase) % partcnt == partid )
                counters[i] = checkpointBuffer.getInt();
    }


    @Override
    public boolean createsFullCheckpoints()
    {
        return true;
    }
}

package reptor.bench.apply.zk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.zip.DeflaterOutputStream;

import reptor.bench.CommandGenerator;
import reptor.bench.CommandResultProcessor;


public class ZooKeeperClient implements CommandGenerator, CommandResultProcessor<ZooKeeperCommand>
{

    private final CommandResultProcessor<? super ZooKeeperCommand> m_resproc;

    private final int    m_datasizemin;
    private final int    m_datasizemax;
    private final int    m_nnodes;
    private final int    m_writerate;
    private final int    m_lossrate;

    private final Random m_rand = new Random();


    public ZooKeeperClient(CommandResultProcessor<? super ZooKeeperCommand> resproc,
                           int datasizemin, int datasizemax, int nnodes, int writerate, int lossrate)
    {
        m_resproc     = resproc;

        m_datasizemin = datasizemin;
        m_datasizemax = datasizemax;
        m_nnodes      = nnodes;
        m_writerate   = writerate;
        m_lossrate    = lossrate;

        if( DATA_SEED.length < m_datasizemax )
            throw new IllegalArgumentException( "Get a longer abstract!!!!" );
    }


    @Override
    public ZooKeeperCommand nextCommand()
    {
        String path = ZooKeeperServer.NODE_BASE + m_rand.nextInt( m_nnodes );
        MWZooKeeperRequest request;

        if( m_rand.nextInt( 100 ) + 1 <= m_writerate )
        {
            int ver = m_rand.nextInt( 100 ) + 1 <= m_lossrate ? Integer.MAX_VALUE : -1;
            request = createSetDataRequest( path, createData(), ver );
        }
        else
        {
            request = createGetDataRequest( path );
        }

        return new ZooKeeperCommand( this, request );
    }


    private MWZooKeeperRequest createSetDataRequest(String path, byte[] data, int version)
    {
        // Create request
        MWZooKeeperRequest request = new MWZooKeeperRequest( MWZooKeeperOperation.SET_DATA, path, version );

        // Zip payload
        if( ZooKeeperServer.ZIP_PAYLOAD )
        {
            try
            {
                ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
                DeflaterOutputStream zipOutputStream = new DeflaterOutputStream( byteOutputStream );

                zipOutputStream.write( data );

                zipOutputStream.close();
                byteOutputStream.close();

                data = byteOutputStream.toByteArray();
            }
            catch( IOException ioe )
            {
                ioe.printStackTrace();
            }
        }

        request.setData( data );

        return request;
    }


    private MWZooKeeperRequest createGetDataRequest(String path)
    {
        return new MWZooKeeperRequest( MWZooKeeperOperation.GET_DATA, path );
    }


    @Override
    public void processResult(ZooKeeperCommand command)
    {
        m_resproc.processResult( command );
    }


    private byte[] createData()
    {
        byte[] data = new byte[ m_datasizemin + m_rand.nextInt( ( m_datasizemax-m_datasizemin ) + 1 ) ];
        System.arraycopy( DATA_SEED, 0, data, 0, data.length );
        return data;
    }


    public static final byte[] DATA_SEED = ( "In an ongoing process, conventional computing infrastruc-" +
                                             "One of the main reasons why Byzantine fault-tolerant (BFT)" +
                                             "systems are not widely used lies in their high resource con-" +
                                             "sumption: 3f + 1 replicas are necessary to tolerate only f" +
                                             "faults. Recent works have been able to reduce the minimum" +
                                             "number of replicas to 2f + 1 by relying on a trusted sub-" +
                                             "system that prevents a replica from making conflicting state-" +
                                             "ments to other replicas without being detected. Nevertheless," +
                                             "having been designed with the focus on fault handling, these" +
                                             "systems still employ a majority of replicas during normal-" +
                                             "case operation for seemingly redundant work. Furthermore," +
                                             "the trusted subsystems available trade off performance for" +
                                             "security; that is, they either achieve high  throughput or they" +
                                             "come with a large trusted computing base." +
                                             "  This paper presents CheapBFT, a BFT system that, for" +
                                             "the first time, tolerates that all but one of the replicas active" +
                                             "in normal-case operation become faulty. CheapBFT runs a" +
                                             "composite agreement protocol and exploits passive replica-" +
                                             "tion to save resources; in the absence of faults, it requires that" +
                                             "only f + 1 replicas actively agree on client requests and ex-" +
                                             "ecute them. In case of suspected faulty behavior, CheapBFT" +
                                             "triggers a transition protocol that activates f extra passive" +
                                             "replicas and brings all non-faulty replicas into a consistent" +
                                             "state again. This approach, for example, allows the system to" +
                                             "safely switch to another, more resilient agreement protocol." +
                                             "CheapBFT relies on an FPGA-based trusted subsystem for" +
                                             "the authentication of protocol messages that provides high" +
                                             "performance and comprises a small trusted computing base." +
                                             "  This paper presents CheapBFT, a BFT system that, for" +
                                             "the first time, tolerates that all but one of the replicas active" +
                                             "in normal-case operation become faulty. CheapBFT runs a" +
                                             "composite agreement protocol and exploits passive replica-" +
                                             "tion to save resources; in the absence of faults, it requires that" +
                                             "only f + 1 replicas actively agree on client requests and ex-" +
                                             "ecute them. In case of suspected faulty behavior, CheapBFT" +
                                             "triggers a transition protocol that activates f extra passive" +
                                             "replicas and brings all non-faulty replicas into a consistent" +
                                             "state again. This approach, for example, allows the system to" +
                                             "safely switch to another, more resilient agreement protocol." +
                                             "CheapBFT relies on an FPGA-based trusted subsystem for" +
                                             "the authentication of protocol messages that provides high" +
                                             "performance and comprises a small trusted computing base." +
                                             "In an ongoing process, conventional computing infrastruc-" +
                                             "One of the main reasons why Byzantine fault-tolerant (BFT)" +
                                             "systems are not widely used lies in their high resource con-" +
                                             "sumption: 3f + 1 replicas are necessary to tolerate only f" +
                                             "faults. Recent works have been able to reduce the minimum" +
                                             "number of replicas to 2f + 1 by relying on a trusted sub-" +
                                             "system that prevents a replica from making conflicting state-" +
                                             "ments to other replicas without being detected. Nevertheless," +
                                             "having been designed with the focus on fault handling, these" +
                                             "systems still employ a majority of replicas during normal-" +
                                             "case operation for seemingly redundant work. Furthermore," +
                                             "the trusted subsystems available trade off performance for" +
                                             "security; that is, they either achieve high throughput or they" +
                                             "come with a large trusted computing base." +
                                             "  This paper presents CheapBFT, a BFT system that, for" +
                                             "the first time, tolerates that all but one of the replicas active" +
                                             "in normal-case operation become faulty. CheapBFT runs a" +
                                             "composite agreement protocol and exploits passive replica-" +
                                             "tion to save resources; in the absence of faults, it requires that" +
                                             "only f + 1 replicas actively agree on client requests and ex-" +
                                             "ecute them. In case of suspected faulty behavior, CheapBFT" +
                                             "triggers a transition protocol that activates f extra passive" +
                                             "replicas and brings all non-faulty replicas into a consistent" +
                                             "state again. This approach, for example, allows the system to" +
                                             "safely switch to another, more resilient agreement protocol." +
                                             "CheapBFT relies on an FPGA-based trusted subsystem for" +
                                             "the authentication of protocol messages that provides high" +
                                             "performance and comprises a small trusted computing base." +
                                             "  This paper presents CheapBFT, a BFT system that, for" +
                                             "the first time, tolerates that all but one of the replicas active" +
                                             "in normal-case operation become faulty. CheapBFT runs a" +
                                             "composite agreement protocol and exploits passive replica-" +
                                             "tion to save resources; in the absence of faults, it requires that" +
                                             "only f + 1 replicas actively agree on client requests and ex-" +
                                             "ecute them. In case of suspected faulty behavior, CheapBFT" +
                                             "triggers a transition protocol that activates f extra passive" +
                                             "replicas and brings all non-faulty replicas into a consistent" +
                                             "state again. This approach, for example, allows the system to" +
                                             "safely switch to another, more resilient agreement protocol." +
                                             "CheapBFT relies on an FPGA-based trusted subsystem for" +
                                             "the authentication of protocol messages that provides high" +
                                             "performance and comprises a small trusted computing base."
                                                 ).getBytes();
}

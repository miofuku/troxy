package reptor.bench.apply.zero;

import java.util.Random;

import com.google.common.base.Preconditions;

import reptor.distrbt.common.data.ImmutableData;
import reptor.distrbt.common.data.ImmutableDataBuffer;
import reptor.replct.service.ServiceInstance;


public class ZeroServer implements ServiceInstance
{

    private ImmutableData m_result;
    private final ImmutableData m_chkpt;

    private final int           m_conflictrate;
    private final ImmutableData m_conflict;
    private final Random        m_rand;


    public ZeroServer(int instno, int replysize, int chkptsize, int conflictrate)
    {
        Preconditions.checkArgument( conflictrate==0 || replysize>0,
                "To generate conflicts, the reply size has to be greater than zero." );

        m_result = new ImmutableDataBuffer( replysize );
        m_chkpt  = new ImmutableDataBuffer( chkptsize );

        m_conflictrate = conflictrate;

        if( conflictrate==0 )
        {
            m_conflict = null;
            m_rand     = null;
        }
        else
        {
            byte[] conbuf = new byte[ replysize ];
            conbuf[ 0 ] = (byte) instno;

            m_conflict = new ImmutableDataBuffer( conbuf );
            m_rand     = new Random( 42 );
        }
    }


    @Override
    public ImmutableData processCommand(int clino, ImmutableData command, boolean readonly)
    {
//      return m_conflictrate>0 && readonly && m_rand.nextInt( 100 )+1 <= m_conflictrate ? m_conflict : m_result;
        if (!readonly)
        {
            byte[] conf = new byte[m_result.size()];
            conf[0] = command.array()[0];
            m_result = new ImmutableDataBuffer(conf);
        }
        return m_result;
    }


    @Override
    public void applyUpdate(ImmutableData update)
    {
    }


    @Override
    public ImmutableData createCheckpoint()
    {
        return m_chkpt;
    }


    @Override
    public void applyCheckpoint(ImmutableData checkpoint)
    {
    }


    @Override
    public boolean createsFullCheckpoints()
    {
        return true;
    }

}

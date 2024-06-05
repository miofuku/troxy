package reptor.bench.apply.zero;

import java.util.Random;

import reptor.bench.BenchmarkCommand;
import reptor.bench.ByteArrayCommand;
import reptor.bench.CommandGenerator;
import reptor.bench.CommandResultProcessor;
import reptor.distrbt.common.data.ImmutableData;
import reptor.distrbt.common.data.ImmutableDataBuffer;


public class ZeroClient implements CommandGenerator, CommandResultProcessor<ByteArrayCommand>
{

    private final CommandResultProcessor<? super ByteArrayCommand> m_resproc;

    private ImmutableData m_command;
    private final int           m_writerate;

    private final Random m_rand = new Random();
    private long counter = 1;


    public ZeroClient(CommandResultProcessor<? super ByteArrayCommand> resproc, int reqsize, int writerate)
    {
        m_resproc   = resproc;
        m_command   = new ImmutableDataBuffer( reqsize );
        m_writerate = writerate;
    }


    @Override
    public BenchmarkCommand nextCommand()
    {
        boolean isreadonly = m_rand.nextInt( 100 )+1 > m_writerate;

        if (!isreadonly)
        {
            m_command.array()[0] = (byte) counter;
            counter++;
        }

        return new ByteArrayCommand( this, m_command, isreadonly );
    }


    @Override
    public void processResult(ByteArrayCommand command)
    {
        m_resproc.processResult( command );
    }

}

package reptor.bench.apply.counter;

import reptor.bench.ByteArrayCommand;
import reptor.bench.CommandGenerator;
import reptor.bench.CommandResultProcessor;
import reptor.distrbt.common.data.ImmutableDataBuffer;


public class CounterClient implements CommandGenerator, CommandResultProcessor<ByteArrayCommand>
{

    private final CommandResultProcessor<? super ByteArrayCommand> m_resproc;

    private final ImmutableDataBuffer m_command;
    private int                       m_counter;


    public CounterClient(CommandResultProcessor<? super ByteArrayCommand> resproc, int reqsize)
    {
        m_resproc = resproc;
        m_command = new ImmutableDataBuffer( reqsize );
        m_counter = 0;
    }


    @Override
    public ByteArrayCommand nextCommand()
    {
        return new ByteArrayCommand( this, m_command, false );
    }


    @Override
    public void processResult(ByteArrayCommand command)
    {
        m_resproc.processResult( command );

        int value = command.getResult().byteBuffer().getInt();

        m_counter++;
        if( value!=m_counter )
            throw new IllegalStateException( "Bad result: counter value is " + value + " instead of " + m_counter );
    }

}

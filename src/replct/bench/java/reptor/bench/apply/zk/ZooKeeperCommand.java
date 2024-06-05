package reptor.bench.apply.zk;

import java.io.IOException;

import reptor.bench.BenchmarkCommand;
import reptor.bench.CommandResultProcessor;
import reptor.distrbt.common.data.ImmutableData;

class ZooKeeperCommand extends BenchmarkCommand
{

    private final CommandResultProcessor<? super ZooKeeperCommand> m_resproc;

    private final MWZooKeeperRequest m_request;
    private boolean                  m_hasreply = false;


    public ZooKeeperCommand(CommandResultProcessor<? super ZooKeeperCommand> resproc, MWZooKeeperRequest request)
    {
        m_resproc = resproc;
        m_request = request;
    }


    @Override
    public ImmutableData getData()
    {
        try
        {
            return ImmutableData.wrap( m_request.serialize() );
        }
        catch( IOException e )
        {
            throw new UnsupportedOperationException( e );
        }
    }


    @Override
    public boolean isReadOnly()
    {
        return m_request.getOperation().isReadOnly();
    }


    @Override
    public void setResult(ImmutableData reply)
    {
        m_hasreply = true;
    }


    @Override
    public boolean hasResult()
    {
        return m_hasreply;
    }


    @Override
    public void processResult()
    {
        m_resproc.processResult( this );
    }

}
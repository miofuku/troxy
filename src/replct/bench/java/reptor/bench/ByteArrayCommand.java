package reptor.bench;

import reptor.distrbt.common.data.ImmutableData;


public class ByteArrayCommand extends BenchmarkCommand
{

    private final CommandResultProcessor<? super ByteArrayCommand> m_resproc;

    private ImmutableData m_data;
    private boolean       m_isreadonly;
    private ImmutableData m_result;


    public ByteArrayCommand(CommandResultProcessor<? super ByteArrayCommand> resproc, ImmutableData data, boolean isreadonly)
    {
        m_resproc    = resproc;
        m_data       = data;
        m_isreadonly = isreadonly;
    }


    public void init(ImmutableData data)
    {
        m_data   = data;
        m_result = null;
    }


    @Override
    public void reset()
    {
        super.reset();

        m_result = null;
    }


    @Override
    public ImmutableData getData()
    {
        return m_data;
    }


    @Override
    public boolean isReadOnly()
    {
        return m_isreadonly;
    }


    @Override
    public void setResult(ImmutableData result)
    {
        m_result = result;
    }


    @Override
    public boolean hasResult()
    {
        return m_result!=null;
    }


    public ImmutableData getResult()
    {
        return m_result;
    }

    @Override
    public void processResult()
    {
        m_resproc.processResult( this );
    }

}

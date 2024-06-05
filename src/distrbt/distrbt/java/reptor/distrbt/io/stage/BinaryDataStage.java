package reptor.distrbt.io.stage;

import java.util.Objects;

import reptor.chronos.com.CommunicationSink;
import reptor.chronos.com.CommunicationSource;
import reptor.chronos.com.CommunicationStage;

// TODO: Binary channels should be stages. Maybe ...
public class BinaryDataStage<I extends CommunicationSink, O extends CommunicationSource>
        implements CommunicationStage<I, O>
{

    private CommunicationStage<? extends I, ? extends O>    m_previous;
    private CommunicationStage<? extends I, ? extends O>    m_next;


    public BinaryDataStage(CommunicationStage<? extends I, ? extends O> previous, CommunicationStage<? extends I, ? extends O> next)
    {
        m_previous = Objects.requireNonNull( previous );
        m_next     = Objects.requireNonNull( next );
    }

    @Override
    public I getSink()
    {
        return m_previous.getSink();
    }

    @Override
    public O getSource()
    {
        return m_next.getSource();
    }

    @Override
    public void activate()
    {
        m_previous.activate();
        m_next.activate();
    }

    @Override
    public void deactivate()
    {
        m_previous.deactivate();
        m_next.deactivate();
    }

    @Override
    public boolean isActivated()
    {
        return m_previous.isActivated() || m_next.isActivated();
    }

}

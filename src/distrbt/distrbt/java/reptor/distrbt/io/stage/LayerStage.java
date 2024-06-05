package reptor.distrbt.io.stage;

import java.util.Objects;

import reptor.chronos.com.CommunicationLayerElement;
import reptor.chronos.com.CommunicationSink;
import reptor.chronos.com.CommunicationSource;
import reptor.chronos.com.CommunicationStage;


public class LayerStage<I extends CommunicationSink, O extends CommunicationSource>
        implements CommunicationStage<I, O>
{

    private final I                         m_sink;
    private final O                         m_source;
    private final CommunicationLayerElement m_layer;


    public LayerStage(CommunicationLayerElement layer, I sink, O source)
    {
        m_layer  = Objects.requireNonNull( layer );
        m_sink   = sink;
        m_source = source;
    }


    @Override
    public I getSink()
    {
        return m_sink;
    }


    @Override
    public O getSource()
    {
        return m_source;
    }


    @Override
    public void activate()
    {
        m_layer.activate();
    }


    @Override
    public void deactivate()
    {
        m_layer.deactivate();
    }


    @Override
    public boolean isActivated()
    {
        return m_layer.isActivated();
    }

}

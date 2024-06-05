package reptor.chronos.com;


public interface CommunicationLayer<II extends CommunicationSink, OI extends CommunicationSource,
                                    IO extends CommunicationSource, OO extends CommunicationSink>
        extends ConnectorEndpoint<II, OI>, ConnectionEndpoint<IO, OO>, CommunicationLayerElement
{
    CommunicationStage<? extends II, ? extends IO>   getInboundStage();
    CommunicationStage<? extends OO, ? extends OI>   getOutboundStage();
}

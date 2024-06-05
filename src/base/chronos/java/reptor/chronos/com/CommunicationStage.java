package reptor.chronos.com;


public interface CommunicationStage<I extends CommunicationSink, O extends CommunicationSource> extends CommunicationLayerElement
{
    I       getSink();
    O       getSource();
}

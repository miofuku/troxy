package reptor.chronos.com;


public interface ConnectorEndpoint<I extends CommunicationSink, O extends CommunicationSource>
{
    I       getInboundConnect();
    O       getOutboundConnect();
}

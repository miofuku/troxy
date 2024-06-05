package reptor.chronos.com;


public interface ConnectionEndpoint<I extends CommunicationSource, O extends CommunicationSink>
{
    I       getInbound();
    O       getOutbound();
}

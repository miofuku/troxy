package reptor.chronos.com;


public interface PushMessageSource<M> extends CommunicationSource
{
    void    initReceiver(PushMessageSink<? super M> receiver);
}

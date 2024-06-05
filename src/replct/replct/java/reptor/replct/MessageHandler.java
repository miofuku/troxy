package reptor.replct;

import reptor.chronos.Orphic;

// TODO: Do we need this? What is the relation to ProtocolHandler?
public interface MessageHandler<M> extends Orphic
{
    boolean handleMessage(M msg);
}

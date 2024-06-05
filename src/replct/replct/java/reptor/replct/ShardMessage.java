package reptor.replct;

import reptor.distrbt.com.Message;


public interface ShardMessage extends Message
{
    short getShardNumber();
}

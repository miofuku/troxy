package reptor.replct.agree.checkpoint;

import reptor.distrbt.com.NetworkMessage;


public interface CheckpointNetworkMessage extends NetworkMessage, CheckpointMessage
{
    short getShardNumber();
}

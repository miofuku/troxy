package reptor.replct.agree.checkpoint;

import reptor.distrbt.com.Message;


public interface CheckpointMessage extends Message
{
    long getOrderNumber();
}

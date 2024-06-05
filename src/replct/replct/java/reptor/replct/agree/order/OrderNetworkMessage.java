package reptor.replct.agree.order;

import reptor.distrbt.com.NetworkMessage;


public interface OrderNetworkMessage extends NetworkMessage
{
    long getOrderNumber();
    int  getViewNumber();
}

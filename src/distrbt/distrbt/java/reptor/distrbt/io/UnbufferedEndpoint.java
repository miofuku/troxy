package reptor.distrbt.io;

import reptor.chronos.com.CommunicationEndpoint;


public interface UnbufferedEndpoint extends CommunicationEndpoint
{
    int getMinimumBufferSize();
}

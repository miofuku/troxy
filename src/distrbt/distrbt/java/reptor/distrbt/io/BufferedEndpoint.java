package reptor.distrbt.io;

import reptor.chronos.com.CommunicationEndpoint;


public interface BufferedEndpoint extends CommunicationEndpoint
{
    void    adjustBuffer(int minbufsize);
}

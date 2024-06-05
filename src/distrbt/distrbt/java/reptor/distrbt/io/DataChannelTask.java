package reptor.distrbt.io;

import java.io.IOException;


public interface DataChannelTask extends GenericDataLinkElement
{
    void    execute() throws IOException;
}
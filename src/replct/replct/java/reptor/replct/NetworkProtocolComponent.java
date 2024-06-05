package reptor.replct;

import reptor.distrbt.com.NetworkMessageRegistry.NetworkMessageRegistryBuilder;


public interface NetworkProtocolComponent
{
    void    registerMessages(NetworkMessageRegistryBuilder msgreg);
}

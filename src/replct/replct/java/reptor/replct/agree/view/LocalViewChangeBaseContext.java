package reptor.replct.agree.view;

import reptor.chronos.Orphic;
import reptor.distrbt.com.Message;


public interface LocalViewChangeBaseContext extends Orphic
{
    short   getShardNumber();
    boolean isInternalCoordinator();

    void    enqueueForViewChangeCoordinator(Message msg);
}

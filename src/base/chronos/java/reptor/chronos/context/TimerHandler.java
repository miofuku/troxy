package reptor.chronos.context;

import reptor.chronos.Asynchronous;
import reptor.chronos.Orphic;

public interface TimerHandler extends Orphic
{
    @Asynchronous
    void timeElapsed(TimeKey key);
}

package reptor.chronos.context;


public interface TimeKey
{
    TimerHandler handler();

    void         schedule(long delay);
    void         clear();
}

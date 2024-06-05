package reptor.chronos;

public interface ChronosDomain extends Runnable, Explorable
{
    ChronosAddress  getAddress();

    void            initContext();
    void            shutdown();
}

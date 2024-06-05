package reptor.chronos;


public interface ChronosTask extends Orphic
{
    boolean isReady();

    // Returns weather everything is done, that is, the ready state after the execution.
    // If the task is marked as ready before the execution, it must not call taskReady()
    // of the master during the execution. If it is not marked as ready but nevertheless
    // executed, the task is allowed to call taskReady() even during the execution but
    // does not need to.
    boolean execute();
}

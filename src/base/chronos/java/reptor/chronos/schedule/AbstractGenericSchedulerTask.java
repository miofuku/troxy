package reptor.chronos.schedule;

import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.chronos.context.ChronosDomainContext;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.orphics.AbstractMaster;


public abstract class AbstractGenericSchedulerTask<D extends ChronosDomainContext>
        extends AbstractMaster<D>
        implements GenericSchedulerTask<D>, SchedulerContext<D>
{

    private SchedulerContext<? extends D> m_master;


    @Override
    public void bindToMaster(SchedulerContext<? extends D> master)
    {
        Preconditions.checkState( m_master==null );

        m_master = Objects.requireNonNull( master );
    }


    @Override
    public void unbindFromMaster(SchedulerContext<? extends D> master)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    protected SchedulerContext<? extends D> master()
    {
        return m_master;
    }


    @Override
    public SchedulerContext<D> getContext()
    {
        return this;
    }

}

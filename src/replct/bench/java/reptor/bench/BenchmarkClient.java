package reptor.bench;

import java.io.IOException;
import java.util.function.LongConsumer;

import reptor.chronos.Asynchronous;
import reptor.chronos.ChronosTask;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.orphics.AbstractMaster;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.invoke.InvocationClientHandler;
import reptor.replct.invoke.InvocationClientProvider;
import reptor.replct.service.ServiceCommand;


public class BenchmarkClient extends AbstractMaster<SelectorDomainContext>
                             implements CommandResultProcessor<BenchmarkCommand>
{

    //-------------------------------------//
    //                Types                //
    //-------------------------------------//

    public interface BenchmarkClientHandler
    {
        @Asynchronous
        void    clientConnected(BenchmarkClient client);
    }

    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private final SchedulerContext<? extends SelectorDomainContext> m_cntxt;

    private final short                     m_clino;
    private final CommandGenerator          m_cmdgen;
    private final InvocationClientHandler   m_invhandler;
    private final BenchmarkClientHandler    m_clihandler;
    private final LongConsumer              m_reqdurobs;

    private boolean                         m_isconnected = false;

    private long                            startingtime = System.currentTimeMillis()/100;


    public BenchmarkClient(SchedulerContext<? extends SelectorDomainContext> cntxt, short clino,
                           Benchmark bench,
                           InvocationClientProvider invprov,
                           BenchmarkClientHandler clihandler,
                           LongConsumer reqdurobs)
                                   throws IOException
    {
        m_cntxt      = cntxt;
        m_clino      = clino;
        m_cmdgen     = bench.createCommandGenerator( this );
        m_invhandler = invprov.createInvocationHandler( this, clino, null );
        m_clihandler = clihandler;
        m_reqdurobs  = reqdurobs;
    }


    @Override
    protected SchedulerContext<? extends SelectorDomainContext> master()
    {
        return m_cntxt;
    }


    //-------------------------------------//
    //           General Interface         //
    //-------------------------------------//

    public short getClientNumber()
    {
        return m_clino;
    }


    //-------------------------------------//
    //            Actor Interface          //
    //-------------------------------------//

    @Asynchronous
    public void startInvocation()
    {
        BenchmarkCommand cmd = m_cmdgen.nextCommand();

        cmd.invocationStarted( master().getDomainContext().time() );

        m_invhandler.startInvocation( cmd );
    }


    public int getNumberOfOngoingInvocations()
    {
        return m_invhandler.getNumberOfOngoingInvocations();
    }


    @Override
    public boolean execute()
    {
        while( m_invhandler.isReady() )
        {
            m_invhandler.execute();

            ServiceCommand cmd;

            while( ( cmd = m_invhandler.pollResult() )!=null )
                cmd.processResult();
        }

        if( !m_isconnected && m_invhandler.isConnected() )
        {
            m_isconnected = true;
            m_clihandler.clientConnected( this );
        }

        clearReady();

        return true;
    }


    @Override
    public void processResult(BenchmarkCommand command)
    {
        command.invocationFinished( master().getDomainContext().time() );

        m_reqdurobs.accept( command.getDuration() / 1000 );

        if ((System.currentTimeMillis()/100-startingtime)%320==0)
            m_invhandler.getConflict();
    }


    //-------------------------------------//
    //          Master Interface           //
    //-------------------------------------//

    @Override
    public void taskReady(ChronosTask task)
    {
        notifyReady();
    }

}

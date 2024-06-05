package reptor.bench;

import java.io.IOException;
import java.net.Socket;
import java.util.function.LongConsumer;

import reptor.bench.apply.http.HttpClient;
import reptor.bench.apply.zero.ProphecyClient;
import reptor.replct.invoke.BlockingInvocationHandler;


public class BlockingBenchmarkClient implements CommandResultProcessor<BenchmarkCommand>
{

    private final short                     m_clino;
    private final CommandGenerator          m_cmdgen;
    private final BlockingInvocationHandler m_invhandler;

    private final LongConsumer              m_reqdurobs;
    public Socket sock = null;
    private Object waitforsock = new Object();


    public BlockingBenchmarkClient(short clino, Benchmark bench, BlockingInvocationHandler invhandler, LongConsumer reqdurobs)
    {
        m_clino      = clino;
        m_cmdgen     = bench.createCommandGenerator( this );
        m_invhandler = invhandler;
        m_reqdurobs  = reqdurobs;
    }


    public short getClientNumber()
    {
        return m_clino;
    }


    public void invokeProphecyService()
    {
        ProphecyClient prophecyClient = (ProphecyClient) m_cmdgen;

        synchronized (waitforsock) {
            while (sock==null)
            {
                try {
                    waitforsock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            prophecyClient.transfer(this, m_invhandler, sock);
        } catch (IOException e) {
            e.printStackTrace();
        }

        sock = null;
    }


    public void setSocket(Socket clisock)
    {
        synchronized (waitforsock)
        {
            sock = clisock;
            waitforsock.notify();
        }
    }


    public void invokeHttpService()
    {
        HttpClient httpClient = (HttpClient) m_cmdgen;

        synchronized (waitforsock) {
            while (sock==null)
            {
                try {
                    waitforsock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            httpClient.transfer(this, m_invhandler, sock);
        } catch (IOException e) {
            e.printStackTrace();
        }

        sock = null;
    }


    public void invokeService() throws IOException
    {
        BenchmarkCommand cmd = m_cmdgen.nextCommand();

        cmd.invocationStarted( getTime() );

        m_invhandler.invokeService( cmd );

        cmd.processResult();
    }


    @Override
    public void processResult(BenchmarkCommand command)
    {
        command.invocationFinished( getTime() );

        m_reqdurobs.accept( command.getDuration() / 1000 );
    }


    private long getTime()
    {
        return System.nanoTime();
    }

}

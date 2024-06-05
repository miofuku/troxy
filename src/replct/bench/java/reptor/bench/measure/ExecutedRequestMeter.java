package reptor.bench.measure;

import java.util.function.LongConsumer;

import reptor.replct.agree.order.OrderMessages.CommandContainer;
import reptor.replct.execute.ExecutionExtensions;


public class ExecutedRequestMeter extends TasksMeter implements ExecutionExtensions.ExecutedRequestExtension
{
    private static class RequestObserver implements ExecutionExtensions.ExecutedRequestObserver
    {
        private final LongConsumer m_consumer;


        public RequestObserver(LongConsumer consumer)
        {
            m_consumer = consumer;
        }


        @Override
        public void requestExecuted(CommandContainer command)
        {
            m_consumer.accept( command.getNumberOfCommands() );
        }
    }


    private final RequestObserver[] m_reqobs;


    public ExecutedRequestMeter(int nstages, long durwarm, long durrun, long durcool, boolean withhis)
    {
        super( nstages, 1, durwarm, durrun, durcool, withhis );

        if( !isActive() )
            m_reqobs = null;
        else
        {
            m_reqobs = new RequestObserver[nstages];
            for( int i = 0; i < m_reqobs.length; i++ )
                m_reqobs[i] = new RequestObserver( getTaskConsumer( i, 0 ) );
        }
    }


    @Override
    public ExecutionExtensions.ExecutedRequestObserver getExecutionRequestObserver(int exectid)
    {
        return m_reqobs != null ? m_reqobs[exectid] : ExecutionExtensions.ExecutedRequestObserver.EMPTY;
    }
}

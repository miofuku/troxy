package reptor.replct.agree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;

import reptor.chronos.ChronosAddress;
import reptor.chronos.com.DomainEndpoint;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.link.ListMulticastLink;
import reptor.chronos.link.MulticastLink;
import reptor.chronos.link.RoundRobinPushSinkLink;
import reptor.distrbt.com.Message;


public interface AgreementPeers
{

    List<? extends DomainEndpoint<PushMessageSink<Message>>> getClientShards();
    List<? extends DomainEndpoint<PushMessageSink<Message>>> getOrderShards();
    List<? extends DomainEndpoint<PushMessageSink<Message>>> getCheckpointShards();
    List<? extends DomainEndpoint<PushMessageSink<Message>>> getViewChangeShards();
    List<? extends DomainEndpoint<PushMessageSink<Message>>> getExecutors();

    int getClientShard(short clino);
    int getInternalOrderCoordinator(long orderno);
    int getInternalCheckpointCoordinator(long orderno);
    int getInternalViewChangeCoordinator(int viewno);

    default <M extends Message> List<PushMessageSink<? super M>>
            createChannels(Collection<? extends DomainEndpoint<PushMessageSink<Message>>> recipients, ChronosAddress origin)
    {
        List<PushMessageSink<? super M>> channels = new ArrayList<>( recipients.size() );

        for( DomainEndpoint<PushMessageSink<Message>> r : recipients )
            channels.add( r.createChannel( origin ) );

        return channels;
    }

    default <M extends Message> MulticastLink<M>
            createMulticastChannel(Collection<? extends DomainEndpoint<PushMessageSink<Message>>> recipients, ChronosAddress origin)
    {
        return new ListMulticastLink<>( createChannels( recipients, origin ) );
    }

    default <M extends Message> PushMessageSink<M>
            createRoundRobinChannel(List<? extends DomainEndpoint<PushMessageSink<M>>> recipients, int[] select, ChronosAddress origin)
    {
        Preconditions.checkArgument( select.length>0 );

        if( select.length==1 )
            return recipients.get( select[ 0 ] ).createChannel( origin );
        else
        {
            List<PushMessageSink<? super M>> selected = new ArrayList<>( select.length );

            for( int i=0; i<select.length; i++ )
                selected.add( recipients.get( select[ i ] ).createChannel( origin ) );

            return new RoundRobinPushSinkLink<>( selected, 0 );
        }
    }
}

package reptor.smart;

import java.io.IOException;

import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;


public class SmartProxy extends BFTClientProxy<ServiceInvocation>
{

    public SmartProxy(ChronosScheduler<? extends SelectorDomainContext> master, short clino,
                      ClientProtocol cliprot,
                      NetworkConnectionConfiguration conconf,
                      MessageMapper mapper,
                      ReplicaGroupConfiguration grpconf, short contact,
                      short remshardno, int[] remaddrnos,
                      AuthorityInstances authinsts, KeyStore keystore)
                            throws IOException
    {
        super( master, clino, cliprot, conconf, new SmartMessageFormatter( Config.createMessageFactory(), BasicMessageDigestionStrategy.Variant.Plain, Hashing.SHA1 ),
               grpconf, contact, remshardno, remaddrnos, authinsts, keystore );
    }


    @Override
    protected BFTInvocationClient<ServiceInvocation> createInstance()
    {
        return new SmartServiceInvocationClient( this );
    }


    private static class SmartServiceInvocationClient extends BFTInvocationClient<ServiceInvocation>
    {
        public SmartServiceInvocationClient(Context cntxt)
        {
            super( cntxt );
        }


        @Override
        protected void requestReady(Request request)
        {
            assert request.getCommand().arrayOffset()==0;

            int seqnr = (int) request.getNumber();

            TOMMessage tommsg = new TOMMessage( client(), client(), seqnr, seqnr, request.getCommand().array(), 0, TOMMessageType.ORDERED_REQUEST );
            SmartRequest req = new SmartRequest( tommsg );
            req.setValid();

            int nreps = Config.SMART_CLIENT_UNICAST && seqnr > 0 ? 1 : m_cntxt.getReplicaGroupConfiguration().getNumberOfReplicas();

            for( short recipient = 0; recipient < nreps; recipient++ )
            {
                if( recipient > 0 )
                    req = req.clone();

                mapper().certifyAndSerializeMessage( req, replicaGroupCertifier().getCertifier() );
                replicaConnection().enqueueUnicast( recipient, req );
            }
        }

    }

}

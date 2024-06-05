package reptor.replct.invoke.bft;

import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.distrbt.certify.suites.AuthorityInstances;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.MessageVerifier;
import reptor.distrbt.com.connect.ConnectionConfiguration;
import reptor.distrbt.io.net.NetworkExtensions.ConnectionObserver;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.order.OrderMessages.Command;
import reptor.replct.invoke.InvocationExtensions;
import reptor.replct.invoke.InvocationReplica;


public abstract class BFTInvocationReplica implements InvocationReplica
{

    protected final ReplicaPeerGroup        m_repgroup;
    protected InvocationExtensions          m_invexts;
    protected ConnectionObserver            m_connobs;
    protected ConnectionConfiguration       m_connconf;

    protected boolean                       m_isactive = false;


    public BFTInvocationReplica(ReplicaPeerGroup repgroup)
    {
        m_repgroup  = Objects.requireNonNull( repgroup );
    }


    public BFTInvocationReplica invocationExtensions(InvocationExtensions invexts)
    {
        Preconditions.checkState( !m_isactive );

        m_invexts = Objects.requireNonNull( invexts );

        return this;
    }


    @Override
    public BFTInvocationReplica connectionObserver(ConnectionObserver connobs)
    {
        Preconditions.checkState( !m_isactive );

        m_connobs = Objects.requireNonNull( connobs );

        return this;
    }


    public BFTInvocationReplica activate()
    {
        Preconditions.checkState( !m_isactive );

        m_isactive = true;
        m_connconf = getInvocation().getConnections().createReplicaToClientConnectionConfiguration( m_connobs );

        return this;
    }


    public ConnectionConfiguration getClientConnectionConfiguration()
    {
        return m_connconf;
    }


    public abstract MessageVerifier<? super Command> createProposalVerifier(short ordershard, AuthorityInstances authinsts, MessageMapper mapper);


    @Override
    public abstract BFTInvocation getInvocation();


    public InvocationExtensions getInvocationExtensions()
    {
        return m_invexts;
    }


    public ReplicaPeerGroup getReplicaGroup()
    {
        return m_repgroup;
    }


    @Override
    public ConnectionObserver getConnectionObserver()
    {
        return m_connobs;
    }

}

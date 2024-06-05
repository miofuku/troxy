package reptor.replct.agree.view;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.chronos.Orphic;
import reptor.distrbt.com.Message;
import reptor.replct.ProtocolHandler;
import reptor.replct.agree.View;
import reptor.replct.agree.view.InternalViewChangeMessages.ViewChangeVerificationMessage;


public class LocalViewChangeVerificationProtocol
{

    public static abstract class Handler<M extends ViewChangeNetworkMessage> implements ProtocolHandler
    {

        private static final Logger s_logger = LoggerFactory.getLogger( Handler.class );

        protected final Orphic     m_cntxt;
        protected final short      m_shardno;
        protected final boolean    m_isintcoord;

        protected int m_staviewno = -1;
        protected int m_curviewno = -1;

        protected ViewChangeContentCertifier<M>[] m_certifs = null;


        public Handler(Orphic cntxt)
        {
            m_cntxt      = cntxt;
            m_shardno    = -1;
            m_isintcoord = false;
        }


        public Handler(Orphic cntxt, short shardno, boolean isintcoord)
        {
            m_cntxt      = cntxt;
            m_shardno    = shardno;
            m_isintcoord = isintcoord;
        }


        protected void init(ViewChangeContentCertifier<M>[] certifs)
        {
            m_certifs = certifs;
        }


        protected void initStableView(View view)
        {
            m_staviewno = m_curviewno = view.getNumber();

            // Do we have a group reconfiguration? Okay, for now, it's only possible for the initial view.
            if( m_isintcoord )
            {
                for( ViewChangeContentCertifier<M> certif : m_certifs )
                    if( certif.getViewNumber()<m_curviewno )
                        initCertifier( certif, m_curviewno );
            }
        }


        protected boolean forwardExternalMessage(M msg)
        {
            s_logger.debug( "{} received {} at {}-{}", m_cntxt, msg, m_curviewno, m_staviewno );

            if( !isOutdated( msg ) )
                enqueueForViewChangeCoordinator( msg );

            return false;
        }


        protected boolean handleExternalMessage(M msg)
        {
            assert m_isintcoord;

            if( s_logger.isDebugEnabled() && msg.getShardNumber()==m_shardno )
                s_logger.debug( "{} received {} at {}-{}", m_cntxt, msg, m_curviewno, m_staviewno );

            if( isOutdated( msg ) )
                return false;

            ViewChangeContentCertifier<M> certif = m_certifs[ msg.getSender() ];

            if( certif.getViewNumber()>msg.getViewNumber() )
                return false;
            // TODO: The message has not been verified yet, we were actually not allowed to do that!
            else if( certif.getViewNumber()<msg.getViewNumber() )
            // TODO: Cancel ongoing verification jobs.
                initCertifier( certif, msg.getViewNumber() );

            if( !certif.addMessage( msg ) || !certif.isComplete() && !certif.isConfirmed()  )
                return false;
            else if( !certif.isConfirmed() )
                return messageComplete( certif );
            else
                return messageConfirmed( certif );
        }


        protected abstract boolean messageComplete(ViewChangeContentCertifier<M> certif);


        protected boolean instructConfirmMessage(ViewChangeVerificationMessage<M[]> confmsg)
        {
            enqueueForVerificationAcceptors( confmsg );
            return false;
        }


        protected boolean handleConfirmMessage(ViewChangeVerificationMessage<M[]> confmsg)
        {
            if( isOutdated( confmsg ) )
                return false;

            return confirmMessage( confmsg );
        }


        protected abstract boolean confirmMessage(ViewChangeVerificationMessage<M[]> confmsg);


        protected boolean notifyMessageShardConfirmed(ViewChangeVerificationMessage<M> msgconf)
        {
            enqueueForViewChangeCoordinator( msgconf );
            return false;
        }


        public boolean handleMessageShardConfirmed(ViewChangeVerificationMessage<M> msgconf)
        {
            assert m_isintcoord;

            if( isOutdated( msgconf ) )
                return false;

            M msg = msgconf.getMessage();
            ViewChangeContentCertifier<M> certif = m_certifs[ msg.getSender() ];

            if( certif.getViewNumber()>msg.getViewNumber() )
                return false;

            if( !certif.messageConfirmed( msg ) )
                return false;
            else
                return messageConfirmed( certif );
        }


        protected abstract boolean messageConfirmed(ViewChangeContentCertifier<M> certif);


        protected boolean notifyResult(ViewDependentMessage result)
        {
            assert m_isintcoord;
            assert result.getViewNumber()>m_curviewno;

            m_curviewno = result.getViewNumber();

            enqueueForVerificationLearners( result );
            return false;
        }


        protected boolean isOutdated(ViewDependentMessage msg)
        {
            // Is it an outdated message or have we already seen a NEW_VIEW for the view in question?
            return msg.getViewNumber()<m_curviewno || msg.getViewNumber()==m_staviewno;
        }


        protected void initCertifier(ViewChangeContentCertifier<M> certif, int viewno)
        {
            certif.initJob( viewno );
        }


        protected abstract void enqueueForViewChangeCoordinator(Message msg);

        protected abstract void enqueueForVerificationAcceptors(Message msg);

        protected abstract void enqueueForVerificationLearners(Message msg);

    }

}

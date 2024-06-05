package reptor.replct.agree.view;

import java.util.Collection;

import reptor.jlib.collect.Slots;


public class LocalViewChangePreparationCollector
{

    private Slots<ViewChangeShardMessage> m_ordervcs;
    private Slots<ViewChangeShardMessage> m_chkptvcs;
    private Slots<ViewChangeShardMessage> m_vwchgvcs;

    private boolean m_iscompl = false;


    public LocalViewChangePreparationCollector(int nordervcs, int nchkptvcs, int nvwchgvcs)
    {
        m_ordervcs = new Slots<>( nordervcs );
        m_chkptvcs = new Slots<>( nchkptvcs );
        m_vwchgvcs = new Slots<>( nvwchgvcs );
    }


    public boolean isComplete()
    {
        return m_iscompl;
    }


    public boolean addViewChange(ViewChangeShardMessage vc)
    {
        Slots<ViewChangeShardMessage> slots;

        switch( vc.getTypeID() )
        {
        case ViewChangeMessages.ORDER_SHARD_VIEW_CHANGE_ID:
            slots = m_ordervcs;
            break;
        case ViewChangeMessages.CHECKPOINT_SHARD_VIEW_CHANGE_ID:
            slots = m_chkptvcs;
            break;
        case ViewChangeMessages.VIEW_SHARD_VIEW_CHANGE_ID:
            slots = m_vwchgvcs;
            break;
        default:
            throw new IllegalStateException();
        }

        Object prev = slots.put( vc.getShardNumber(), vc );
        assert prev==null;

        return checkComplete();
    }


    public Collection<ViewChangeShardMessage> getOrderShardViewChanges()
    {
        return m_ordervcs;
    }

    public Collection<ViewChangeShardMessage> getCheckpointShardViewChanges()
    {
        return m_chkptvcs;
    }

    public Collection<ViewChangeShardMessage> getViewShardViewChanges()
    {
        return m_vwchgvcs;
    }


    private boolean checkComplete()
    {
        if( m_iscompl || m_ordervcs.emptySlotsCount()>0 || m_chkptvcs.emptySlotsCount()>0 || m_vwchgvcs.emptySlotsCount()>0 )
            return false;
        else
        {
            m_iscompl = true;
            return true;
        }
    }

}

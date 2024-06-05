package reptor.bench.compose;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.common.base.Preconditions;

import reptor.chronos.ChronosAddress;
import reptor.chronos.ChronosTask;
import reptor.chronos.domains.DomainThread;
import reptor.chronos.schedule.GenericScheduler;
import reptor.chronos.schedule.SingleRoundScheduler;
import reptor.distrbt.domains.SelectorDomain;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.jlib.NotImplementedException;
import reptor.jlib.threading.SystemThread;
import reptor.replct.common.settings.SettingsReader;


public class DomainGroup
{

    private final int[]                         m_procaff;
    private final int[]                         m_threadaff;
    private final SelectorDomainBuilder[]       m_dombuilders;
    private final Map<TaskType, int[]>          m_taskassign  = new HashMap<>();
    private final Map<TaskType, int[]>          m_groupassign = new HashMap<>();

    private DomainThread[]                                      m_domains;
    private List<SingleRoundScheduler<SelectorDomainContext>>   m_schedulers;
    private Map<TaskType, SortedMap<Integer, Collection<ChronosTask>>> m_unassigned = new HashMap<>();


    private DomainGroup(SettingsReader reader, String group, String defname, Map<TaskType, Integer> ntasks)
    {
        m_procaff   = reader.getIntArray( group + ".affinity", null );
        m_threadaff = reader.getIntArray( "schedulers." + group + ".affinity", null );

        int initno = reader.getInt( "schedulers." + group + ".init_number", 1 );

        List<String> addscheds = new LinkedList<>();

        for( TaskType tt : ntasks.keySet() )
        {
            String key = configKey( tt );
            int[] g = reader.getIntArray( "schedulers.task." + key + ".group", null );

            if( g!=null )
                m_groupassign.put( tt, g );
            else
            {
                int[] s = tt==TaskType.NETWORK_ENDPOINT ?
                        new int[] { 0 } : reader.getIntArray( "schedulers.task." + key, null );

                if( s==null )
                {
                    int nt = ntasks.get( tt );

                    s = new int[ nt ];

                    for( int i=0; i<nt; i++ )
                    {
                        s[ i ] = initno + addscheds.size();
                        addscheds.add( tt.name() + i );
                    }
                }

                if( s.length>0 )
                    m_taskassign.put( tt, s );
            }
        }

        m_dombuilders = new SelectorDomainBuilder[ initno+addscheds.size() ];

        for( int i=0; i<initno; i++ )
        {
            String name = reader.getString( "schedulers." + group + "." + i, defname + i );
            int[] aff   = reader.getIntArray( "schedulers." + group + "." + i + ".affinity", m_threadaff );

            m_dombuilders[ i ] = new SelectorDomainBuilder( name, aff );
        }

        int i = initno;
        for( String n : addscheds )
            m_dombuilders[ i++ ] = new SelectorDomainBuilder( n, m_threadaff );
    }


    public static String configKey(TaskType type)
    {
        TaskType base = type==TaskType.VIEW_SHARD ? TaskType.ORDER_SHARD : type;

        switch( base )
        {
        case CLIENT_SHARD:
            return "client_stage";
        case VIEW_SHARD:
            return "view_change_stage";
        case ORDER_SHARD:
            return "order_stage";
        case CHECKPOINT_SHARD:
            return "checkpoint_stage";
        case EXECUTOR:
            return "execution_stage";
        case WORKER:
            return "worker_stage";
        case NETWORK_ENDPOINT:
            return "network_endpoint";
        case REPLICA_NETWORK:
            return "replica_network_endpoint_worker";
        default:
            throw new NotImplementedException( base.toString() );
        }
    }


    public static DomainGroup load(SettingsReader reader, String group, String defname,
                                          Map<TaskType, Integer> ntasks)
    {
        return new DomainGroup( reader, group, defname, ntasks );
    }


    public int getNumberOfDomains()
    {
        return m_dombuilders.length;
    }


    public void init()
    {
        Preconditions.checkState( m_domains==null );

        if( m_procaff!=null )
            SystemThread.setProcessAffinity( m_procaff );

        m_domains    = new DomainThread[ getNumberOfDomains() ];
        m_schedulers = new ArrayList<>( getNumberOfDomains() );

        for( int i=0; i<m_domains.length; i++ )
        {
            m_domains[ i ] = m_dombuilders[ i ].createDomain();

            m_schedulers.add( new SingleRoundScheduler<>() );
            ((SelectorDomain) m_domains[ i ].getDomain()).bindTask( m_schedulers.get( i ) );
        }
    }


    public List<? extends GenericScheduler<SelectorDomainContext>> getSchedulers()
    {
        return m_schedulers;
    }


    public GenericScheduler<SelectorDomainContext> getMaster(int no)
    {
        return m_schedulers.get( no );
    }


    public GenericScheduler<SelectorDomainContext> masterForTask(TaskType type, int no)
    {
        return m_schedulers.get( getDomainForTask( type, no ) );
    }


    public int getDomainForTask(TaskType type, int no)
    {
        int[] sids = m_taskassign.get( type );
        return sids[ no % sids.length ];
    }


    public GenericScheduler<SelectorDomainContext> masterForGroup(TaskType type, int no)
    {
        return m_schedulers.get( domainForGroup( type, no ) );
    }


    public int domainForGroup(TaskType type, int no)
    {
        int[] sids = m_groupassign.get( type );

        return sids[ no % sids.length ];
    }


    public ChronosAddress addressForDomain(int domno)
    {
        return m_domains[ domno ].getDomain().getAddress();
    }


    public <T extends ChronosTask> T registerTask(GenericScheduler<?> scheduler, T task, TaskType tt, int groupno)
    {
        scheduler.registerTask( task );

        SortedMap<Integer, Collection<ChronosTask>> group = m_unassigned.get( tt );

        if( group == null )
        {
            group = new TreeMap<>();
            m_unassigned.put( tt, group );
        }

        Collection<ChronosTask> entry = group.get( groupno );

        if( entry == null )
        {
            entry = new LinkedList<ChronosTask>();
            group.put( groupno, entry );
        }
        entry.add( task );

        return task;
    }


    public DomainGroup activate()
    {
        return this;
    }


    public DomainGroup start()
    {
        for( DomainThread dom : m_domains )
            dom.init();

        for( DomainThread dom : m_domains )
            dom.start();

        return this;
    }


    public DomainThread[] getDomains()
    {
        return m_domains;
    }


    public SelectorDomainBuilder[] getDomainBuilders()
    {
        return m_dombuilders;
    }


    public Map<TaskType, int[]> getModuleAssignment()
    {
        return m_taskassign;
    }


    public Map<TaskType, int[]> getGroupAssignment()
    {
        return m_groupassign;
    }


    public int[] getProcessAffinity()
    {
        return m_procaff;
    }


    public int[] getDefaultThreadAffinity()
    {
        return m_threadaff;
    }

}

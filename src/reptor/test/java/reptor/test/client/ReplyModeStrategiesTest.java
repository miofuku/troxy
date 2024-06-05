package reptor.test.client;

import reptor.distrbt.common.data.ImmutableData;
import reptor.replct.common.WorkDistribution;
import reptor.replct.invoke.ReplyMode;
import reptor.replct.invoke.ReplyModeStrategy;
import reptor.replct.invoke.ReplyModeStrategyInstance;
import reptor.replct.invoke.InvocationMessages.Request;
import reptor.replct.invoke.ReplyModeStrategies.AllButLeaderReply;
import reptor.replct.invoke.ReplyModeStrategies.AllButXReply;
import reptor.replct.invoke.ReplyModeStrategies.AllReply;


public class ReplyModeStrategiesTest
{

    private static final short                  NREPLICAS    = 4;
    private static final short                  NFAULTS      = 1;
    private static final short                  NORDERSHARDS = 2;
    private static final ImmutableData          RESULT       = ImmutableData.EMPTY;
    private static final Request                REQUEST      = new Request( (short) 0, 0, ImmutableData.EMPTY, false, false );

//    private static final WorkDistribution   LEADERDIST   = new WorkDistribution.Continuous( 1 );
//    private static final WorkDistribution   FULLDIST     = new WorkDistribution.Blockwise( NREPLICAS-1, NORDERSHARDS );

//    private static final WorkDistribution   LEADERDIST   = new WorkDistribution.RoundRobin( NREPLICAS );
    private static final WorkDistribution   LEADERDIST   = new WorkDistribution.Blockwise( NREPLICAS, NORDERSHARDS );
//    private static final WorkDistribution   LEADERDIST   = new WorkDistribution.Skewed( NREPLICAS );
    private static final WorkDistribution   FULLDIST       = new WorkDistribution.Continuous( 0 );

    private static final WorkDistribution   NOREPLYDIST    = new WorkDistribution.Blockwise( NREPLICAS, NORDERSHARDS );
//    private static final WorkDistribution   NOREPLYDIST    = new WorkDistribution.RoundRobin( NREPLICAS );


    private static String toString(ReplyMode rm)
    {
        switch( rm )
        {
        case None:
            return " ";
        case Hashed:
            return "h";
        default:
            return "f";
        }
    }


    private static void testStrategy(ReplyModeStrategy rmstrat)
    {
        System.out.println( rmstrat );

        ReplyModeStrategyInstance[] rminsts = new ReplyModeStrategyInstance[ NREPLICAS ];

        for( short repno=0; repno<NREPLICAS; repno++ )
            rminsts[ repno ] = rmstrat.createInstance( repno, NREPLICAS, NFAULTS );

        for( long locodrno=0; locodrno<20; locodrno++ )
        {
            System.out.print( String.format( "%3d", locodrno ) );

            for( ReplyModeStrategyInstance rm : rminsts )
            {
                rm.initOrdered( locodrno, locodrno, (short) LEADERDIST.getStageForUnit( locodrno ) );

                System.out.print( " " + toString( rm.replyMode( REQUEST, RESULT ) ) );
            }

            System.out.println();
        }

        System.out.println();
    }


    public static void main(String[] args)
    {
        testStrategy( new AllReply( 0 ) );
        testStrategy( new AllButLeaderReply( 0, FULLDIST ) );
        testStrategy( new AllButXReply( 0, NOREPLYDIST, (short) 2 ) );
    }

}

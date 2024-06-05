package reptor.test;

import reptor.replct.common.WorkDistribution;


public class TestInstanceDistribution
{
    private static final int NO = 6;
    private static final int NR = 4;
    private static final int NI = 100;


    public static void test(WorkDistribution dist)
    {
        for( long i = 0; i < NI; i++ )
        {
            int o = dist.getStageForUnit( i );
            long s = dist.getSlotForLocalUnit( o, i );
            System.out.println( String.format( "Instance %d -> stage %d seq %d", i, o, s ) );
        }

        for( long i = 0; i < NI; i++ )
        {
            System.out.print( String.format( "Instance %3d:", i ) );
            for( int o = 0; o < NO; o++ )
                System.out.print( String.format( " %3d", dist.getNextLocalUnit( o, i ) ) );
            System.out.print( "   " );
            for( int o = 0; o < NO; o++ )
                System.out.print( String.format( " %3d",
                        dist.getSlotForLocalUnit( o, dist.getNextLocalUnit( o, i ) ) ) );
            System.out.print( "   " );
            for( int o = 0; o < NO; o++ )
                System.out.print( String.format( " %3d", dist.getSlotForUnit( o, i ) ) );
            System.out.println();
        }

        WorkDistribution.WorkIterator[] iters = new WorkDistribution.WorkIterator[NO];
        for( int o = 0; o < NO; o++ )
            iters[o] = dist.getUnitIterator( o, 0 );

        for( long s = 0; s < NI; s++ )
        {
            System.out.print( String.format( "Slot %3d:", s ) );
            for( int o = 0; o < NO; o++ )
                System.out.print( String.format( " %3d", dist.getUnitForSlot( o, s ) ) );
            System.out.print( "   " );
            for( int o = 0; o < NO; o++ )
                System.out.print( String.format( " %3d", iters[o].nextUnit() ) );

            System.out.println();
        }

        WorkDistribution[] outerdists = new WorkDistribution[]
        {
                new WorkDistribution.RoundRobin( NR ),
                new WorkDistribution.Blockwise( NR, NO ),
                new WorkDistribution.Skewed( NR )
        };

        for( long s = 0; s < NI; s++ )
        {
            System.out.print( String.format( "Slot %3d:", s ) );
            for( WorkDistribution od : outerdists )
            {
                for( int o = 0; o < NO; o++ )
                {
                    int r = od.getStageForUnit( dist.getUnitForSlot( o, s ) );
                    System.out.print( String.format( " %3d", r ) );
                }
                System.out.print( "   " );
            }

            System.out.println();
        }
    }


    public static void main(String[] args)
    {
        test( new WorkDistribution.Continuous( 0 ) );
        test( new WorkDistribution.RoundRobin( NO ) );
        test( new WorkDistribution.Blockwise( NO, NR ) );
        test( new WorkDistribution.Skewed( NO ) );
    }
}

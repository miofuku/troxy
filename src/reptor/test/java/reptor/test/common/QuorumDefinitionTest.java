package reptor.test.common;

import java.util.ArrayList;
import java.util.List;

import reptor.replct.common.quorums.FBasedQuorumDefinition;
import reptor.replct.common.quorums.IntersectingQuorumDefinition;
import reptor.replct.common.quorums.QuorumDefinition;


public class QuorumDefinitionTest
{

    private static void testCounterQuorum()
    {
        List<String>           in = new ArrayList<>();
        List<QuorumDefinition> id = new ArrayList<>();
        in.add( "i0" );
        id.add( new IntersectingQuorumDefinition( 0 ) );
        in.add( "i1" );
        id.add( new IntersectingQuorumDefinition( 1 ) );
        in.add( "i2" );
        id.add( new IntersectingQuorumDefinition( 2 ) );

        for( int nfaults=0; nfaults<=2; nfaults++ )
        {
            System.out.println( "nfaults: " + nfaults );

            System.out.print( "   " );
            for( String n : in  )
                for( int ffac=1; ffac<=2; ffac++ )
                    System.out.print( String.format( "   %s  ", n ) );
            System.out.println();

            System.out.print( "   " );
            for( int i=0; i<in.size(); i++ )
                for( int ffac=1; ffac<=2; ffac++ )
                    System.out.print( String.format( "   f%d  ", ffac ) );
            System.out.println();

            for( int nprocs=1; nprocs<=15; nprocs++ )
            {
                System.out.print( String.format( "%2d:", nprocs ) );
                for( QuorumDefinition d : id )
                {
                    for( int ffac=1; ffac<=2; ffac++ )
                    {
                        int qs = ffac*nfaults+1;
                        int cs = d.counterQuorumSize( nprocs, nfaults, qs );
                        String s = d.isQuorumSizeSupported( nprocs, nfaults, qs ) ? " " : "!";
                        System.out.print( String.format( " %s%2d/%2d", s, qs, cs ) );
                    }
                }
                System.out.println();
            }

            System.out.println();
        }
    }


    private static void testQuorumTypes()
    {
        List<String>           qn = new ArrayList<>();
        List<QuorumDefinition> qd = new ArrayList<>();

        qn.add( "f0" );
        qd.add( new FBasedQuorumDefinition( 0 ) );
        qn.add( "f1" );
        qd.add( new FBasedQuorumDefinition( 1 ) );
        qn.add( "f2" );
        qd.add( new FBasedQuorumDefinition( 2 ) );

        qn.add( "i0" );
        qd.add( new IntersectingQuorumDefinition( 0 ) );
        qn.add( "i1" );
        qd.add( new IntersectingQuorumDefinition( 1 ) );
        qn.add( "i2" );
        qd.add( new IntersectingQuorumDefinition( 2 ) );

        System.out.print( "   " );
        for( String n : qn )
            System.out.print( String.format( "   %s  ", n ) );
        System.out.println();

        for( int nprocs=1; nprocs<=15; nprocs++ )
        {
            System.out.print( String.format( "%2d:", nprocs ) );
            for( QuorumDefinition d : qd )
            {
                int f = d.tolerableFaults( nprocs );

                System.out.print( String.format( "  %2d   ", f ) );
            }
            System.out.println();
        }

        System.out.println();

        for( int nfaults=0; nfaults<=2; nfaults++ )
        {
            System.out.println( "nfaults: " + nfaults );

            System.out.print( "   " );
            for( String n : qn )
                System.out.print( String.format( "   %s  ", n ) );
            System.out.println();

            for( int nprocs=1; nprocs<=15; nprocs++ )
            {
                System.out.print( String.format( "%2d:", nprocs ) );
                for( QuorumDefinition d : qd )
                {
                    String s = d.isGroupSupported( nprocs, nfaults ) ? " " : "!";
                    int us = d.upperQuorumSize( nprocs, nfaults );
                    int ls = d.lowerQuorumSize( nprocs, nfaults );

                    System.out.print( String.format( " %s%2d/%2d", s, us, ls ) );
                }
                System.out.println();
            }

            System.out.println();
        }
    }


    public static void main(String[] args)
    {
        testQuorumTypes();
        testCounterQuorum();
    }

}

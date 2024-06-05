package reptor.replct.common.quorums;


public class QuorumDefinitions
{

    public static final FBasedQuorumDefinition       CORRECT_MEMBER       = new FBasedQuorumDefinition( 1 );
    public static final IntersectingQuorumDefinition ANY_INTERSECTION     = new IntersectingQuorumDefinition( 0 );
    public static final IntersectingQuorumDefinition CORRECT_INTERSECTION = new IntersectingQuorumDefinition( 1 );

}

package reptor.replct.common.quorums;


public interface QuorumDefinition
{
    int     tolerableFaults(int nprocs);
    int     minimumProcesses(int nfaults);

    boolean isGroupSupported(int nprocs, int nfaults);
    int     upperQuorumSize(int nprocs, int nfaults);
    int     lowerQuorumSize(int nprocs, int nfaults);

    boolean isQuorumSizeSupported(int nprocs, int nfaults, int quorumsize);
    int     counterQuorumSize(int nprocs, int nfaults, int quorumsize);
}

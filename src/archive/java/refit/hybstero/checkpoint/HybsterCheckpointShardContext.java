package refit.hybstero.checkpoint;

import distrbt.com.transmit.MessageTransmitter;
import reptor.chronos.ChronosDomainContext;
import reptor.distrbt.certify.VerifierGroup;
import reptor.distrbt.com.MessageMapper;

// Actually checkpoint shard processor context.
public interface HybsterCheckpointShardContext
{
    ChronosDomainContext getDomainContext();

    MessageMapper        getMessageMapper();
    VerifierGroup   getReplicaTCVerifiers();
    VerifierGroup   getReplicaTMVerifiers();

    MessageTransmitter   getReplicaTCTransmitter();
    MessageTransmitter   getReplicaTMTransmitter();
    MessageTransmitter   getReplicaTransmitter();
}

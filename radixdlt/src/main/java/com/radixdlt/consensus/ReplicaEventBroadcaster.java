package com.radixdlt.consensus;

public interface ReplicaEventBroadcaster {
    void broadcastReplicaEvent(ReplicaConsensusEvent event);
}

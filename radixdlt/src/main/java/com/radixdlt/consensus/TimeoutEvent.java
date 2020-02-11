package com.radixdlt.consensus;

public class TimeoutEvent implements ReplicaConsensusEvent {
    private int indexOfRoundThatTimedOut;

    public TimeoutEvent(int indexOfRoundThatTimedOut) {
        this.indexOfRoundThatTimedOut = indexOfRoundThatTimedOut;
    }

    public int getIndexOfRoundThatTimedOut() {
        return indexOfRoundThatTimedOut;
    }
}

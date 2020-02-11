package com.radixdlt.consensus;

public class TimeoutProcessor {
    private Pacemaker pacemaker;
    public TimeoutProcessor(Pacemaker pacemaker) {
        this.pacemaker = pacemaker;
    }

    public void processLocalTimeout(int round) {
        if (!pacemaker.resetLocalTimeoutIfCurrentRoundsEquals(round)) {
            return;
        }

//        int lastVotedRound =
    }
}

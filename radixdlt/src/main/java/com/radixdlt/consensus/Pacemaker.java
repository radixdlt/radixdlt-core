package com.radixdlt.consensus;

import com.radixdlt.consensus.tempo.Scheduler;
import org.radix.network2.TimeSupplier;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class Pacemaker {

    // This should probably be moved to somewhere else. 3 because 3-chain of HotStuff/Cerberus
    public static final int NUMBER_OF_ROUNDS_PER_INSTANCE = 3;

    private int currentRound;
    private int highestCommittedRound;

    /**
     *  The deadline for the next local timeout event. It is reset every time a new round start, or a previous deadline expires.
     */
    private long currentRoundDeadline;

    private PacemakerTimeInterval timeInterval;
    private TimeSupplier timeSupplier;
    private ReplicaEventBroadcaster eventBroadcaster;
    private Scheduler scheduler;

    public Pacemaker(
            PacemakerTimeInterval timeInterval,
            TimeSupplier timeSupplier,
            ReplicaEventBroadcaster eventBroadcaster,
            Scheduler scheduler
    ) {
        this.timeInterval = Objects.requireNonNull(timeInterval, "'timeInterval' is required");
        this.timeSupplier =  Objects.requireNonNull(timeSupplier, "'timeSupplier' is required");
        this.eventBroadcaster = Objects.requireNonNull(eventBroadcaster, "'eventBroadcaster' is required");
        this.scheduler =  Objects.requireNonNull(scheduler, "'scheduler' is required");

        this.currentRound = 0;
        this.highestCommittedRound = 0;
        this.currentRoundDeadline = timeSupplier.currentTime();
    }

    /**
     * In case the local timeout corresponds to the current round, reset the timeout and return {@code true}. Otherwise ignore and return {@code false}.
     * @param round The round to setup a local timeout for - if needed.
     * @return Whether the a local timeout was setup or not.
     */

    public boolean resetLocalTimeoutIfCurrentRoundsEquals(int round) {
        if (round != this.currentRound) {
            return false;
        }
        // Libra `pacemakers.rs#L192`
//        counters::TIMEOUT_COUNT.inc();
        this.setupTimeout();
        return true;
    }

    public int currentRound() {
        return currentRound;
    }

    public int highestCommittedRound() {
        return highestCommittedRound;
    }

    /**
     * Setup the timeout task an return the duration of the current timeout
     * @return The current timeout
     */
    private long setupTimeout() {
        // Porting of: https://github.com/libra/libra/blob/master/consensus/src/chained_bft/liveness/pacemaker.rs#L234-L246
        long timeout = this.setupDeadline();
        scheduler.schedule(() -> broadcastTimeout(), timeout, TimeUnit.NANOSECONDS);
        return timeout;
    }

    /**
     * Setup the current round deadline and return the duration of the current round
     * @return The duration of the current round
     */
    private long setupDeadline() {
        // Porting of: https://github.com/libra/libra/blob/master/consensus/src/chained_bft/liveness/pacemaker.rs#L248-L273
        final int roundIndexAfterCommittedRound;
        if (this.highestCommittedRound == 0) {
            // Genesis doesn't require the 3-chain rule for commit, hence start the index at the round after genesis.
            roundIndexAfterCommittedRound = this.currentRound - 1;
        } else if (this.currentRound < (this.highestCommittedRound + NUMBER_OF_ROUNDS_PER_INSTANCE)) {
            roundIndexAfterCommittedRound = 0;
        } else {
            roundIndexAfterCommittedRound = this.currentRound - this.highestCommittedRound - NUMBER_OF_ROUNDS_PER_INSTANCE;
        }

        final long timeout = this.timeInterval.durationOfRound(roundIndexAfterCommittedRound);

        final long deadline = this.timeSupplier.currentTime() + timeout;
        this.currentRoundDeadline = deadline;
        return timeout;
    }

    /**
     * Broadcasts a {@link TimeoutEvent} to other Replicas.
     */
    private void broadcastTimeout() {
        final TimeoutEvent timeoutEvent = new TimeoutEvent(this.currentRound);
        eventBroadcaster.broadcastReplicaEvent(timeoutEvent);
    }
}

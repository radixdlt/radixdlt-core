package com.radixdlt.consensus;

/**
 * Determines the maximum round duration based on the round difference between the current
 * round and the committed round
 */
public interface PacemakerTimeInterval {

    /**
     *  Use the index of the round after the highest {@link QuorumCertificate} to commit a block and
     *  return the duration for this round
     *
     *  Round indices start at 0 (round index = 0 is the first round after the round that led
     *  to the highest committed round).  Given that round r is the highest round to commit a
     *  block, then round index 0 is round r+1.
     *
     *  Note that for genesis does not follow the 3-chain rule for commits, so round 1 has
     *  round index 0.  For example, if one wants to calculate the round duration of round
     *  6 and the highest committed round is 3 (meaning the highest round to commit a block
     *  is round 5, then the round index is 0).
     * @param roundIndexAfterCommittedQC the index of the round after the highest known {@link QuorumCertificate}
     * @return The duration of the round
     */
    long durationOfRound(int roundIndexAfterCommittedQC);
}

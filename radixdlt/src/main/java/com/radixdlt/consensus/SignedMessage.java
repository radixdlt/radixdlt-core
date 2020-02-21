package com.radixdlt.consensus;

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.crypto.Signature;

import java.util.Objects;

public final class SignedMessage {
    private Hash hash;
    private Signature signature;
    private ECPublicKey publicKey;

    public SignedMessage(Hash hash, Signature signature, ECPublicKey publicKey) {
        this.hash = Objects.requireNonNull(hash);
        this.signature = Objects.requireNonNull(signature);
        this.publicKey = Objects.requireNonNull(publicKey);
    }

    public Hash hash() {
        return hash;
    }

    public Signature signature() {
        return signature;
    }

    public ECPublicKey publicKey() {
        return publicKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SignedMessage that = (SignedMessage) o;
        return Objects.equals(hash, that.hash)
                && Objects.equals(signature, that.signature)
                && Objects.equals(publicKey, that.publicKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash, signature, publicKey);
    }

    @Override
    public String toString() {
        return String.format(
                "%s{hash=%s, signature=%s, publicKey=%s}",
                getClass().getSimpleName(),
                hash, signature, publicKey
        );
    }
}

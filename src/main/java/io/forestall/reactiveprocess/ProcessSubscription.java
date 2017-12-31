package io.forestall.reactiveprocess;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ProcessSubscription implements Flow.Subscription {

    private final AtomicLong balance;
    private final AtomicBoolean active;

    public ProcessSubscription() {
        balance = new AtomicLong(0L);
        active = new AtomicBoolean(true);
    }

    /**
     * Snapshot of the current balance between what we have sent and what has been requested.
     * This should never be negative as it would indicate we sent more than was asked for.
     *
     * @return current balance
     */
    public long getBalance() {
        return balance.get();
    }

    /**
     * When sending items to this subscription, log the number sent here.
     *
     * @param n number of items sent to this subscription
     */
    public void sent(long n) {
        if (n < 0)
            throw new IllegalArgumentException("Could only have sent a non-negative number of items.");

        balance.addAndGet(-n);
    }

    @Override
    public void request(long n) {
        if (n < 0)
            throw new IllegalArgumentException("Can only request a non-negative number of items.");

        balance.addAndGet(n);
    }

    @Override
    public void cancel() {
        active.set(false);
    }
}

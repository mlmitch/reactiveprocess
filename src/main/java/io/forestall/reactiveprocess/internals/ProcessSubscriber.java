package io.forestall.reactiveprocess.internals;

import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;

public class ProcessSubscriber implements Flow.Subscriber<InputStream> {

    private final ExecutorService subscriptionExecutor;

    private Flow.Subscription subscription;
    private long outstandingRequests;

    private final ConcurrentLinkedQueue<InputStream> input;

    private static final long BUFFER_SIZE = 2 * Flow.defaultBufferSize();
    private static final long REQUEST_THRESHOLD = Flow.defaultBufferSize();

    public ProcessSubscriber() {
        subscriptionExecutor = Executors.newSingleThreadExecutor();
        subscription = null;
        outstandingRequests = 0;

        input = new ConcurrentLinkedQueue<>();
    }

    public Optional<InputStream> get() {
        return Optional.ofNullable(input.poll());
    }

    public void cancelSubscription() {
        subscriptionExecutor.submit(() -> {
            subscription.cancel();
            subscription = null;
        });
    }

    @Override
    public void onNext(InputStream item) {
        if (null == item) {
            throw new NullPointerException("Null InputStreams not allowed.");
        }

        //book keep and request more if warranted
        subscriptionExecutor.submit(() -> {
            outstandingRequests--;
            if (outstandingRequests < REQUEST_THRESHOLD && null != subscription) {
                subscription.request(BUFFER_SIZE - outstandingRequests);
                outstandingRequests = BUFFER_SIZE;
            }
        });

        //always true for this type of queue
        input.add(item);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        if (null == subscription) {
            throw new NullPointerException("Null Subscriptions not allowed.");
        }

        subscriptionExecutor.submit(() -> {
            if (null == this.subscription) {
                this.subscription = subscription;
                this.subscription.request(BUFFER_SIZE);
                outstandingRequests = BUFFER_SIZE;
            } else {
                subscription.cancel();
            }
        });
    }

    @Override
    public void onError(Throwable throwable) {
        if (null == throwable) {
            throw new NullPointerException("Null Throwables not allowed.");
        }

        //Outstanding requests will be reset when
        //another subscriber is provided
        subscriptionExecutor.submit(() -> {
            subscription = null;
        });
    }

    @Override
    public void onComplete() {
        //Outstanding requests will be reset when
        //another subscriber is provided
        subscriptionExecutor.submit(() -> {
            subscription = null;
        });
    }

}

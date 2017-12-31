package io.forestall.reactiveprocess.internals;

import io.forestall.reactiveprocess.ProcessInput;

import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;


/**
 * No blocking on Subscriber interface methods.
 *
 * @param <S>
 * @param <T>
 */
public class ProcessSubscriber<S extends InputStream, T> implements Flow.Subscriber<ProcessInput<S, T>> {

    private final ExecutorService subscriptionExecutor;

    private Flow.Subscription subscription;
    private long outstandingRequests;

    /**
     * This queue can balloon if subscription churn happens and past publishers
     * keep calling onNext.
     */
    private final ConcurrentLinkedQueue<ProcessInput<S, T>> input;
    private final AtomicLong inputSize;

    private final long bufferSize;
    private final long requestThreshold;

    public ProcessSubscriber(long bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize must be greater than 0");
        }

        subscriptionExecutor = Executors.newSingleThreadExecutor();
        subscription = null;
        outstandingRequests = 0;

        input = new ConcurrentLinkedQueue<>();
        inputSize = new AtomicLong(0);

        this.bufferSize = bufferSize;
        this.requestThreshold = bufferSize - (bufferSize / 2); //prevents from being 0
    }

    public Optional<ProcessInput<S, T>> get() {
        Optional<ProcessInput<S, T>> result = Optional.ofNullable(input.poll());

        if (result.isPresent()) {
            inputSize.decrementAndGet();
        }

        return result;
    }

    public void cancelSubscription() {
        subscriptionExecutor.submit(() -> {
            if (null != subscription) {
                subscription.cancel();
                subscription = null;
            }
        });
    }

    @Override
    public void onNext(ProcessInput<S, T> item) {
        if (null == item) {
            throw new NullPointerException("Null InputStreams not allowed.");
        }

        //always true for this type of queue
        input.add(item);

        //book keeping
        subscriptionExecutor.submit(() -> {
            outstandingRequests--;
            long localInputSize = inputSize.incrementAndGet();

            //don't strictly enforce input size
            //just make sure the queue being consumed.
            if (outstandingRequests < requestThreshold
                    && localInputSize < bufferSize
                    && null != subscription) {

                subscription.request(bufferSize - outstandingRequests);
                outstandingRequests = bufferSize;
            }
        });
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        if (null == subscription) {
            throw new NullPointerException("Null Subscriptions not allowed.");
        }

        subscriptionExecutor.submit(() -> {
            if (null == this.subscription) {
                this.subscription = subscription;
                outstandingRequests = 0;
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
            //if (null != subscription)
            subscription = null;
        });
    }

    @Override
    public void onComplete() {
        //Outstanding requests will be reset when
        //another subscriber is provided
        subscriptionExecutor.submit(() -> {
            //if (null != subscription)
            subscription = null;
        });
    }
}

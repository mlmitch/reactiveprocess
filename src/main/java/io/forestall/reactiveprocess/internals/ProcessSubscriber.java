package io.forestall.reactiveprocess.internals;

import io.forestall.reactiveprocess.ProcessInput;

import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;

import static java.lang.Long.min;


/**
 * No blocking on Subscriber interface methods.
 *
 * @param <T>
 */
public class ProcessSubscriber<T> implements Flow.Subscriber<ProcessInput<T>> {

    private final Runnable push;

    //provides thread safety on the subscription and other book keeping fields
    private final ExecutorService executor;

    private Flow.Subscription subscription;
    private long outstandingRequests;
    private boolean destroyed;

    private final ConcurrentLinkedQueue<ProcessInput<T>> input;
    //keep our own input size as ConcurrentLinkedQueue::size is O(n) and not accurate
    private long inputSize;

    private final long maxRequests;
    private final long requestThreshold;

    /**
     * @param maxRequests maximum number of outstanding requests allowed
     * @param executor    ExecutorService for running push and other internal tasks
     * @param push        function to signal the availability of elements
     */
    public ProcessSubscriber(long maxRequests, ExecutorService executor, Runnable push) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be greater than 0");
        }

        this.push = push;

        this.executor = executor;
        subscription = null;
        outstandingRequests = 0;
        destroyed = false;

        input = new ConcurrentLinkedQueue<>();
        inputSize = 0;

        this.maxRequests = maxRequests;
        this.requestThreshold = maxRequests - (maxRequests / 2); //prevents from being 0
    }

    public void unsubscribe() {
        executor.submit(() -> {
            if (null != subscription) {
                subscription.cancel();
                subscription = null;
            }
        });
    }

    /**
     * Unsubscribe and prevent future subscriptions
     */
    public void destroy() {
        executor.submit(() -> {
            destroyed = true;
            if (null != subscription) {
                subscription.cancel();
                subscription = null;
            }
        });
    }

    public Optional<ProcessInput<T>> supply() {
        Optional<ProcessInput<T>> result = Optional.ofNullable(input.poll());

        if (result.isPresent()) {
            //successfully got an element
            executor.submit(() -> {
                inputSize--;
                pull();
            });
        }

        return result;
    }

    @Override
    public void onNext(ProcessInput<T> item) {
        if (null == item) {
            throw new NullPointerException("Null InputStreams not allowed.");
        }

        //always true for this type of queue
        input.add(item);

        //book keeping
        executor.submit(() -> {
            outstandingRequests--;
            inputSize++;
            pull();

            //signal that something has become available
            push.run();
        });
    }

    //only call from within subscription executor
    private void pull() {
        //don't strictly enforce input size
        //just make sure the queue is being consumed.
        if (outstandingRequests < requestThreshold
                && inputSize < maxRequests
                && null != subscription) {
            //if outstandingRequests goes negative due to previous
            //publishers, we recover here.
            subscription.request(min(maxRequests - outstandingRequests, maxRequests));
            outstandingRequests = maxRequests;
        }
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        if (null == subscription) {
            throw new NullPointerException("Null Subscriptions not allowed.");
        }

        executor.submit(() -> {
            if (null == this.subscription && !destroyed) {
                this.subscription = subscription;
                outstandingRequests = 0;
                pull();
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

        //TODO: consider propagating the error... doesn't seem to be a good reason to

        //Outstanding requests will be reset when
        //another subscriber is provided
        //This needs to be submitted or subscription would need to be volatile
        executor.submit(() -> {
            //if (null != subscription)
            subscription = null;
        });
    }

    @Override
    public void onComplete() {
        //Outstanding requests will be reset when
        //another subscriber is provided
        executor.submit(() -> {
            //if (null != subscription)
            subscription = null;
        });
    }
}

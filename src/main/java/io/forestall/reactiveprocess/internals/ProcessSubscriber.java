package io.forestall.reactiveprocess.internals;

import io.forestall.reactiveprocess.ProcessInput;

import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Long.min;


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
    private boolean destroyCalled;

    private final ConcurrentLinkedQueue<ProcessInput<S, T>> input;
    //keep our own input size as ConcurrentLinkedQueue::size is O(n) and not accurate
    private final AtomicLong inputSize;

    private final long maxRequests;
    private final long requestThreshold;

    public ProcessSubscriber(long maxRequests) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be greater than 0");
        }

        subscriptionExecutor = Executors.newSingleThreadExecutor();
        subscription = null;
        outstandingRequests = 0;
        destroyCalled = false;

        input = new ConcurrentLinkedQueue<>();
        inputSize = new AtomicLong(0);

        this.maxRequests = maxRequests;
        this.requestThreshold = maxRequests - (maxRequests / 2); //prevents from being 0
    }

    /**
     * Cancels the current subscription and prevents future subscriptions.
     */
    public void destroy() {
        subscriptionExecutor.submit(() -> {
            if (null != subscription) {
                subscription.cancel();
                subscription = null;
            }
            destroyCalled = true;
        });
    }

    public Optional<ProcessInput<S, T>> get() {
        Optional<ProcessInput<S, T>> result = Optional.ofNullable(input.poll());

        if (result.isPresent()) {
            long localInputSize = inputSize.getAndDecrement();
            if (localInputSize >= maxRequests) {
                //the buffer could have been full when onNexts were called
                //therefore we might need to request more here
                subscriptionExecutor.submit(this::requestMore);
            }
        }

        return result;
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
            inputSize.incrementAndGet();
            requestMore();
        });
    }

    private void requestMore() {
        //don't strictly enforce input size
        //just make sure the queue is being consumed.
        if (outstandingRequests < requestThreshold
                && inputSize.get() < maxRequests
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

        subscriptionExecutor.submit(() -> {
            if (null == this.subscription && !destroyCalled) {
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

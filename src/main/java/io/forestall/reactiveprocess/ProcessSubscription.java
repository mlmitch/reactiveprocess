package io.forestall.reactiveprocess;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.function.Supplier;

public class ProcessSubscription<T> implements Flow.Subscription {

    private final Flow.Subscriber<? super ProcessOutput<T>> subscriber;
    private final ExecutorService executor;

    private final Supplier<Optional<ProcessOutput<T>>> pull;

    private long requests;
    private boolean active;

    /**
     * @param subscriber the subscriber of this subscription
     * @param executor   ExecutorService for running pull and other internal tasks
     * @param pull       method to possibly supply items when called
     */
    ProcessSubscription(Flow.Subscriber<? super ProcessOutput<T>> subscriber,
                        ExecutorService executor,
                        Supplier<Optional<ProcessOutput<T>>> pull) {

        this.subscriber = subscriber;
        this.pull = pull;
        this.executor = executor;

        requests = 0;
        active = true;
    }

    //Only the subscriber should call this
    @Override
    public void request(long n) {
        //make exception out here so the stack is useful
        Optional<IllegalArgumentException> e = (n > 0) ?
                Optional.empty() :
                Optional.of(new IllegalArgumentException("Non positive request amount '" + n + "' is illegal"));

        executor.submit(() -> {
            if (e.isPresent()) {
                error(e.get());
            } else {
                requests += n;
                pullFromProducer();
            }
        });
    }

    //Only the subscriber should call this
    @Override
    public void cancel() {
        executor.submit(() -> active = false);
    }

    //only call from executor
    Flow.Subscriber<? super ProcessOutput<T>> getSubscriber() {
        return subscriber;
    }

    //called from processor on the executor
    boolean isActive() {
        return active;
    }

    //called from processor on the executor
    void pushComplete() {
        if (active) {
            active = false;
            subscriber.onComplete();
        }
    }

    //called from processor on the executor
    void pushError(Throwable throwable) {
        error(throwable);
    }

    //called from processor on the executor
    void pushToSubscription() {
        pullFromProducer();
    }

    //only call executor
    private void pullFromProducer() {
        //recursive function to deliver data to the subscriber
        //won't stack overflow as the recursive call is dispatched on the executor

        if (requests > 0 && active) {
            Optional<ProcessOutput<T>> outputOptional = this.pull.get();
            if (outputOptional.isPresent()) {
                requests--;
                subscriber.onNext(outputOptional.get());

                //recursive call to supply and send again
                //if this one was well received
                //use executor to avoid starving other
                //subscriptions and large stacks
                executor.submit(this::pullFromProducer);
            }
        }
    }

    //only call from executor
    private void error(Throwable throwable) {
        if (active) {
            active = false;
            subscriber.onError(throwable);
        }
    }
}

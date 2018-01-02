package io.forestall.reactiveprocess.internals;

import io.forestall.reactiveprocess.ProcessOutput;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.function.Supplier;

public class ProcessSubscription<T> implements Flow.Subscription {

    private final Flow.Subscriber<? super ProcessOutput<T>> subscriber;
    private final ExecutorService subscriberExecutor;

    private final Supplier<Optional<ProcessOutput<T>>> pull;

    private long requests;
    private boolean active;

    /**
     * @param subscriber the subscriber of this subscription
     * @param pull       non-blocking method to possibly supply items when called
     */
    public ProcessSubscription(Flow.Subscriber<? super ProcessOutput<T>> subscriber,
                               Supplier<Optional<ProcessOutput<T>>> pull) {

        this.subscriber = subscriber;
        this.pull = pull;
        this.subscriberExecutor = Executors.newSingleThreadExecutor();

        requests = 0;
        active = true;
    }

    //only call from subscriberExecutor
    private void pullAndSend() {
        //recursive function to deliver data to the subscriber
        //won't stack overflow as the recursive call is dispatched on the
        //subscriber executor

        if (requests > 0 && active) {
            Optional<ProcessOutput<T>> outputOptional = this.pull.get();
            if (outputOptional.isPresent()) {
                requests--;
                subscriber.onNext(outputOptional.get());

                //recursive call to supply and send again
                //if this one was well received
                subscriberExecutor.submit(this::pullAndSend);
            }
        }
    }

    //only call from subscriberExecutor
    private void error(Throwable throwable) {
        if (active) {
            active = false;
            subscriber.onError(throwable);
        }
    }

    public void pushComplete() {
        subscriberExecutor.submit(() -> {
            if (active) {
                active = false;
                subscriber.onComplete();
            }
        });
    }

    public void pushError(Throwable throwable) {
        subscriberExecutor.submit(() -> error(throwable));
    }

    public void push() {
        subscriberExecutor.submit(this::pullAndSend);
    }

    //Only the subscriber should call this
    @Override
    public void request(long n) {
        subscriberExecutor.submit(() -> {
            if (n > 0) {
                requests += n;
                pullAndSend();
            } else {
                error(new IllegalArgumentException("Non positive requests are illegal"));
            }
        });
    }

    //Only the subscriber should call this
    @Override
    public void cancel() {
        subscriberExecutor.submit(() -> active = false);
    }
}

package io.forestall.reactiveprocess.internals;

import io.forestall.reactiveprocess.ProcessOutput;

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
    public ProcessSubscription(Flow.Subscriber<? super ProcessOutput<T>> subscriber,
                               ExecutorService executor,
                               Supplier<Optional<ProcessOutput<T>>> pull) {

        this.subscriber = subscriber;
        this.pull = pull;
        this.executor = executor;

        requests = 0;
        active = true;
    }

    //only call executor
    private void pullAndSend() {
        //recursive function to deliver data to the subscriber
        //won't stack overflow as the recursive call is dispatched on the executor

        if (requests > 0 && active) {
            Optional<ProcessOutput<T>> outputOptional = this.pull.get();
            if (outputOptional.isPresent()) {
                requests--;
                subscriber.onNext(outputOptional.get());

                //recursive call to supply and send again
                //if this one was well received
                executor.submit(this::pullAndSend);
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
        executor.submit(() -> {
            if (active) {
                active = false;
                subscriber.onComplete();
            }
        });
    }

    public void pushError(Throwable throwable) {
        executor.submit(() -> error(throwable));
    }

    public void push() {
        executor.submit(this::pullAndSend);
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
                pullAndSend();
            }
        });
    }

    //Only the subscriber should call this
    @Override
    public void cancel() {
        executor.submit(() -> active = false);
    }
}

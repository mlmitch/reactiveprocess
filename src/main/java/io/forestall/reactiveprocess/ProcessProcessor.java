package io.forestall.reactiveprocess;

import io.forestall.reactiveprocess.internals.ProcessSubscriber;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

public class ProcessProcessor<S extends InputStream, T>
        implements Flow.Processor<ProcessInput<S, T>, ProcessOutput<BufferedInputStream, T>> {

    private final ProcessSubscriber<S, T> processSubscriber;

    private final ProcessBuilder processBuilder;
    private final int maxProcesses;

    public ProcessProcessor(List<String> arguments, int maxProcesses, long bufferSize) {
        if (maxProcesses <= 0) {
            throw new IllegalArgumentException("Maximum number of processes must be greater than 0");
        }

        if (null == arguments || arguments.isEmpty()) {
            throw new IllegalArgumentException("Null or empty argument lists are not allowed");
        }

        this.maxProcesses = maxProcesses;

        //copy the list so changes to the input don't happen in here
        processBuilder = new ProcessBuilder(new ArrayList<>(arguments));

        processSubscriber = new ProcessSubscriber<>(bufferSize);
    }

    /**
     * Cancels the incoming subscription.
     * Ensures future
     */
    public void destroy() {
        //stop incoming things
        processSubscriber.destroy();

        //TODO: initiate draining of everything and
        //end outgoing subscriptions when done.
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ProcessOutput<BufferedInputStream, T>> subscriber) {

    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        processSubscriber.onSubscribe(subscription);
    }

    @Override
    public void onNext(ProcessInput<S, T> item) {
        processSubscriber.onNext(item);
    }

    @Override
    public void onError(Throwable throwable) {
        processSubscriber.onError(throwable);
    }

    @Override
    public void onComplete() {
        processSubscriber.onComplete();
    }
}
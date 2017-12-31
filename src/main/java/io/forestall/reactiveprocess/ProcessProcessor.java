package io.forestall.reactiveprocess;

import io.forestall.reactiveprocess.internals.ProcessPublisher;
import io.forestall.reactiveprocess.internals.ProcessSubscriber;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Flow;

public class ProcessProcessor<S extends InputStream, T>
        implements Flow.Processor<ProcessInput<S, T>, ProcessOutput<BufferedInputStream, T>> {

    private final ProcessSubscriber<S, T> processSubscriber;
    private final ProcessPublisher<BufferedInputStream, T> processPublisher;

    //TODO: maintain some sort of shutdown state where we reject future subscriptions?

    public ProcessProcessor(List<String> arguments, int maxProcesses) {
        processSubscriber = new ProcessSubscriber<>();
        processPublisher = new ProcessPublisher<>();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ProcessOutput<BufferedInputStream, T>> subscriber) {
        processPublisher.subscribe(subscriber);
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